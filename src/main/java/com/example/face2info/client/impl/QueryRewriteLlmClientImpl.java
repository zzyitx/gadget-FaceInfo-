package com.example.face2info.client.impl;

import com.example.face2info.client.QueryRewriteLlmClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.DeepSeekApiProperties;
import com.example.face2info.config.KimiApiProperties;
import com.example.face2info.entity.internal.QueryRewriteProvider;
import com.example.face2info.entity.internal.RewriteCandidate;
import com.example.face2info.entity.internal.SensitiveQueryAnalysis;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.RetryUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查询改写客户端，统一封装 DeepSeek/Kimi 的提示词和 JSON 解析。
 */
@Component
public class QueryRewriteLlmClientImpl implements QueryRewriteLlmClient {

    private static final String GENERATE_FUNCTION_NAME = "submit_query_rewrite_candidates";
    private static final String VALIDATE_FUNCTION_NAME = "submit_query_rewrite_validation";

    private final RestTemplate restTemplate;
    private final RestTemplate kimiRestTemplate;
    private final ApiProperties properties;
    private final ObjectMapper objectMapper;

    public QueryRewriteLlmClientImpl(RestTemplate restTemplate,
                                     @Qualifier("kimiRestTemplate") RestTemplate kimiRestTemplate,
                                     ApiProperties properties,
                                     ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.kimiRestTemplate = kimiRestTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<RewriteCandidate> generateCandidates(QueryRewriteProvider provider, SensitiveQueryAnalysis analysis) {
        return callWithRetry(provider, "候选生成", buildGenerateRequest(provider, analysis), GENERATE_FUNCTION_NAME);
    }

    @Override
    public List<RewriteCandidate> validateCandidates(QueryRewriteProvider provider,
                                                     SensitiveQueryAnalysis analysis,
                                                     List<RewriteCandidate> candidates) {
        return callWithRetry(provider, "候选验证", buildValidateRequest(provider, analysis, candidates), VALIDATE_FUNCTION_NAME);
    }

    private List<RewriteCandidate> callWithRetry(QueryRewriteProvider provider,
                                                 String action,
                                                 Map<String, Object> requestBody,
                                                 String functionName) {
        ProviderSettings settings = resolveSettings(provider);
        return RetryUtils.execute(provider.name() + " " + action, settings.maxRetries(), settings.backoffInitialMs(), () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(settings.apiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<JsonNode> response = settings.restTemplate().exchange(
                    settings.baseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    JsonNode.class
            );
            return parseCandidates(response.getBody(), functionName);
        });
    }

    private Map<String, Object> buildGenerateRequest(QueryRewriteProvider provider, SensitiveQueryAnalysis analysis) {
        ProviderSettings settings = resolveSettings(provider);
        return Map.of(
                "model", settings.model(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", settings.systemPrompt()),
                        Map.of("role", "user", "content", buildGeneratePrompt(analysis))
                ),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", GENERATE_FUNCTION_NAME,
                                "description", "提交查询改写候选结果",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "candidates", Map.of(
                                                        "type", "array",
                                                        "items", Map.of(
                                                                "type", "object",
                                                                "properties", Map.of(
                                                                        "rewrittenQuery", Map.of("type", "string"),
                                                                        "rewriteReason", Map.of("type", "string")
                                                                ),
                                                                "required", List.of("rewrittenQuery"),
                                                                "additionalProperties", false
                                                        )
                                                )
                                        ),
                                        "required", List.of("candidates"),
                                        "additionalProperties", false
                                )
                        )
                )),
                "tool_choice", Map.of("type", "function", "function", Map.of("name", GENERATE_FUNCTION_NAME))
        );
    }

    private Map<String, Object> buildValidateRequest(QueryRewriteProvider provider,
                                                     SensitiveQueryAnalysis analysis,
                                                     List<RewriteCandidate> candidates) {
        ProviderSettings settings = resolveSettings(provider);
        return Map.of(
                "model", settings.model(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", settings.systemPrompt()),
                        Map.of("role", "user", "content", buildValidatePrompt(analysis, candidates))
                ),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", VALIDATE_FUNCTION_NAME,
                                "description", "提交筛选后的查询改写候选",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "approvedCandidates", Map.of(
                                                        "type", "array",
                                                        "items", Map.of(
                                                                "type", "object",
                                                                "properties", Map.of(
                                                                        "rewrittenQuery", Map.of("type", "string"),
                                                                        "safetyScore", Map.of("type", "number"),
                                                                        "semanticPreservationScore", Map.of("type", "number"),
                                                                        "finalScore", Map.of("type", "number")
                                                                ),
                                                                "required", List.of("rewrittenQuery", "finalScore"),
                                                                "additionalProperties", false
                                                        )
                                                )
                                        ),
                                        "required", List.of("approvedCandidates"),
                                        "additionalProperties", false
                                )
                        )
                )),
                "tool_choice", Map.of("type", "function", "function", Map.of("name", VALIDATE_FUNCTION_NAME))
        );
    }

    private String buildGeneratePrompt(SensitiveQueryAnalysis analysis) {
        return """
                你需要把人物派生主题查询改写成更中性、更适合公开搜索引擎使用的表达。
                约束：
                1. 只能通过函数 submit_query_rewrite_candidates 返回结果，禁止输出解释、Markdown、代码块和额外文本。
                2. 必须保留 protectedTerms 中的人名和核心实体，不得改写。
                3. 不得改变查询意图，只能把过于对抗、定性或激烈的表述改成中性说法。
                4. 至少返回 1 个候选，最多返回 5 个候选。
                originalQuery: %s
                normalizedQuery: %s
                tokens: %s
                sensitiveTerms: %s
                protectedTerms: %s
                """.formatted(
                analysis.getOriginalQuery(),
                analysis.getNormalizedQuery(),
                analysis.getTokens(),
                analysis.getSensitiveTerms(),
                analysis.getProtectedTerms()
        );
    }

    private String buildValidatePrompt(SensitiveQueryAnalysis analysis, List<RewriteCandidate> candidates) {
        return """
                你需要从候选查询里筛选适合公开搜索引擎使用的中性表达。
                约束：
                1. 只能通过函数 submit_query_rewrite_validation 返回结果，禁止输出解释、Markdown、代码块和额外文本。
                2. 剔除仍然带有明显攻击、煽动、极化或规避痕迹的候选。
                3. 分数越高，表示越安全且越接近原始语义。
                originalQuery: %s
                normalizedQuery: %s
                protectedTerms: %s
                candidates: %s
                """.formatted(
                analysis.getOriginalQuery(),
                analysis.getNormalizedQuery(),
                analysis.getProtectedTerms(),
                candidates
        );
    }

    private List<RewriteCandidate> parseCandidates(JsonNode body, String functionName) {
        String content = extractStructuredPayload(body, functionName);
        try {
            JsonNode json = objectMapper.readTree(extractJsonCandidate(content));
            JsonNode candidatesNode = GENERATE_FUNCTION_NAME.equals(functionName)
                    ? json.path("candidates")
                    : json.path("approvedCandidates");
            List<RewriteCandidate> candidates = new ArrayList<>();
            if (candidatesNode.isArray()) {
                for (JsonNode candidateNode : candidatesNode) {
                    String rewrittenQuery = trimToNull(candidateNode.path("rewrittenQuery").asText(null));
                    if (!StringUtils.hasText(rewrittenQuery)) {
                        continue;
                    }
                    candidates.add(new RewriteCandidate()
                            .setRewrittenQuery(rewrittenQuery)
                            .setRewriteReason(trimToNull(candidateNode.path("rewriteReason").asText(null)))
                            .setSafetyScore(candidateNode.path("safetyScore").asDouble(0.0))
                            .setSemanticPreservationScore(candidateNode.path("semanticPreservationScore").asDouble(0.0))
                            .setFinalScore(candidateNode.path("finalScore").asDouble(0.0)));
                }
            }
            return candidates;
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("INVALID_RESPONSE: 查询改写模型返回了非法 JSON", ex);
        }
    }

    private String extractStructuredPayload(JsonNode body, String expectedFunctionName) {
        JsonNode messageNode = body == null ? null : body.path("choices").path(0).path("message");
        JsonNode toolCalls = messageNode == null ? null : messageNode.path("tool_calls");
        if (toolCalls != null && toolCalls.isArray()) {
            for (JsonNode toolCall : toolCalls) {
                JsonNode functionNode = toolCall.path("function");
                if (expectedFunctionName.equals(functionNode.path("name").asText(null))) {
                    String arguments = functionNode.path("arguments").asText(null);
                    if (StringUtils.hasText(arguments)) {
                        return arguments;
                    }
                }
            }
        }
        String content = messageNode == null ? null : messageNode.path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new ApiCallException("EMPTY_RESPONSE: 查询改写模型未返回内容");
        }
        return content;
    }

    private String extractJsonCandidate(String content) {
        String trimmed = trimToNull(content);
        if (!StringUtils.hasText(trimmed)) {
            throw new ApiCallException("EMPTY_RESPONSE: 查询改写模型内容为空");
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }
        throw new ApiCallException("INVALID_RESPONSE: 查询改写模型未返回 JSON");
    }

    private ProviderSettings resolveSettings(QueryRewriteProvider provider) {
        if (provider == QueryRewriteProvider.KIMI) {
            KimiApiProperties kimi = properties.getApi().getKimi();
            validateSettings(kimi.getBaseUrl(), kimi.getApiKey(), kimi.getModel(), "Kimi");
            return new ProviderSettings(
                    kimiRestTemplate,
                    kimi.getBaseUrl(),
                    kimi.getApiKey(),
                    kimi.getModel(),
                    kimi.getSystemPrompt(),
                    kimi.getMaxRetries(),
                    kimi.getBackoffInitialMs()
            );
        }
        DeepSeekApiProperties deepseek = properties.getApi().getDeepseek();
        validateSettings(deepseek.getBaseUrl(), deepseek.getApiKey(), deepseek.getModel(), "DeepSeek");
        return new ProviderSettings(
                restTemplate,
                deepseek.getBaseUrl(),
                deepseek.getApiKey(),
                deepseek.getModel(),
                deepseek.getSystemPrompt(),
                deepseek.getMaxRetries(),
                deepseek.getBackoffInitialMs()
        );
    }

    private void validateSettings(String baseUrl, String apiKey, String model, String providerName) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(model)) {
            throw new ApiCallException("CONFIG_MISSING: " + providerName + " 查询改写配置不完整");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ProviderSettings(RestTemplate restTemplate,
                                    String baseUrl,
                                    String apiKey,
                                    String model,
                                    String systemPrompt,
                                    int maxRetries,
                                    long backoffInitialMs) {
    }
}
