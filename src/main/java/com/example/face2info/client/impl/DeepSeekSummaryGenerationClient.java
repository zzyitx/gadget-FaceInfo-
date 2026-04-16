package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.DeepSeekApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.PersonBasicInfo;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SectionSummaryItem;
import com.example.face2info.entity.internal.SectionedSummary;
import com.example.face2info.entity.internal.TopicExpansionDecision;
import com.example.face2info.entity.internal.TopicExpansionQuery;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.LogSanitizer;
import com.example.face2info.util.RetryUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DeepSeekSummaryGenerationClient {

    private static final String PAGE_SUMMARY_FUNCTION_NAME = "submit_page_summary";
    private static final String PERSON_PROFILE_FUNCTION_NAME = "submit_person_profile";
    private static final String SECTION_SUMMARY_FUNCTION_NAME = "submit_section_summary";
    private static final String TOPIC_EXPANSION_FUNCTION_NAME = "submit_topic_expansion";
    private static final String SECTIONED_FAMILY_SUMMARY_FUNCTION_NAME = "submit_sectioned_family_summary";
    private static final String PROFILE_JUDGEMENT_FUNCTION_NAME = "submit_profile_judgement";
    private static final Pattern XML_INVOKE_PATTERN = Pattern.compile(
            "<invoke\\s+name\\s*=\\s*\"([^\"]+)\"\\s*>(.*?)</invoke>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern XML_PARAMETER_PATTERN = Pattern.compile(
            "<parameter\\s+name\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</parameter>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern SERIALIZED_PARAMETER_PATTERN = Pattern.compile(
            "<[^>]*parameter\\s+name\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</[^>]*parameter>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final RestTemplate restTemplate;
    private final ApiProperties properties;
    private final ObjectMapper objectMapper;

    public DeepSeekSummaryGenerationClient(RestTemplate restTemplate,
                                           ApiProperties properties,
                                           ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public PageSummary summarizePage(String fallbackName, PageContent page) {
        DeepSeekApiProperties deepseek = properties.getApi().getDeepseek();
        validateConfig(deepseek);
        log.info("DeepSeek 页面摘要开始 fallbackName={} url={} title={}",
                fallbackName,
                page == null ? null : page.getUrl(),
                page == null ? null : page.getTitle());
        return RetryUtils.execute("DeepSeek 页面摘要", deepseek.getMaxRetries(), deepseek.getBackoffInitialMs(), () -> {
            JsonNode body = callDeepSeek(deepseek, buildPageRequest(deepseek, fallbackName, page));
            PageSummary summary = parsePageSummary(page, body);
            log.info("DeepSeek 页面摘要成功 url={} summaryLength={} tagCount={} factCount={}",
                    summary.getSourceUrl(),
                    summary.getSummary() == null ? 0 : summary.getSummary().length(),
                    summary.getTags() == null ? 0 : summary.getTags().size(),
                    summary.getKeyFacts() == null ? 0 : summary.getKeyFacts().size());
            return summary;
        });
    }

    public ResolvedPersonProfile summarizePersonFromPageSummaries(String fallbackName, List<PageSummary> pageSummaries) {
        DeepSeekApiProperties deepseek = properties.getApi().getDeepseek();
        validateConfig(deepseek);
        return RetryUtils.execute("DeepSeek 人物总结", deepseek.getMaxRetries(), deepseek.getBackoffInitialMs(), () -> {
            JsonNode body = callDeepSeek(deepseek, buildPersonRequest(deepseek, fallbackName, pageSummaries));
            return parseProfileFromPageSummaries(fallbackName, pageSummaries, body);
        });
    }

    public String summarizeSectionFromPageSummaries(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        DeepSeekApiProperties deepseek = properties.getApi().getDeepseek();
        validateConfig(deepseek);
        return RetryUtils.execute("DeepSeek 主题摘要", deepseek.getMaxRetries(), deepseek.getBackoffInitialMs(), () -> {
            JsonNode body = callDeepSeek(deepseek, buildSectionRequest(deepseek, resolvedName, sectionType, pageSummaries));
            return parseSectionSummary(body);
        });
    }

    public TopicExpansionDecision expandTopicQueriesFromPageSummaries(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        DeepSeekApiProperties deepseek = properties.getApi().getDeepseek();
        validateConfig(deepseek);
        return RetryUtils.execute("DeepSeek 主题扩展推断", deepseek.getMaxRetries(), deepseek.getBackoffInitialMs(), () -> {
            JsonNode body = callDeepSeek(deepseek, buildTopicExpansionRequest(deepseek, resolvedName, sectionType, pageSummaries));
            return parseTopicExpansionDecision(body);
        });
    }

    public SectionedSummary summarizeSectionedSectionFromPageSummaries(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        DeepSeekApiProperties deepseek = properties.getApi().getDeepseek();
        validateConfig(deepseek);
        return RetryUtils.execute("DeepSeek 主题分段摘要", deepseek.getMaxRetries(), deepseek.getBackoffInitialMs(), () -> {
            JsonNode body = callDeepSeek(deepseek, buildSectionedTopicRequest(deepseek, resolvedName, sectionType, pageSummaries));
            return parseSectionedSummary(body);
        });
    }

    public ResolvedPersonProfile applyComprehensiveJudgement(String fallbackName,
                                                             List<PageSummary> pageSummaries,
                                                             ResolvedPersonProfile draftProfile) {
        DeepSeekApiProperties deepseek = properties.getApi().getDeepseek();
        validateConfig(deepseek);
        return RetryUtils.execute("DeepSeek 综合判断", deepseek.getMaxRetries(), deepseek.getBackoffInitialMs(), () -> {
            JsonNode body = callDeepSeek(deepseek, buildJudgementRequest(deepseek, fallbackName, pageSummaries, draftProfile));
            return parseJudgedProfile(fallbackName, pageSummaries, draftProfile, body);
        });
    }

    private void validateConfig(DeepSeekApiProperties deepseek) {
        if (!StringUtils.hasText(deepseek.getApiKey())
                || !StringUtils.hasText(deepseek.getBaseUrl())
                || !StringUtils.hasText(deepseek.getModel())) {
            log.error("DeepSeek 配置缺失 baseUrlConfigured={} modelConfigured={} apiKeyConfigured={}",
                    StringUtils.hasText(deepseek.getBaseUrl()),
                    StringUtils.hasText(deepseek.getModel()),
                    StringUtils.hasText(deepseek.getApiKey()));
            throw new ApiCallException("CONFIG_MISSING: DeepSeek 配置不完整");
        }
    }

    private JsonNode callDeepSeek(DeepSeekApiProperties deepseek, Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(deepseek.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        log.info("DeepSeek 请求已发送 model={} url={}", deepseek.getModel(), LogSanitizer.maskUrl(deepseek.getBaseUrl()));
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                deepseek.getBaseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                JsonNode.class
        );
        return response.getBody();
    }

    private Map<String, Object> buildPageRequest(DeepSeekApiProperties deepseek, String fallbackName, PageContent page) {
        return Map.of(
                "model", deepseek.getModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", firstNonBlank(deepseek.getSystemPrompt(), "你是人物公开信息聚合助手，只能输出 JSON。")),
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
                "tool_choice", Map.of("type", "function", "function", Map.of("name", PAGE_SUMMARY_FUNCTION_NAME))
        );
    }

    private Map<String, Object> buildPersonRequest(DeepSeekApiProperties deepseek,
                                                   String fallbackName,
                                                   List<PageSummary> pageSummaries) {
        return Map.of(
                "model", deepseek.getModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", firstNonBlank(deepseek.getSystemPrompt(), "你是人物公开信息聚合助手，只能输出 JSON。")),
                        Map.of("role", "user", "content", buildPersonPrompt(fallbackName, pageSummaries))
                ),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", PERSON_PROFILE_FUNCTION_NAME,
                                "description", "提交人物聚合画像的结构化结果",
                                "parameters", profileSchema()
                        )
                )),
                "tool_choice", Map.of("type", "function", "function", Map.of("name", PERSON_PROFILE_FUNCTION_NAME))
        );
    }

    private Map<String, Object> buildJudgementRequest(DeepSeekApiProperties deepseek,
                                                      String fallbackName,
                                                      List<PageSummary> pageSummaries,
                                                      ResolvedPersonProfile draftProfile) {
        return Map.of(
                "model", deepseek.getModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", firstNonBlank(deepseek.getSystemPrompt(), "你是人物公开信息聚合助手，只能输出 JSON。")),
                        Map.of("role", "user", "content", buildJudgementPrompt(fallbackName, pageSummaries, draftProfile))
                ),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", PROFILE_JUDGEMENT_FUNCTION_NAME,
                                "description", "提交综合判断后的最终人物画像",
                                "parameters", profileSchema()
                        )
                )),
                "tool_choice", Map.of("type", "function", "function", Map.of("name", PROFILE_JUDGEMENT_FUNCTION_NAME))
        );
    }

    private Map<String, Object> buildSectionRequest(DeepSeekApiProperties deepseek,
                                                    String resolvedName,
                                                    String sectionType,
                                                    List<PageSummary> pageSummaries) {
        return Map.of(
                "model", deepseek.getModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", firstNonBlank(deepseek.getSystemPrompt(), "你是人物公开信息聚合助手，只能输出 JSON。")),
                        Map.of("role", "user", "content", buildSectionPrompt(resolvedName, sectionType, pageSummaries))
                ),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", SECTION_SUMMARY_FUNCTION_NAME,
                                "description", "提交人物某个主题的单段摘要",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of("summary", Map.of("type", "string")),
                                        "required", List.of("summary"),
                                        "additionalProperties", false
                                )
                        )
                )),
                "tool_choice", Map.of("type", "function", "function", Map.of("name", SECTION_SUMMARY_FUNCTION_NAME))
        );
    }

    // 这里强制模型返回 term/section/reason 三元组，服务层才能把扩展理由精确挂到对应小标题下。
    private Map<String, Object> buildTopicExpansionRequest(DeepSeekApiProperties deepseek,
                                                           String resolvedName,
                                                           String sectionType,
                                                           List<PageSummary> pageSummaries) {
        return Map.of(
                "model", deepseek.getModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", firstNonBlank(deepseek.getSystemPrompt(), "你是人物公开信息聚合助手，只能输出 JSON。")),
                        Map.of("role", "user", "content", buildTopicExpansionPrompt(resolvedName, sectionType, pageSummaries))
                ),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", TOPIC_EXPANSION_FUNCTION_NAME,
                                "description", "提交主题扩展搜索建议",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "shouldExpand", Map.of("type", "boolean"),
                                                "expansionQueries", Map.of(
                                                        "type", "array",
                                                        "items", Map.of(
                                                                "type", "object",
                                                                "properties", Map.of(
                                                                        "term", Map.of("type", "string"),
                                                                        "section", Map.of("type", "string"),
                                                                        "reason", Map.of("type", "string")
                                                                ),
                                                                "required", List.of("term"),
                                                                "additionalProperties", false
                                                        )
                                                )
                                        ),
                                        "required", List.of("shouldExpand", "expansionQueries"),
                                        "additionalProperties", false
                                )
                        )
                )),
                "tool_choice", Map.of("type", "function", "function", Map.of("name", TOPIC_EXPANSION_FUNCTION_NAME))
        );
    }

    // 分段摘要只允许落到预设 section 名称，避免不同模型输出自由标题导致前端展示漂移。
    private Map<String, Object> buildSectionedTopicRequest(DeepSeekApiProperties deepseek,
                                                           String resolvedName,
                                                           String sectionType,
                                                           List<PageSummary> pageSummaries) {
        return Map.of(
                "model", deepseek.getModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", firstNonBlank(deepseek.getSystemPrompt(), "你是人物公开信息聚合助手，只能输出 JSON。")),
                        Map.of("role", "user", "content", buildSectionedTopicPrompt(resolvedName, sectionType, pageSummaries))
                ),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", SECTIONED_FAMILY_SUMMARY_FUNCTION_NAME,
                                "description", "提交主题分段摘要",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "sections", Map.of(
                                                        "type", "array",
                                                        "items", Map.of(
                                                                "type", "object",
                                                                "properties", Map.of(
                                                                        "section", Map.of("type", "string"),
                                                                        "summary", Map.of("type", "string")
                                                                ),
                                                                "required", List.of("section", "summary"),
                                                                "additionalProperties", false
                                                        )
                                                )
                                        ),
                                        "required", List.of("sections"),
                                        "additionalProperties", false
                                )
                        )
                )),
                "tool_choice", Map.of("type", "function", "function", Map.of("name", SECTIONED_FAMILY_SUMMARY_FUNCTION_NAME))
        );
    }

    private Map<String, Object> profileSchema() {
        return Map.ofEntries(
                Map.entry("type", "object"),
                Map.entry("properties", Map.ofEntries(
                        Map.entry("resolvedName", Map.of("type", "string")),
                        Map.entry("description", Map.of("type", "string")),
                        Map.entry("summary", Map.of("type", "string")),
                        Map.entry("educationSummary", Map.of("type", "string")),
                        Map.entry("familyBackgroundSummary", Map.of("type", "string")),
                        Map.entry("careerSummary", Map.of("type", "string")),
                        Map.entry("chinaRelatedStatementsSummary", Map.of("type", "string")),
                        Map.entry("politicalTendencySummary", Map.of("type", "string")),
                        Map.entry("contactInformationSummary", Map.of("type", "string")),
                        Map.entry("familyMemberSituationSummary", Map.of("type", "string")),
                        Map.entry("misconductSummary", Map.of("type", "string")),
                        Map.entry("keyFacts", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("tags", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("wikipedia", Map.of("type", "string")),
                        Map.entry("officialWebsite", Map.of("type", "string")),
                        Map.entry("basicInfo", Map.ofEntries(
                                Map.entry("type", "object"),
                                Map.entry("properties", Map.ofEntries(
                                        Map.entry("birthDate", Map.of("type", "string")),
                                        Map.entry("education", Map.of("type", "array", "items", Map.of("type", "string"))),
                                        Map.entry("occupations", Map.of("type", "array", "items", Map.of("type", "string"))),
                                        Map.entry("biographies", Map.of("type", "array", "items", Map.of("type", "string")))
                                )),
                                Map.entry("additionalProperties", false)
                        )),
                        Map.entry("evidenceUrls", Map.of("type", "array", "items", Map.of("type", "string")))
                )),
                Map.entry("required", List.of("resolvedName")),
                Map.entry("additionalProperties", false)
        );
    }

    private String buildPagePrompt(String fallbackName, PageContent page) {
        return """
                请基于以下单篇正文提取人物信息。
                必须满足以下约束：
                1. 只能通过函数 submit_page_summary 返回结果，禁止输出解释、道歉、思考过程、Markdown 代码块或任何额外文本。
                2. 返回内容语言必须为中文。
                3. 即使信息不足，也必须按既定字段返回 JSON；keyFacts/tags 返回空数组，summary 必须给出最保守的正文摘要。
                JSON 字段固定为 summary、keyFacts、tags、sourceUrl、title。
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
                        summary: %s
                        keyFacts: %s
                        tags: %s
                        """.formatted(
                        summary.getSourceUrl(),
                        summary.getTitle(),
                        summary.getSummary(),
                        summary.getKeyFacts(),
                        summary.getTags()
                ))
                .collect(Collectors.joining("\n---\n"));

        return """
                请基于以下篇级摘要集合生成人物最终画像。
                必须满足以下约束：
                1. 只能通过函数 submit_person_profile 返回结果，禁止输出解释、道歉、思考过程、Markdown 代码块或任何额外文本。
                2. 返回内容语言必须为中文。
                3. 即使证据有限，也必须按既定字段返回 JSON；缺失字段返回空字符串或空数组，不允许自然语言拒答。
                JSON 字段固定为 resolvedName、description、summary、educationSummary、familyBackgroundSummary、careerSummary、chinaRelatedStatementsSummary、politicalTendencySummary、contactInformationSummary、familyMemberSituationSummary、misconductSummary、keyFacts、tags、wikipedia、officialWebsite、basicInfo、evidenceUrls。
                summary 只写人物主体信息与关键细节，必须详细、清晰，不要简短结论，也不要重复 educationSummary、familyBackgroundSummary、careerSummary、chinaRelatedStatementsSummary、politicalTendencySummary、contactInformationSummary、familyMemberSituationSummary、misconductSummary 的内容。
                basicInfo 为对象，字段固定为 birthDate、education、occupations、biographies。
                fallbackName: %s
                篇级摘要如下：
                %s
                """.formatted(fallbackName, pageSummaryContent);
    }

    private String buildJudgementPrompt(String fallbackName,
                                        List<PageSummary> pageSummaries,
                                        ResolvedPersonProfile draftProfile) {
        String pageSummaryContent = pageSummaries == null ? "" : pageSummaries.stream()
                .map(summary -> """
                        sourceUrl: %s
                        title: %s
                        summary: %s
                        keyFacts: %s
                        tags: %s
                        """.formatted(
                        summary.getSourceUrl(),
                        summary.getTitle(),
                        summary.getSummary(),
                        summary.getKeyFacts(),
                        summary.getTags()
                ))
                .collect(Collectors.joining("\n---\n"));

        return """
                请基于篇级总结和最终总结草稿进行一次综合判断，输出更稳健的人物最终画像。
                必须满足以下约束：
                1. 只能通过函数 submit_profile_judgement 返回结果，禁止输出解释、道歉、思考过程、Markdown 代码块或任何额外文本。
                2. 返回内容语言必须为中文。
                3. 即使结论不确定，也必须按既定字段返回 JSON；不允许自然语言拒答。
                JSON 字段固定为 resolvedName、description、summary、educationSummary、familyBackgroundSummary、careerSummary、chinaRelatedStatementsSummary、politicalTendencySummary、contactInformationSummary、familyMemberSituationSummary、misconductSummary、keyFacts、tags、wikipedia、officialWebsite、basicInfo、evidenceUrls。
                summary 只写人物主体信息与关键细节，必须详细、清晰，不要简短结论，也不要重复 educationSummary、familyBackgroundSummary、careerSummary、chinaRelatedStatementsSummary、politicalTendencySummary、contactInformationSummary、familyMemberSituationSummary、misconductSummary 的内容。
                basicInfo 为对象，字段固定为 birthDate、education、occupations、biographies。
                fallbackName: %s
                draftResolvedName: %s
                draftDescription: %s
                draftSummary: %s
                draftKeyFacts: %s
                draftTags: %s
                draftEvidenceUrls: %s
                篇级摘要如下：
                %s
                """.formatted(
                fallbackName,
                draftProfile == null ? null : draftProfile.getResolvedName(),
                draftProfile == null ? null : draftProfile.getDescription(),
                draftProfile == null ? null : draftProfile.getSummary(),
                draftProfile == null ? null : draftProfile.getKeyFacts(),
                draftProfile == null ? null : draftProfile.getTags(),
                draftProfile == null ? null : draftProfile.getEvidenceUrls(),
                pageSummaryContent
        );
    }

    private String buildSectionPrompt(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        String pageSummaryContent = pageSummaries == null ? "" : pageSummaries.stream()
                .map(summary -> """
                        sourceUrl: %s
                        title: %s
                        summary: %s
                        keyFacts: %s
                        tags: %s
                        """.formatted(
                        summary.getSourceUrl(),
                        summary.getTitle(),
                        summary.getSummary(),
                        summary.getKeyFacts(),
                        summary.getTags()
                ))
                .collect(Collectors.joining("\n---\n"));

        return """
                请基于以下篇级摘要，为人物生成一个主题单段摘要。
                必须满足以下约束：
                1. 只能通过函数 submit_section_summary 返回结果，禁止输出解释、道歉、思考过程、Markdown 代码块或任何额外文本。
                2. 返回内容语言必须为中文。
                3. 即使信息不足，也必须返回 JSON；summary 可为空字符串，但不能返回自然语言拒答。
                JSON 字段固定为 summary。
                主题只允许是 education、family、career、china_related_statements、political_view、contact_information、family_member_situation、misconduct 之一。
                resolvedName: %s
                sectionType: %s
                篇级摘要如下：
                %s
                """.formatted(resolvedName, sectionType, pageSummaryContent);
    }

    private PageSummary parsePageSummary(PageContent page, JsonNode body) {
        String content = extractStructuredPayload(body, PAGE_SUMMARY_FUNCTION_NAME);
        try {
            JsonNode json = readStructuredJson(content, "DeepSeek 页面摘要");
            String summary = json.path("summary").asText(null);
            if (!StringUtils.hasText(summary)) {
                throw new ApiCallException("EMPTY_RESPONSE: DeepSeek 页面摘要为空");
            }
            return new PageSummary()
                    .setSourceUrl(firstNonBlank(trimToNull(json.path("sourceUrl").asText(null)), page == null ? null : page.getUrl()))
                    .setTitle(firstNonBlank(trimToNull(json.path("title").asText(null)), page == null ? null : page.getTitle()))
                    .setSummary(summary.trim())
                    .setKeyFacts(readStringList(json.path("keyFacts")))
                    .setTags(readStringList(json.path("tags")));
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("INVALID_RESPONSE: DeepSeek 页面摘要不是合法 JSON", ex);
        }
    }

    private ResolvedPersonProfile parseProfileFromPageSummaries(String fallbackName,
                                                                List<PageSummary> pageSummaries,
                                                                JsonNode body) {
        String content = extractStructuredPayload(body, PERSON_PROFILE_FUNCTION_NAME);
        try {
            JsonNode json = readStructuredJson(content, "DeepSeek 最终画像");
            return toProfile(json, fallbackName, pageSummaries == null ? List.of() : pageSummaries, null);
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("INVALID_RESPONSE: DeepSeek 返回内容不是合法 JSON", ex);
        }
    }

    private ResolvedPersonProfile parseJudgedProfile(String fallbackName,
                                                     List<PageSummary> pageSummaries,
                                                     ResolvedPersonProfile draftProfile,
                                                     JsonNode body) {
        String content = extractStructuredPayload(body, PROFILE_JUDGEMENT_FUNCTION_NAME);
        try {
            JsonNode json = readStructuredJson(content, "DeepSeek 综合判断");
            return toProfile(json, fallbackName, pageSummaries == null ? List.of() : pageSummaries, draftProfile);
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("INVALID_RESPONSE: DeepSeek 综合判断内容不是合法 JSON", ex);
        }
    }

    private String parseSectionSummary(JsonNode body) {
        String content = extractStructuredPayload(body, SECTION_SUMMARY_FUNCTION_NAME);
        try {
            JsonNode json = readStructuredJson(content, "DeepSeek 主题摘要");
            return trimToNull(json.path("summary").asText(null));
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("INVALID_RESPONSE: DeepSeek 主题摘要不是合法 JSON", ex);
        }
    }

    private TopicExpansionDecision parseTopicExpansionDecision(JsonNode body) {
        String content = extractStructuredPayload(body, TOPIC_EXPANSION_FUNCTION_NAME);
        try {
            JsonNode json = readStructuredJson(content, "DeepSeek 主题扩展推断");
            return new TopicExpansionDecision()
                    .setShouldExpand(json.path("shouldExpand").asBoolean(false))
                    .setExpansionQueries(readExpansionQueries(json.path("expansionQueries")));
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("INVALID_RESPONSE: DeepSeek 主题扩展推断不是合法 JSON", ex);
        }
    }

    private SectionedSummary parseSectionedSummary(JsonNode body) {
        String content = extractStructuredPayload(body, SECTIONED_FAMILY_SUMMARY_FUNCTION_NAME);
        try {
            JsonNode json = readStructuredJson(content, "DeepSeek 家族成员分段摘要");
            return new SectionedSummary().setSections(readSectionSummaryItems(json.path("sections")));
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("INVALID_RESPONSE: DeepSeek 家族成员分段摘要不是合法 JSON", ex);
        }
    }

    private ResolvedPersonProfile toProfile(JsonNode json,
                                            String fallbackName,
                                            List<PageSummary> pageSummaries,
                                            ResolvedPersonProfile draftProfile) {
        List<String> evidenceUrls = readStringList(json.path("evidenceUrls"));
        if (evidenceUrls.isEmpty() && pageSummaries != null && !pageSummaries.isEmpty()) {
            evidenceUrls = pageSummaries.stream()
                    .map(PageSummary::getSourceUrl)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        if (evidenceUrls.isEmpty() && draftProfile != null && draftProfile.getEvidenceUrls() != null) {
            evidenceUrls = draftProfile.getEvidenceUrls();
        }

        ResolvedPersonProfile profile = new ResolvedPersonProfile()
                .setResolvedName(firstNonBlank(trimToNull(json.path("resolvedName").asText(null)), fallbackName))
                .setDescription(trimToNull(json.path("description").asText(null)))
                .setSummary(trimToNull(json.path("summary").asText(null)))
                .setEducationSummary(trimToNull(json.path("educationSummary").asText(null)))
                .setFamilyBackgroundSummary(trimToNull(json.path("familyBackgroundSummary").asText(null)))
                .setCareerSummary(trimToNull(json.path("careerSummary").asText(null)))
                .setChinaRelatedStatementsSummary(trimToNull(json.path("chinaRelatedStatementsSummary").asText(null)))
                .setPoliticalTendencySummary(trimToNull(json.path("politicalTendencySummary").asText(null)))
                .setContactInformationSummary(trimToNull(json.path("contactInformationSummary").asText(null)))
                .setFamilyMemberSituationSummary(trimToNull(json.path("familyMemberSituationSummary").asText(null)))
                .setMisconductSummary(trimToNull(json.path("misconductSummary").asText(null)))
                .setKeyFacts(readStringList(json.path("keyFacts")))
                .setTags(readStringList(json.path("tags")))
                .setWikipedia(trimToNull(json.path("wikipedia").asText(null)))
                .setOfficialWebsite(trimToNull(json.path("officialWebsite").asText(null)))
                .setBasicInfo(readBasicInfo(json.path("basicInfo")))
                .setEvidenceUrls(evidenceUrls);

        // 某些模型会把后续 parameter 片段串进 summary 字段里，这里优先把被污染的结构化字段回收出来，
        // 保证返回给前端的仍然是稳定的 profile 结构，而不是把协议碎片直接暴露出去。
        recoverContaminatedProfileFields(profile);

        if (draftProfile != null) {
            if (!StringUtils.hasText(profile.getDescription())) {
                profile.setDescription(draftProfile.getDescription());
            }
            if (!StringUtils.hasText(profile.getSummary())) {
                profile.setSummary(draftProfile.getSummary());
            }
            if (!StringUtils.hasText(profile.getEducationSummary())) {
                profile.setEducationSummary(draftProfile.getEducationSummary());
            }
            if (!StringUtils.hasText(profile.getFamilyBackgroundSummary())) {
                profile.setFamilyBackgroundSummary(draftProfile.getFamilyBackgroundSummary());
            }
            if (!StringUtils.hasText(profile.getCareerSummary())) {
                profile.setCareerSummary(draftProfile.getCareerSummary());
            }
            if (!StringUtils.hasText(profile.getChinaRelatedStatementsSummary())) {
                profile.setChinaRelatedStatementsSummary(draftProfile.getChinaRelatedStatementsSummary());
            }
            if (!StringUtils.hasText(profile.getPoliticalTendencySummary())) {
                profile.setPoliticalTendencySummary(draftProfile.getPoliticalTendencySummary());
            }
            if (!StringUtils.hasText(profile.getContactInformationSummary())) {
                profile.setContactInformationSummary(draftProfile.getContactInformationSummary());
            }
            if (!StringUtils.hasText(profile.getFamilyMemberSituationSummary())) {
                profile.setFamilyMemberSituationSummary(draftProfile.getFamilyMemberSituationSummary());
            }
            if (!StringUtils.hasText(profile.getMisconductSummary())) {
                profile.setMisconductSummary(draftProfile.getMisconductSummary());
            }
            if ((profile.getKeyFacts() == null || profile.getKeyFacts().isEmpty()) && draftProfile.getKeyFacts() != null) {
                profile.setKeyFacts(draftProfile.getKeyFacts());
            }
            if ((profile.getTags() == null || profile.getTags().isEmpty()) && draftProfile.getTags() != null) {
                profile.setTags(draftProfile.getTags());
            }
        }
        return profile;
    }

    private void recoverContaminatedProfileFields(ResolvedPersonProfile profile) {
        if (profile == null || !StringUtils.hasText(profile.getSummary())) {
            return;
        }
        String summary = profile.getSummary();
        Matcher matcher = SERIALIZED_PARAMETER_PATTERN.matcher(summary);
        if (!matcher.find()) {
            profile.setSummary(stripProtocolArtifacts(summary));
            return;
        }

        Map<String, JsonNode> recoveredParameters = new LinkedHashMap<>();
        matcher.reset();
        while (matcher.find()) {
            String name = trimToNull(matcher.group(1));
            if (!StringUtils.hasText(name)) {
                continue;
            }
            recoveredParameters.put(name, toJsonValueNode(matcher.group(2)));
        }

        Matcher firstParameterMatcher = SERIALIZED_PARAMETER_PATTERN.matcher(summary);
        int firstParameterIndex = firstParameterMatcher.find() ? firstParameterMatcher.start() : summary.length();
        String cleanSummary = trimToNull(stripProtocolArtifacts(summary.substring(0, firstParameterIndex)));
        JsonNode summaryNode = recoveredParameters.get("summary");
        if (summaryNode != null && !summaryNode.isNull() && StringUtils.hasText(summaryNode.asText())) {
            profile.setSummary(trimToNull(summaryNode.asText()));
        } else if (StringUtils.hasText(cleanSummary)) {
            profile.setSummary(cleanSummary);
        }

        applyRecoveredProfileField(profile, "educationSummary", recoveredParameters.get("educationSummary"));
        applyRecoveredProfileField(profile, "familyBackgroundSummary", recoveredParameters.get("familyBackgroundSummary"));
        applyRecoveredProfileField(profile, "careerSummary", recoveredParameters.get("careerSummary"));
        applyRecoveredProfileField(profile, "chinaRelatedStatementsSummary", recoveredParameters.get("chinaRelatedStatementsSummary"));
        applyRecoveredProfileField(profile, "politicalTendencySummary", recoveredParameters.get("politicalTendencySummary"));
        applyRecoveredProfileField(profile, "contactInformationSummary", recoveredParameters.get("contactInformationSummary"));
        applyRecoveredProfileField(profile, "familyMemberSituationSummary", recoveredParameters.get("familyMemberSituationSummary"));
        applyRecoveredProfileField(profile, "misconductSummary", recoveredParameters.get("misconductSummary"));
        applyRecoveredProfileField(profile, "keyFacts", recoveredParameters.get("keyFacts"));
        applyRecoveredProfileField(profile, "tags", recoveredParameters.get("tags"));
        applyRecoveredProfileField(profile, "wikipedia", recoveredParameters.get("wikipedia"));
        applyRecoveredProfileField(profile, "officialWebsite", recoveredParameters.get("officialWebsite"));
        applyRecoveredProfileField(profile, "basicInfo", recoveredParameters.get("basicInfo"));
    }

    private void applyRecoveredProfileField(ResolvedPersonProfile profile, String fieldName, JsonNode valueNode) {
        if (profile == null || valueNode == null || valueNode.isNull()) {
            return;
        }
        switch (fieldName) {
            case "educationSummary" -> profile.setEducationSummary(trimToNull(valueNode.asText(null)));
            case "familyBackgroundSummary" -> profile.setFamilyBackgroundSummary(trimToNull(valueNode.asText(null)));
            case "careerSummary" -> profile.setCareerSummary(trimToNull(valueNode.asText(null)));
            case "chinaRelatedStatementsSummary" -> profile.setChinaRelatedStatementsSummary(trimToNull(valueNode.asText(null)));
            case "politicalTendencySummary" -> profile.setPoliticalTendencySummary(trimToNull(valueNode.asText(null)));
            case "contactInformationSummary" -> profile.setContactInformationSummary(trimToNull(valueNode.asText(null)));
            case "familyMemberSituationSummary" -> profile.setFamilyMemberSituationSummary(trimToNull(valueNode.asText(null)));
            case "misconductSummary" -> profile.setMisconductSummary(trimToNull(valueNode.asText(null)));
            case "keyFacts" -> profile.setKeyFacts(readStringList(valueNode));
            case "tags" -> profile.setTags(readStringList(valueNode));
            case "wikipedia" -> profile.setWikipedia(trimToNull(valueNode.asText(null)));
            case "officialWebsite" -> profile.setOfficialWebsite(trimToNull(valueNode.asText(null)));
            case "basicInfo" -> profile.setBasicInfo(readBasicInfo(valueNode));
            default -> {
            }
        }
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
        String content = messageNode == null ? null : messageNode.path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new ApiCallException("EMPTY_RESPONSE: DeepSeek 返回内容为空");
        }
        // SophNet DeepSeek 有时不会回标准 tool_calls，而是把函数调用序列化到 message.content 中。
        String xmlPayload = extractXmlInvokePayload(content, expectedFunctionName);
        if (StringUtils.hasText(xmlPayload)) {
            return xmlPayload;
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

    private List<TopicExpansionQuery> readExpansionQueries(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<TopicExpansionQuery> queries = new java.util.ArrayList<>();
        for (JsonNode item : arrayNode) {
            queries.add(new TopicExpansionQuery()
                    .setTerm(trimToNull(item.path("term").asText(null)))
                    .setSection(trimToNull(item.path("section").asText(null)))
                    .setReason(trimToNull(item.path("reason").asText(null))));
        }
        return List.copyOf(queries);
    }

    private List<SectionSummaryItem> readSectionSummaryItems(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<SectionSummaryItem> items = new java.util.ArrayList<>();
        for (JsonNode item : arrayNode) {
            items.add(new SectionSummaryItem()
                    .setSection(trimToNull(item.path("section").asText(null)))
                    .setSummary(trimToNull(item.path("summary").asText(null))));
        }
        return List.copyOf(items);
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeJsonContent(String content) {
        String trimmed = trimToNull(content);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```json\\s*", "");
            trimmed = trimmed.replaceFirst("^```\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    private JsonNode readStructuredJson(String content, String scene) throws JsonProcessingException {
        String candidate = extractJsonCandidate(content);
        if (!StringUtils.hasText(candidate)) {
            throw new ApiCallException("INVALID_RESPONSE: " + scene + "未返回 JSON，content=" + previewContent(content));
        }
        return objectMapper.readTree(candidate);
    }

    private String extractJsonCandidate(String content) {
        String normalized = normalizeJsonContent(content);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        if (normalized.startsWith("{") || normalized.startsWith("[")) {
            return normalized;
        }
        // 兼容“解释文本 + JSON”这类混合输出，优先裁出首个对象 JSON。
        String objectCandidate = findBalancedJsonSegment(normalized, '{', '}');
        if (StringUtils.hasText(objectCandidate)) {
            return objectCandidate;
        }
        return findBalancedJsonSegment(normalized, '[', ']');
    }

    private String findBalancedJsonSegment(String content, char openChar, char closeChar) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == openChar) {
                if (depth == 0) {
                    start = i;
                }
                depth++;
                continue;
            }
            if (current == closeChar && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    return content.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private String buildTopicExpansionPrompt(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        String pageSummaryContent = pageSummaries == null ? "" : pageSummaries.stream()
                .map(summary -> """
                        sourceUrl: %s
                        title: %s
                        summary: %s
                        """.formatted(summary.getSourceUrl(), summary.getTitle(), summary.getSummary()))
                .collect(Collectors.joining("\n---\n"));
        return """
                请基于以下人物主题篇级摘要，判断是否需要继续追加公开资料搜索。
                你的任务不是总结主题内容，而是生成下一轮可检索的扩展词。
                sectionType: %s
                resolvedName: %s
                只能返回 JSON。
                篇级摘要如下：
                %s
                """.formatted(sectionType, resolvedName, pageSummaryContent);
    }

    private String buildSectionedTopicPrompt(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        String pageSummaryContent = pageSummaries == null ? "" : pageSummaries.stream()
                .map(summary -> """
                        sourceUrl: %s
                        title: %s
                        summary: %s
                        """.formatted(summary.getSourceUrl(), summary.getTitle(), summary.getSummary()))
                .collect(Collectors.joining("\n---\n"));
        String allowedSections = String.join("、", allowedSectionNames(sectionType));
        return """
                请基于以下主题篇级摘要，输出分段摘要 JSON。
                resolvedName: %s
                sectionType: %s
                可用分段标题: %s
                只能返回 JSON。
                篇级摘要如下：
                %s
                """.formatted(resolvedName, sectionType, allowedSections, pageSummaryContent);
    }

    private List<String> allowedSectionNames(String sectionType) {
        return switch (sectionType) {
            case "china_related_statements" -> List.of("涉华言论", "中国评价", "国际关系", "相关争议");
            case "political_view" -> List.of("政治倾向", "党派与组织", "政治理念", "政策立场");
            case "contact_information" -> List.of("公开通讯", "办公电话", "官方邮箱", "认证社交账号", "其他联系方式");
            case "family_member_situation" -> List.of("家庭成员", "亲属信息", "经商与投资", "争议与纠纷");
            case "misconduct" -> List.of("违法记录", "行政处罚", "负面事件", "失信信息");
            default -> List.of(sectionType);
        };
    }

    private String previewContent(String content) {
        String normalized = trimToNull(content);
        if (normalized == null) {
            return "<empty>";
        }
        String singleLine = normalized.replaceAll("\\s+", " ");
        return singleLine.length() <= 120 ? singleLine : singleLine.substring(0, 120) + "...";
    }

    private String extractXmlInvokePayload(String content, String expectedFunctionName) {
        if (!StringUtils.hasText(content) || !content.contains("<invoke")) {
            return null;
        }
        Matcher invokeMatcher = XML_INVOKE_PATTERN.matcher(content);
        while (invokeMatcher.find()) {
            String functionName = invokeMatcher.group(1);
            if (!expectedFunctionName.equals(functionName)) {
                continue;
            }
            String invokeBody = invokeMatcher.group(2);
            Matcher parameterMatcher = XML_PARAMETER_PATTERN.matcher(invokeBody);
            Map<String, JsonNode> parameters = new LinkedHashMap<>();
            while (parameterMatcher.find()) {
                String name = trimToNull(parameterMatcher.group(1));
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                // XML parameter 最终仍要回到统一 JSON 解析流程，所以这里先转成 JsonNode 映射。
                parameters.put(name, toJsonValueNode(parameterMatcher.group(2)));
            }
            if (parameters.isEmpty()) {
                return null;
            }
            return objectMapper.valueToTree(parameters).toString();
        }
        return null;
    }

    private String stripProtocolArtifacts(String value) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        normalized = normalized.replaceAll("</[^>]*parameter>\\s*$", "");
        normalized = normalized.replaceAll("\\s*<\\/?[^>]*parameter[^>]*>\\s*", " ");
        return trimToNull(normalized);
    }

    private JsonNode toJsonValueNode(String rawValue) {
        String value = trimToNull(rawValue);
        if (value == null) {
            return objectMapper.nullNode();
        }
        if ((value.startsWith("{") && value.endsWith("}")) || (value.startsWith("[") && value.endsWith("]"))) {
            try {
                return objectMapper.readTree(value);
            } catch (JsonProcessingException ex) {
                log.debug("xml parameter json parse failed value={} error={}", previewContent(value), ex.getMessage());
            }
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "null".equalsIgnoreCase(value)) {
            try {
                return objectMapper.readTree(value.toLowerCase());
            } catch (JsonProcessingException ex) {
                log.debug("xml parameter literal parse failed value={} error={}", value, ex.getMessage());
            }
        }
        if (value.matches("-?\\d+(\\.\\d+)?")) {
            try {
                return objectMapper.readTree(value);
            } catch (JsonProcessingException ex) {
                log.debug("xml parameter number parse failed value={} error={}", value, ex.getMessage());
            }
        }
        return objectMapper.getNodeFactory().textNode(value);
    }
}
