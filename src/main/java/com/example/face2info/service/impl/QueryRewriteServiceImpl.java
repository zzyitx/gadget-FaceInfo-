package com.example.face2info.service.impl;

import com.example.face2info.client.QueryRewriteLlmClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.DerivedTopicRequest;
import com.example.face2info.entity.internal.QueryRewriteProvider;
import com.example.face2info.entity.internal.RewriteCandidate;
import com.example.face2info.entity.internal.SensitiveQueryAnalysis;
import com.example.face2info.entity.internal.TopicQueryDecision;
import com.example.face2info.entity.internal.TopicRewriteStrategy;
import com.example.face2info.service.QueryRewriteService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 查询改写服务，先用规则检测敏感表达，再用模型生成更适合搜索的中性候选。
 */
@Service
public class QueryRewriteServiceImpl implements QueryRewriteService {

    private final QueryRewriteLlmClient llmClient;
    private final ApiProperties properties;

    public QueryRewriteServiceImpl(QueryRewriteLlmClient llmClient, ApiProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    @Override
    public TopicQueryDecision rewrite(DerivedTopicRequest request) {
        SensitiveQueryAnalysis analysis = analyze(request);
        List<QueryRewriteProvider> providers = resolveProviders();
        for (QueryRewriteProvider provider : providers) {
            try {
                List<RewriteCandidate> generated = llmClient.generateCandidates(provider, analysis);
                if (generated == null || generated.isEmpty()) {
                    continue;
                }
                List<RewriteCandidate> approved = llmClient.validateCandidates(provider, analysis, generated);
                RewriteCandidate selected = selectBestCandidate(approved);
                if (selected != null && StringUtils.hasText(selected.getRewrittenQuery())) {
                    return new TopicQueryDecision()
                            .setFinalQuery(normalizeQuery(selected.getRewrittenQuery()))
                            .setStrategy(TopicRewriteStrategy.REWRITE)
                            .setSensitive(analysis.isSensitive())
                            .setTokens(analysis.getTokens());
                }
            } catch (RuntimeException ignored) {
                // 单个 provider 失败时继续尝试后续 provider，避免派生主题查询整体中断。
            }
        }
        return new TopicQueryDecision()
                .setFinalQuery(resolveFallbackQuery(request, analysis))
                .setStrategy(TopicRewriteStrategy.REWRITE)
                .setSensitive(analysis.isSensitive())
                .setUsedFallback(true)
                .setTokens(analysis.getTokens());
    }

    private SensitiveQueryAnalysis analyze(DerivedTopicRequest request) {
        String normalized = normalizeQuery(request == null ? null : request.getRawQuery());
        List<String> sensitiveTerms = detectSensitiveTerms(request, normalized);
        return new SensitiveQueryAnalysis()
                .setOriginalQuery(request == null ? null : request.getRawQuery())
                .setNormalizedQuery(normalized)
                .setTokens(tokenize(normalized))
                .setProtectedTerms(resolveProtectedTerms(request))
                .setSensitiveTerms(sensitiveTerms)
                .setSensitive(!sensitiveTerms.isEmpty())
                .setRiskLevel(sensitiveTerms.isEmpty() ? "low" : "high");
    }

    private List<String> resolveProtectedTerms(DerivedTopicRequest request) {
        Set<String> terms = new LinkedHashSet<>();
        if (request != null && StringUtils.hasText(request.getResolvedName())) {
            terms.add(request.getResolvedName().trim());
        }
        if (request != null && request.getProtectedTerms() != null) {
            for (String term : request.getProtectedTerms()) {
                if (StringUtils.hasText(term)) {
                    terms.add(term.trim());
                }
            }
        }
        return List.copyOf(terms);
    }

    private List<String> detectSensitiveTerms(DerivedTopicRequest request, String normalizedQuery) {
        if (request == null || request.getTopicType() == null) {
            return List.of();
        }
        List<String> patterns = properties.getApi().getQueryRewrite().getSensitiveTopicPatterns()
                .getOrDefault(request.getTopicType().getKey(), List.of());
        List<String> matched = new ArrayList<>();
        String safeQuery = normalizedQuery == null ? "" : normalizedQuery.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (StringUtils.hasText(pattern) && safeQuery.contains(pattern.toLowerCase(Locale.ROOT))) {
                matched.add(pattern.trim());
            }
        }
        return matched;
    }

    private List<String> tokenize(String normalizedQuery) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return List.of();
        }
        String sanitized = normalizedQuery
                .replace("，", " ")
                .replace("。", " ")
                .replace("？", " ")
                .replace("！", " ")
                .replace("、", " ")
                .replace("的", " ");
        Set<String> tokens = new LinkedHashSet<>();
        for (String piece : sanitized.split("\\s+")) {
            if (StringUtils.hasText(piece)) {
                tokens.add(piece.trim());
            }
        }
        return List.copyOf(tokens);
    }

    private List<QueryRewriteProvider> resolveProviders() {
        List<String> configured = properties.getApi().getQueryRewrite().getProviderPriority();
        if (configured == null || configured.isEmpty()) {
            return List.of(QueryRewriteProvider.DEEPSEEK, QueryRewriteProvider.KIMI);
        }
        List<QueryRewriteProvider> providers = new ArrayList<>();
        for (String value : configured) {
            QueryRewriteProvider provider = QueryRewriteProvider.fromValue(value);
            if (!providers.contains(provider)) {
                providers.add(provider);
            }
        }
        return providers;
    }

    private RewriteCandidate selectBestCandidate(List<RewriteCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .filter(candidate -> candidate != null && StringUtils.hasText(candidate.getRewrittenQuery()))
                .max(Comparator.comparingDouble(RewriteCandidate::getFinalScore))
                .orElse(null);
    }

    private String resolveFallbackQuery(DerivedTopicRequest request, SensitiveQueryAnalysis analysis) {
        if (request != null && request.getTopicType() != null) {
            List<String> templates = properties.getApi().getQueryRewrite().getFallbackTemplates()
                    .get(request.getTopicType().getKey());
            if (templates != null) {
                for (String template : templates) {
                    if (StringUtils.hasText(template)) {
                        String subject = request.getResolvedName();
                        if (!StringUtils.hasText(subject)) {
                            subject = analysis.getProtectedTerms().isEmpty() ? null : analysis.getProtectedTerms().get(0);
                        }
                        if (StringUtils.hasText(subject)) {
                            return normalizeQuery(String.format(template, subject.trim()));
                        }
                    }
                }
            }
        }
        return analysis.getNormalizedQuery();
    }

    private String normalizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return query;
        }
        return query.trim().replaceAll("\\s+", " ");
    }
}
