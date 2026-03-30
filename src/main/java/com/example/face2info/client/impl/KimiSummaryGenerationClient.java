package com.example.face2info.client.impl;

import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.KimiApiProperties;
import com.example.face2info.entity.internal.PageContent;
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
    public ResolvedPersonProfile summarizePerson(String fallbackName, List<PageContent> pages) {
        KimiApiProperties kimi = properties.getApi().getKimi();
        int pageCount = pages == null ? 0 : pages.size();
        log.info("Kimi 模块开始处理 fallbackName={} pageCount={} provider={}",
                fallbackName, pageCount, properties.getApi().getSummary().getProvider());
        if (!StringUtils.hasText(kimi.getApiKey())
                || !StringUtils.hasText(kimi.getBaseUrl())
                || !StringUtils.hasText(kimi.getModel())) {
            log.error("Kimi 配置缺失 baseUrlConfigured={} modelConfigured={} apiKeyConfigured={}",
                    StringUtils.hasText(kimi.getBaseUrl()),
                    StringUtils.hasText(kimi.getModel()),
                    StringUtils.hasText(kimi.getApiKey()));
            throw new ApiCallException("CONFIG_MISSING: kimi config is incomplete");
        }

        log.info("Kimi 配置检查通过 model={} url={}", kimi.getModel(), LogSanitizer.maskUrl(kimi.getBaseUrl()));
        return RetryUtils.execute("Kimi summarize", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(kimi.getApiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                    "model", kimi.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", kimi.getSystemPrompt()),
                            Map.of("role", "user", "content", buildUserPrompt(fallbackName, pages))
                    )
            );

            log.info("Kimi 请求已发送 model={} pageCount={} url={}",
                    kimi.getModel(), pageCount, LogSanitizer.maskUrl(kimi.getBaseUrl()));
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    kimi.getBaseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    JsonNode.class
            );

            ResolvedPersonProfile profile = parseProfile(fallbackName, pages, response.getBody());
            log.info("Kimi 返回解析成功 resolvedName={} summaryLength={} tagCount={} evidenceUrlCount={}",
                    profile.getResolvedName(),
                    profile.getSummary() == null ? 0 : profile.getSummary().length(),
                    profile.getTags() == null ? 0 : profile.getTags().size(),
                    profile.getEvidenceUrls() == null ? 0 : profile.getEvidenceUrls().size());
            return profile;
        });
    }

    private String buildUserPrompt(String fallbackName, List<PageContent> pages) {
        String pageContent = pages == null ? "" : pages.stream()
                .map(page -> "URL: " + page.getUrl() + "\n正文: " + page.getContent())
                .collect(Collectors.joining("\n---\n"));
        return """
                请基于以下正文抽取人物信息，只能输出 JSON，且返回内容语言必须为中文，不要输出额外解释。
                JSON 字段固定为 resolvedName、summary、tags、evidenceUrls。
                fallbackName: %s
                正文如下：
                %s
                """.formatted(fallbackName, pageContent);
    }

    private ResolvedPersonProfile parseProfile(String fallbackName, List<PageContent> pages, JsonNode body) {
        String content = body == null ? null : body.path("choices").path(0).path("message").path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new ApiCallException("EMPTY_RESPONSE: kimi content is empty");
        }

        try {
            JsonNode json = objectMapper.readTree(normalizeJsonContent(content));
            Set<String> deduplicatedTags = new LinkedHashSet<>();
            JsonNode tagsNode = json.path("tags");
            if (tagsNode.isArray()) {
                for (JsonNode tag : tagsNode) {
                    String value = tag.asText(null);
                    if (StringUtils.hasText(value)) {
                        deduplicatedTags.add(value.trim());
                    }
                }
            }

            ResolvedPersonProfile profile = new ResolvedPersonProfile()
                    .setResolvedName(firstNonBlank(json.path("resolvedName").asText(null), fallbackName))
                    .setSummary(json.path("summary").asText(null))
                    .setTags(List.copyOf(deduplicatedTags));

            JsonNode evidenceUrls = json.path("evidenceUrls");
            if (evidenceUrls.isArray()) {
                profile.setEvidenceUrls(readStringList(evidenceUrls));
            } else if (pages != null) {
                profile.setEvidenceUrls(pages.stream()
                        .map(PageContent::getUrl)
                        .filter(StringUtils::hasText)
                        .toList());
            }
            return profile;
        } catch (JsonProcessingException ex) {
            log.warn("Kimi 返回解析失败 fallbackName={} error={}", fallbackName, ex.getMessage(), ex);
            throw new ApiCallException("INVALID_RESPONSE: kimi content is not valid json", ex);
        }
    }

    private List<String> readStringList(JsonNode arrayNode) {
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode item : arrayNode) {
            String value = item.asText(null);
            if (StringUtils.hasText(value)) {
                values.add(value.trim());
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
