package com.example.face2info.client.impl;

import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.KimiApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.PersonBasicInfo;
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

    private static final String PAGE_SUMMARY_FUNCTION_NAME = "submit_page_summary";
    private static final String PERSON_PROFILE_FUNCTION_NAME = "submit_person_profile";

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
        log.info("Kimi page summary start fallbackName={} url={} title={}",
                fallbackName,
                page == null ? null : page.getUrl(),
                page == null ? null : page.getTitle());

        return RetryUtils.execute("Kimi summarize page", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
            JsonNode body = callKimi(kimi, buildPageRequest(kimi, fallbackName, page));
            PageSummary summary = parsePageSummary(page, body);
            log.info("Kimi page summary success url={} summaryLength={} tagCount={} factCount={}",
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
        log.info("Kimi final profile summary start fallbackName={} pageSummaryCount={}", fallbackName, pageSummaryCount);

        return RetryUtils.execute("Kimi summarize person", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
            JsonNode body = callKimi(kimi, buildPersonRequest(kimi, fallbackName, pageSummaries));
            ResolvedPersonProfile profile = parseProfileFromPageSummaries(fallbackName, pageSummaries, body);
            log.info("Kimi final profile summary success resolvedName={} summaryLength={} tagCount={} evidenceUrlCount={}",
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
            log.error("Kimi config missing baseUrlConfigured={} modelConfigured={} apiKeyConfigured={}",
                    StringUtils.hasText(kimi.getBaseUrl()),
                    StringUtils.hasText(kimi.getModel()),
                    StringUtils.hasText(kimi.getApiKey()));
            throw new ApiCallException("CONFIG_MISSING: kimi config is incomplete");
        }
    }

    private JsonNode callKimi(KimiApiProperties kimi, Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(kimi.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        log.info("Kimi request sent model={} url={}", kimi.getModel(), LogSanitizer.maskUrl(kimi.getBaseUrl()));
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                kimi.getBaseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                JsonNode.class
        );
        return response.getBody();
    }

    private Map<String, Object> buildPageRequest(KimiApiProperties kimi, String fallbackName, PageContent page) {
        return Map.of(
                "model", kimi.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", kimi.getSystemPrompt()),
                        Map.of("role", "user", "content", buildPagePrompt(fallbackName, page))
                ),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", PAGE_SUMMARY_FUNCTION_NAME,
                                "description", "提交单篇页面摘要的结构化结果",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "resolvedNameCandidate", Map.of("type", "string"),
                                                "summary", Map.of("type", "string"),
                                                "keyFacts", Map.of("type", "array", "items", Map.of("type", "string")),
                                                "tags", Map.of("type", "array", "items", Map.of("type", "string")),
                                                "sourceUrl", Map.of("type", "string"),
                                                "title", Map.of("type", "string")
                                        ),
                                        "required", List.of("summary"),
                                        "additionalProperties", false
                                )
                        )
                )),
                "tool_choice", Map.of(
                        "type", "function",
                        "function", Map.of("name", PAGE_SUMMARY_FUNCTION_NAME)
                )
        );
    }

    private Map<String, Object> buildPersonRequest(KimiApiProperties kimi,
                                                   String fallbackName,
                                                   List<PageSummary> pageSummaries) {
        return Map.of(
                "model", kimi.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", kimi.getSystemPrompt()),
                        Map.of("role", "user", "content", buildPersonPrompt(fallbackName, pageSummaries))
                ),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", PERSON_PROFILE_FUNCTION_NAME,
                                "description", "提交人物聚合画像的结构化结果",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "resolvedName", Map.of("type", "string"),
                                                "description", Map.of("type", "string"),
                                                "summary", Map.of("type", "string"),
                                                "keyFacts", Map.of("type", "array", "items", Map.of("type", "string")),
                                                "tags", Map.of("type", "array", "items", Map.of("type", "string")),
                                                "wikipedia", Map.of("type", "string"),
                                                "officialWebsite", Map.of("type", "string"),
                                                "basicInfo", Map.of(
                                                        "type", "object",
                                                        "properties", Map.of(
                                                                "birthDate", Map.of("type", "string"),
                                                                "education", Map.of("type", "array", "items", Map.of("type", "string")),
                                                                "occupations", Map.of("type", "array", "items", Map.of("type", "string")),
                                                                "biographies", Map.of("type", "array", "items", Map.of("type", "string"))
                                                        ),
                                                        "additionalProperties", false
                                                ),
                                                "evidenceUrls", Map.of("type", "array", "items", Map.of("type", "string"))
                                        ),
                                        "required", List.of("resolvedName"),
                                        "additionalProperties", false
                                )
                        )
                )),
                "tool_choice", Map.of(
                        "type", "function",
                        "function", Map.of("name", PERSON_PROFILE_FUNCTION_NAME)
                )
        );
    }

    private String buildPagePrompt(String fallbackName, PageContent page) {
        return """
                请基于以下单篇正文提取人物信息，只能输出 JSON，且返回内容语言必须为中文。
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
                请基于以下篇级摘要集合生成人物最终画像，只能输出 JSON，且返回内容语言必须为中文。
                JSON 字段固定为 resolvedName、description、summary、keyFacts、tags、wikipedia、officialWebsite、basicInfo、evidenceUrls。
                basicInfo 为对象，字段固定为 birthDate、education、occupations、biographies。
                fallbackName: %s
                篇级摘要如下：
                %s
                """.formatted(fallbackName, pageSummaryContent);
    }

    private PageSummary parsePageSummary(PageContent page, JsonNode body) {
        String content = extractStructuredPayload(body, PAGE_SUMMARY_FUNCTION_NAME);
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
            log.warn("Kimi page summary parse failed error={}", ex.getMessage(), ex);
            throw new ApiCallException("INVALID_RESPONSE: kimi page summary is not valid json", ex);
        }
    }

    private ResolvedPersonProfile parseProfileFromPageSummaries(String fallbackName,
                                                                List<PageSummary> pageSummaries,
                                                                JsonNode body) {
        String content = extractStructuredPayload(body, PERSON_PROFILE_FUNCTION_NAME);
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
                    .setDescription(trimToNull(json.path("description").asText(null)))
                    .setSummary(trimToNull(json.path("summary").asText(null)))
                    .setKeyFacts(readStringList(json.path("keyFacts")))
                    .setTags(readStringList(json.path("tags")))
                    .setWikipedia(trimToNull(json.path("wikipedia").asText(null)))
                    .setOfficialWebsite(trimToNull(json.path("officialWebsite").asText(null)))
                    .setBasicInfo(readBasicInfo(json.path("basicInfo")))
                    .setEvidenceUrls(evidenceUrls);
        } catch (JsonProcessingException ex) {
            log.warn("Kimi final profile parse failed fallbackName={} error={}", fallbackName, ex.getMessage(), ex);
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

    private String extractStructuredPayload(JsonNode body, String expectedFunctionName) {
        JsonNode messageNode = body == null ? null : body.path("choices").path(0).path("message");
        JsonNode toolCalls = messageNode == null ? null : messageNode.path("tool_calls");
        if (toolCalls != null && toolCalls.isArray()) {
            for (JsonNode toolCall : toolCalls) {
                JsonNode functionNode = toolCall.path("function");
                String functionName = functionNode.path("name").asText(null);
                String arguments = functionNode.path("arguments").asText(null);
                if (expectedFunctionName.equals(functionName) && StringUtils.hasText(arguments)) {
                    return arguments;
                }
            }
        }
        return extractContent(body);
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

    private PersonBasicInfo readBasicInfo(JsonNode basicInfoNode) {
        if (basicInfoNode == null || basicInfoNode.isMissingNode() || basicInfoNode.isNull()) {
            return new PersonBasicInfo();
        }
        return new PersonBasicInfo()
                .setBirthDate(trimToNull(basicInfoNode.path("birthDate").asText(null)))
                .setEducation(readStringList(basicInfoNode.path("education")))
                .setOccupations(readStringList(basicInfoNode.path("occupations")))
                .setBiographies(readStringList(basicInfoNode.path("biographies")));
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
