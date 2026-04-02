package com.example.face2info.client.impl;

import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.KimiApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.LogSanitizer;
import com.example.face2info.util.RetryUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "face2info.api.summary", name = "provider", havingValue = "kimi")
public class KimiSummaryGenerationClient implements SummaryGenerationClient {

    private final RestTemplate restTemplate;
    private final ApiProperties properties;
    private final ObjectMapper objectMapper;

    public KimiSummaryGenerationClient(RestTemplate restTemplate, ApiProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageSummary summarizePage(String fallbackName, PageContent page) {
        KimiApiProperties kimi = properties.getApi().getKimi();
        validateConfig(kimi);
        log.info("Kimi 篇级总结开始 fallbackName={} url={} title={}",
                fallbackName,
                page == null ? null : page.getUrl(),
                page == null ? null : page.getTitle());

        return RetryUtils.execute("Kimi summarize page", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
            JsonNode body = callKimi(kimi, buildPagePrompt(fallbackName, page));
            PageSummary summary = parsePageSummary(page, body);
            log.info("Kimi 篇级总结成功 url={} summaryLength={} tagCount={} factCount={}",
                    summary.getSourceUrl(),
                    summary.getSummary() == null ? 0 : summary.getSummary().length(),
                    summary.getTags() == null ? 0 : summary.getTags().size(),
                    summary.getKeyFacts() == null ? 0 : summary.getKeyFacts().size());
            return summary;
        });
    }

    @Override
    public ResolvedPersonProfile summarizePersonFromPageSummaries(String fallbackName, List<PageSummary> pageSummaries) {
        KimiApiProperties kimi = properties.getApi().getKimi();
        validateConfig(kimi);
        int pageSummaryCount = pageSummaries == null ? 0 : pageSummaries.size();
        log.info("Kimi 最终汇总开始 fallbackName={} pageSummaryCount={}", fallbackName, pageSummaryCount);

        return RetryUtils.execute("Kimi summarize person", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
            JsonNode body = callKimi(kimi, buildPersonPrompt(fallbackName, pageSummaries));
            ResolvedPersonProfile profile = parseProfileFromPageSummaries(fallbackName, pageSummaries, body);
            log.info("Kimi 最终汇总成功 resolvedName={} summaryLength={} tagCount={} evidenceUrlCount={}",
                    profile.getResolvedName(),
                    profile.getSummary() == null ? 0 : profile.getSummary().length(),
                    profile.getTags() == null ? 0 : profile.getTags().size(),
                    profile.getEvidenceUrls() == null ? 0 : profile.getEvidenceUrls().size());
            return profile;
        });
    }

    private void validateConfig(KimiApiProperties kimi) {
        if (!StringUtils.hasText(kimi.getApiKey())
                || !StringUtils.hasText(kimi.getBaseUrl())
                || !StringUtils.hasText(kimi.getModel())) {
            log.error("Kimi 配置缺失 baseUrlConfigured={} modelConfigured={} apiKeyConfigured={}",
                    StringUtils.hasText(kimi.getBaseUrl()),
                    StringUtils.hasText(kimi.getModel()),
                    StringUtils.hasText(kimi.getApiKey()));
            throw new ApiCallException("CONFIG_MISSING: kimi config is incomplete");
        }
    }

    private JsonNode callKimi(KimiApiProperties kimi, String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(kimi.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of(
                "model", kimi.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", kimi.getSystemPrompt()),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        log.info("Kimi 请求已发送 model={} url={}", kimi.getModel(), LogSanitizer.maskUrl(kimi.getBaseUrl()));
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                kimi.getBaseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                JsonNode.class
        );
        return response.getBody();
    }

    private String buildPagePrompt(String fallbackName, PageContent page) {
        return """
                请基于以下单篇正文抽取人物信息，只能输出 JSON，且返回内容语言必须为中文。
                JSON 字段固定为 resolvedNameCandidate、summary、keyFacts、tags、sourceUrl。
                fallbackName: %s
                title: %s
                url: %s
                正文如下：
                %s
                """.formatted(
                fallbackName,
                page == null ? null : page.getTitle(),
                page == null ? null : page.getUrl(),
                page == null ? null : page.getContent()
        );
    }

    private String buildPersonPrompt(String fallbackName, List<PageSummary> pageSummaries) {
        String pageSummaryContent = pageSummaries == null ? "" : pageSummaries.stream()
                .map(summary -> """
                        sourceUrl: %s
                        title: %s
                        resolvedNameCandidate: %s
                        summary: %s
                        keyFacts: %s
                        tags: %s
                        """.formatted(
                        summary.getSourceUrl(),
                        summary.getTitle(),
                        summary.getResolvedNameCandidate(),
                        summary.getSummary(),
                        summary.getKeyFacts(),
                        summary.getTags()
                ))
                .collect(Collectors.joining("\n---\n"));

        return """
                请基于以下篇级摘要集合生成最终人物总结，只能输出 JSON，且返回内容语言必须为中文。
                JSON 字段固定为 resolvedName、summary、keyFacts、tags、evidenceUrls。
                fallbackName: %s
                篇级摘要如下：
                %s
                """.formatted(fallbackName, pageSummaryContent);
    }

    private PageSummary parsePageSummary(PageContent page, JsonNode body) {
        String content = extractContent(body);
        try {
            JsonNode json = objectMapper.readTree(normalizeJsonContent(content));
            String summary = json.path("summary").asText(null);
            if (!StringUtils.hasText(summary)) {
                throw new ApiCallException("EMPTY_RESPONSE: kimi page summary is empty");
            }

            return new PageSummary()
                    .setSourceUrl(firstNonBlank(json.path("sourceUrl").asText(null), page == null ? null : page.getUrl()))
                    .setTitle(firstNonBlank(json.path("title").asText(null), page == null ? null : page.getTitle()))
                    .setResolvedNameCandidate(json.path("resolvedNameCandidate").asText(null))
                    .setSummary(summary.trim())
                    .setKeyFacts(readStringList(json.path("keyFacts")))
                    .setTags(readStringList(json.path("tags")));
        } catch (JsonProcessingException ex) {
            log.warn("Kimi 篇级总结解析失败 error={}", ex.getMessage(), ex);
            throw new ApiCallException("INVALID_RESPONSE: kimi page summary is not valid json", ex);
        }
    }

    private ResolvedPersonProfile parseProfileFromPageSummaries(String fallbackName,
                                                                List<PageSummary> pageSummaries,
                                                                JsonNode body) {
        String content = extractContent(body);
        try {
            JsonNode json = objectMapper.readTree(normalizeJsonContent(content));
            List<String> evidenceUrls = readStringList(json.path("evidenceUrls"));
            if (evidenceUrls.isEmpty() && pageSummaries != null) {
                evidenceUrls = pageSummaries.stream()
                        .map(PageSummary::getSourceUrl)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList();
            }

            return new ResolvedPersonProfile()
                    .setResolvedName(firstNonBlank(json.path("resolvedName").asText(null), fallbackName))
                    .setSummary(json.path("summary").asText(null))
                    .setKeyFacts(readStringList(json.path("keyFacts")))
                    .setTags(readStringList(json.path("tags")))
                    .setEvidenceUrls(evidenceUrls);
        } catch (JsonProcessingException ex) {
            log.warn("Kimi 最终汇总解析失败 fallbackName={} error={}", fallbackName, ex.getMessage(), ex);
            throw new ApiCallException("INVALID_RESPONSE: kimi content is not valid json", ex);
        }
    }

    private String extractContent(JsonNode body) {
        String content = body == null ? null : body.path("choices").path(0).path("message").path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new ApiCallException("EMPTY_RESPONSE: kimi content is empty");
        }
        return content;
    }

    private List<String> readStringList(JsonNode arrayNode) {
        Set<String> values = new LinkedHashSet<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                String value = item.asText(null);
                if (StringUtils.hasText(value)) {
                    values.add(value.trim());
                }
            }
        }
        return List.copyOf(values);
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private String normalizeJsonContent(String content) {
        String normalized = content == null ? null : content.trim();
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        if (!normalized.startsWith("```")) {
            return normalized;
        }

        int firstLineBreak = normalized.indexOf('\n');
        if (firstLineBreak < 0) {
            return normalized;
        }

        String withoutOpeningFence = normalized.substring(firstLineBreak + 1).trim();
        if (withoutOpeningFence.endsWith("```")) {
            withoutOpeningFence = withoutOpeningFence.substring(0, withoutOpeningFence.length() - 3).trim();
        }
        return withoutOpeningFence;
    }
}
