package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.DeepSeekApiProperties;
import com.example.face2info.entity.internal.ArticleCitation;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ParagraphSource;
import com.example.face2info.entity.internal.ParagraphSummaryItem;
import com.example.face2info.entity.internal.PersonBasicInfo;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageInferenceResult;
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
    private static final String SEARCH_LANGUAGE_INFERENCE_FUNCTION_NAME = "submit_search_language_inference";
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
    private static final Pattern PAGE_SUMMARY_REFUSAL_PATTERN = Pattern.compile(
            "(暂时无法|无法直接|不能提供|无法提供|需要去思考|需要完整的正文|无法给出|无法总结|抱歉).*"
                    + "(摘要|结构化|帮助|正文|JSON|结果)?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern MEANINGFUL_TEXT_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z0-9]");
    private static final int MAX_REPEATED_UNIT_LENGTH = 8;
    private static final int MIN_REPEATED_UNIT_OCCURRENCES = 6;
    private static final double MIN_REPEATED_UNIT_COVERAGE = 0.85d;

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

    public SearchLanguageInferenceResult inferSearchLanguageProfile(String resolvedName,
                                                                    ResolvedPersonProfile profile) {
        DeepSeekApiProperties deepseek = properties.getApi().getDeepseek();
        validateConfig(deepseek);
        return RetryUtils.execute("DeepSeek 搜索语言推断", deepseek.getMaxRetries(), deepseek.getBackoffInitialMs(), () -> {
            JsonNode body = callDeepSeek(deepseek, buildSearchLanguageInferenceRequest(deepseek, resolvedName, profile));
            return parseSearchLanguageInferenceResult(extractStructuredPayload(body, SEARCH_LANGUAGE_INFERENCE_FUNCTION_NAME));
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
                                                "sourceId", Map.of("type", "integer"),
                                                "summary", Map.of("type", "string"),
                                                "summaryParagraphs", paragraphArraySchema(),
                                                "keyFacts", Map.of("type", "array", "items", Map.of("type", "string")),
                                                "tags", Map.of("type", "array", "items", Map.of("type", "string")),
                                                "sourceUrl", Map.of("type", "string"),
                                                "title", Map.of("type", "string"),
                                                "articleSources", articleCitationArraySchema()
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

    private Map<String, Object> buildSearchLanguageInferenceRequest(DeepSeekApiProperties deepseek,
                                                                    String resolvedName,
                                                                    ResolvedPersonProfile profile) {
        return Map.of(
                "model", deepseek.getModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", firstNonBlank(deepseek.getSystemPrompt(), "你是人物公开信息聚合助手，只能输出 JSON。")),
                        Map.of("role", "user", "content", buildSearchLanguageInferencePrompt(resolvedName, profile))
                ),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", SEARCH_LANGUAGE_INFERENCE_FUNCTION_NAME,
                                "description", "提交人物搜索语言推断结果",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "primaryNationality", Map.of("type", "string"),
                                                "recommendedLanguages", Map.of("type", "array", "items", Map.of("type", "string")),
                                                "localizedNames", Map.of("type", "object"),
                                                "reason", Map.of("type", "string"),
                                                "confidence", Map.of("type", "number")
                                        ),
                                        "required", List.of("recommendedLanguages", "localizedNames", "confidence"),
                                        "additionalProperties", false
                                )
                        )
                )),
                "tool_choice", Map.of("type", "function", "function", Map.of("name", SEARCH_LANGUAGE_INFERENCE_FUNCTION_NAME))
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
                                        "properties", Map.of(
                                                "summary", Map.of("type", "string"),
                                                "sourceIds", Map.of("type", "array", "items", Map.of("type", "integer"))
                                        ),
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
                        Map.entry("summaryParagraphs", paragraphArraySchema()),
                        Map.entry("educationSummary", Map.of("type", "string")),
                        Map.entry("educationSummaryParagraphs", paragraphArraySchema()),
                        Map.entry("familyBackgroundSummary", Map.of("type", "string")),
                        Map.entry("familyBackgroundSummaryParagraphs", paragraphArraySchema()),
                        Map.entry("careerSummary", Map.of("type", "string")),
                        Map.entry("careerSummaryParagraphs", paragraphArraySchema()),
                        Map.entry("chinaRelatedStatementsSummary", Map.of("type", "string")),
                        Map.entry("chinaRelatedStatementsSummaryParagraphs", paragraphArraySchema()),
                        Map.entry("politicalTendencySummary", Map.of("type", "string")),
                        Map.entry("politicalTendencySummaryParagraphs", paragraphArraySchema()),
                        Map.entry("contactInformationSummary", Map.of("type", "string")),
                        Map.entry("contactInformationSummaryParagraphs", paragraphArraySchema()),
                        Map.entry("familyMemberSituationSummary", Map.of("type", "string")),
                        Map.entry("familyMemberSituationSummaryParagraphs", paragraphArraySchema()),
                        Map.entry("misconductSummary", Map.of("type", "string")),
                        Map.entry("misconductSummaryParagraphs", paragraphArraySchema()),
                        Map.entry("keyFacts", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("tags", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("articleSources", articleCitationArraySchema()),
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
        String pageBody = buildPageBody(page);
        return """
                请基于以下单篇正文提取人物信息。
                必须满足以下约束：
                1. 只能通过函数 submit_page_summary 返回结果，禁止输出解释、道歉、思考过程、Markdown 代码块或任何额外文本。
                2. 返回内容语言必须为中文。
                3. 即使信息不足，也必须按既定字段返回 JSON；keyFacts/tags 返回空数组，summary 必须给出最保守的正文摘要。
                4. 编号代表文章编号，不代表段落编号。当前文章编号为 [%s]，每个句子后都必须追加当前文章编号。
                5. 只允许引用当前文章编号，不允许生成其他编号。
                JSON 字段固定为 sourceId、summary、summaryParagraphs、keyFacts、tags、sourceUrl、title、articleSources。
                fallbackName: %s
                articleId: %s
                title: %s
                url: %s
                正文如下：
                %s
                """.formatted(
                page == null || page.getSourceId() == null ? "1" : page.getSourceId(),
                fallbackName,
                page == null ? null : page.getSourceId(),
                page == null ? null : page.getTitle(),
                page == null ? null : page.getUrl(),
                pageBody
        );
    }

    private String buildPageBody(PageContent page) {
        if (page == null || !StringUtils.hasText(page.getContent())) {
            return page == null ? null : page.getContent();
        }
        int maxLength = properties.getApi().getSummary().getPageContentMaxLength();
        if (maxLength <= 0 || page.getContent().length() <= maxLength) {
            return page.getContent();
        }
        // 关键逻辑：单页正文先截断再摘要，避免文档镜像站整页文本直接超过模型上下文。
        return """
                正文过长，以下内容已按长度截断（原始长度=%d，保留前 %d 字符）：
                %s
                """.formatted(page.getContent().length(), maxLength, page.getContent().substring(0, maxLength));
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
                4. 编号代表文章编号，不代表段落编号；每个句子后都必须给出来源编号，格式为 [1] 或 [1][3]。
                5. 禁止引用文章编号表中不存在的编号。
                JSON 字段固定为 resolvedName、description、summary、summaryParagraphs、educationSummary、educationSummaryParagraphs、familyBackgroundSummary、familyBackgroundSummaryParagraphs、careerSummary、careerSummaryParagraphs、chinaRelatedStatementsSummary、chinaRelatedStatementsSummaryParagraphs、politicalTendencySummary、politicalTendencySummaryParagraphs、contactInformationSummary、contactInformationSummaryParagraphs、familyMemberSituationSummary、familyMemberSituationSummaryParagraphs、misconductSummary、misconductSummaryParagraphs、keyFacts、tags、articleSources、wikipedia、officialWebsite、basicInfo、evidenceUrls。
                summary 只写人物主体信息与关键细节，必须详细、清晰，不要简短结论，也不要重复 educationSummary、familyBackgroundSummary、careerSummary、chinaRelatedStatementsSummary、politicalTendencySummary、contactInformationSummary、familyMemberSituationSummary、misconductSummary 的内容。
                所有 *Paragraphs 字段必须返回数组；数组元素字段固定为 text、sourceIds、sourceUrls、sources。
                text 中必须直接写内联引用，格式为 [n]，例如“人物简介[1][2]”。不要生成引用来源列表、参考文献列表或单独的“来源：”段落。
                sourceIds 只能填写文章编号表中已出现的 id；sourceUrls 只能填写上方篇级摘要中已出现的 sourceUrl；sources 用于返回当前段落实际引用的来源对象。
                basicInfo 为对象，字段固定为 birthDate、education、occupations、biographies。
                fallbackName: %s
                文章编号表：
                %s
                篇级摘要如下：
                %s
                """.formatted(fallbackName, buildArticleCitationContext(pageSummaries), pageSummaryContent);
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
                请基于页面摘要集合和最终总结草稿进行一次综合判断，输出更稳健的人物最终画像。
                必须满足以下约束：
                1. 只能通过函数 submit_profile_judgement 返回结果，禁止输出解释、道歉、思考过程、Markdown 代码块或任何额外文本。
                2. 返回内容语言必须为中文。
                3. 即使结论不确定，也必须按既定字段返回 JSON；不允许自然语言拒答。
                4. 编号代表文章编号，不代表段落编号；每个句子后都必须给出来源编号，格式为 [1] 或 [1][3]。
                5. 禁止引用文章编号表中不存在的编号。
                JSON 字段固定为 resolvedName、description、summary、summaryParagraphs、educationSummary、educationSummaryParagraphs、familyBackgroundSummary、familyBackgroundSummaryParagraphs、careerSummary、careerSummaryParagraphs、chinaRelatedStatementsSummary、chinaRelatedStatementsSummaryParagraphs、politicalTendencySummary、politicalTendencySummaryParagraphs、contactInformationSummary、contactInformationSummaryParagraphs、familyMemberSituationSummary、familyMemberSituationSummaryParagraphs、misconductSummary、misconductSummaryParagraphs、keyFacts、tags、articleSources、wikipedia、officialWebsite、basicInfo、evidenceUrls。
                summary 只写人物主体信息与关键细节，必须详细、清晰，不要简短结论，也不要重复 educationSummary、familyBackgroundSummary、careerSummary、chinaRelatedStatementsSummary、politicalTendencySummary、contactInformationSummary、familyMemberSituationSummary、misconductSummary 的内容。
                所有 *Paragraphs 字段必须返回数组；数组元素字段固定为 text、sourceIds、sourceUrls、sources。
                text 中必须直接写内联引用，格式为 [n]，例如“人物简介[1][2]”。不要生成引用来源列表、参考文献列表或单独的“来源：”段落。
                sourceIds 只能填写文章编号表中已出现的 id；sourceUrls 只能填写上方篇级摘要中已出现的 sourceUrl；sources 用于返回当前段落实际引用的来源对象。
                basicInfo 为对象，字段固定为 birthDate、education、occupations、biographies。
                fallbackName: %s
                文章编号表：
                %s
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
                buildArticleCitationContext(pageSummaries),
                draftProfile == null ? null : draftProfile.getResolvedName(),
                draftProfile == null ? null : draftProfile.getDescription(),
                draftProfile == null ? null : draftProfile.getSummary(),
                draftProfile == null ? null : draftProfile.getKeyFacts(),
                draftProfile == null ? null : draftProfile.getTags(),
                draftProfile == null ? null : draftProfile.getEvidenceUrls(),
                pageSummaryContent
        );
    }

    private String buildSearchLanguageInferencePrompt(String resolvedName, ResolvedPersonProfile profile) {
        return """
                你是公开人物检索助手，只能通过函数 %s 返回 JSON，不允许输出解释。
                任务：根据人物已有资料，推断国籍、推荐搜索语言和多语言姓名。
                约束：
                1. recommendedLanguages 至少包含 zh 和 en。
                2. 如果证据不足，primaryNationality 返回 unknown。
                3. localizedNames 的 key 是语言代码，value 是适合公开搜索的姓名。
                4. confidence 返回 0 到 1 之间的小数。
                人物名：%s
                资料：
                %s
                """.formatted(
                SEARCH_LANGUAGE_INFERENCE_FUNCTION_NAME,
                resolvedName,
                summarizeInferenceInput(profile)
        );
    }

    private String summarizeInferenceInput(ResolvedPersonProfile profile) {
        if (profile == null) {
            return "";
        }
        return """
                resolvedName: %s
                description: %s
                summary: %s
                biographies: %s
                """.formatted(
                profile.getResolvedName(),
                profile.getDescription(),
                profile.getSummary(),
                profile.getBasicInfo() == null ? List.of() : profile.getBasicInfo().getBiographies()
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
                4. 编号代表文章编号，不代表段落编号；summary 中每个句子后都必须追加 [n] 或 [n][m]。
                5. sourceIds 只能填写文章编号表中已出现的 id。
                JSON 字段固定为 summary、sourceIds。
                主题只允许是 education、family、career、china_related_statements、political_view、contact_information、family_member_situation、misconduct 之一。
                resolvedName: %s
                sectionType: %s
                文章编号表：
                %s
                篇级摘要如下：
                %s
                """.formatted(resolvedName, sectionType, buildArticleCitationContext(pageSummaries), pageSummaryContent);
    }

    private PageSummary parsePageSummary(PageContent page, JsonNode body) {
        String content = extractStructuredPayload(body, PAGE_SUMMARY_FUNCTION_NAME);
        try {
            JsonNode json = readStructuredJson(content, "DeepSeek 页面摘要");
            return buildPageSummaryFromJson(page, json);
        } catch (ApiCallException ex) {
            String plainTextSummary = extractPlainTextPageSummary(content);
            if (StringUtils.hasText(plainTextSummary)) {
                log.warn("DeepSeek 页面摘要未返回 JSON，回退使用纯文本摘要 url={} preview={}",
                        page == null ? null : page.getUrl(),
                        previewContent(plainTextSummary));
                return new PageSummary()
                        .setSourceId(page == null ? null : page.getSourceId())
                        .setSourceUrl(page == null ? null : page.getUrl())
                        .setTitle(page == null ? null : page.getTitle())
                        .setSummary(plainTextSummary)
                        .setSummaryParagraphs(List.of())
                        .setKeyFacts(List.of())
                        .setTags(List.of())
                        .setArticleSources(List.of());
            }
            throw ex;
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("INVALID_RESPONSE: DeepSeek 页面摘要不是合法 JSON", ex);
        }
    }

    private PageSummary buildPageSummaryFromJson(PageContent page, JsonNode json) {
        String summary = json.path("summary").asText(null);
        if (!StringUtils.hasText(summary)) {
            throw new ApiCallException("EMPTY_RESPONSE: DeepSeek 页面摘要为空");
        }
        return new PageSummary()
                .setSourceId(json.path("sourceId").isInt() ? json.path("sourceId").asInt() : (page == null ? null : page.getSourceId()))
                .setSourceUrl(firstNonBlank(trimToNull(json.path("sourceUrl").asText(null)), page == null ? null : page.getUrl()))
                .setTitle(firstNonBlank(trimToNull(json.path("title").asText(null)), page == null ? null : page.getTitle()))
                .setSummary(summary.trim())
                .setSummaryParagraphs(readParagraphSummaryItems(json.path("summaryParagraphs")))
                .setKeyFacts(readStringList(json.path("keyFacts")))
                .setTags(readStringList(json.path("tags")))
                .setArticleSources(readArticleCitations(json.path("articleSources")));
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
            JsonNode json = readStructuredJson(content, "DeepSeek 主题分段摘要");
            return new SectionedSummary().setSections(readSectionSummaryItems(json.path("sections")));
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("INVALID_RESPONSE: DeepSeek 主题分段摘要不是合法 JSON", ex);
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
                .setSummaryParagraphs(readParagraphSummaryItems(json.path("summaryParagraphs")))
                .setEducationSummary(trimToNull(json.path("educationSummary").asText(null)))
                .setEducationSummaryParagraphs(readParagraphSummaryItems(json.path("educationSummaryParagraphs")))
                .setFamilyBackgroundSummary(trimToNull(json.path("familyBackgroundSummary").asText(null)))
                .setFamilyBackgroundSummaryParagraphs(readParagraphSummaryItems(json.path("familyBackgroundSummaryParagraphs")))
                .setCareerSummary(trimToNull(json.path("careerSummary").asText(null)))
                .setCareerSummaryParagraphs(readParagraphSummaryItems(json.path("careerSummaryParagraphs")))
                .setChinaRelatedStatementsSummary(trimToNull(json.path("chinaRelatedStatementsSummary").asText(null)))
                .setChinaRelatedStatementsSummaryParagraphs(readParagraphSummaryItems(json.path("chinaRelatedStatementsSummaryParagraphs")))
                .setPoliticalTendencySummary(trimToNull(json.path("politicalTendencySummary").asText(null)))
                .setPoliticalTendencySummaryParagraphs(readParagraphSummaryItems(json.path("politicalTendencySummaryParagraphs")))
                .setContactInformationSummary(trimToNull(json.path("contactInformationSummary").asText(null)))
                .setContactInformationSummaryParagraphs(readParagraphSummaryItems(json.path("contactInformationSummaryParagraphs")))
                .setFamilyMemberSituationSummary(trimToNull(json.path("familyMemberSituationSummary").asText(null)))
                .setFamilyMemberSituationSummaryParagraphs(readParagraphSummaryItems(json.path("familyMemberSituationSummaryParagraphs")))
                .setMisconductSummary(trimToNull(json.path("misconductSummary").asText(null)))
                .setMisconductSummaryParagraphs(readParagraphSummaryItems(json.path("misconductSummaryParagraphs")))
                .setKeyFacts(readStringList(json.path("keyFacts")))
                .setTags(readStringList(json.path("tags")))
                .setArticleSources(readArticleCitations(json.path("articleSources")))
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
            if ((profile.getSummaryParagraphs() == null || profile.getSummaryParagraphs().isEmpty()) && draftProfile.getSummaryParagraphs() != null) {
                profile.setSummaryParagraphs(draftProfile.getSummaryParagraphs());
            }
            if (!StringUtils.hasText(profile.getEducationSummary())) {
                profile.setEducationSummary(draftProfile.getEducationSummary());
            }
            if ((profile.getEducationSummaryParagraphs() == null || profile.getEducationSummaryParagraphs().isEmpty()) && draftProfile.getEducationSummaryParagraphs() != null) {
                profile.setEducationSummaryParagraphs(draftProfile.getEducationSummaryParagraphs());
            }
            if (!StringUtils.hasText(profile.getFamilyBackgroundSummary())) {
                profile.setFamilyBackgroundSummary(draftProfile.getFamilyBackgroundSummary());
            }
            if ((profile.getFamilyBackgroundSummaryParagraphs() == null || profile.getFamilyBackgroundSummaryParagraphs().isEmpty()) && draftProfile.getFamilyBackgroundSummaryParagraphs() != null) {
                profile.setFamilyBackgroundSummaryParagraphs(draftProfile.getFamilyBackgroundSummaryParagraphs());
            }
            if (!StringUtils.hasText(profile.getCareerSummary())) {
                profile.setCareerSummary(draftProfile.getCareerSummary());
            }
            if ((profile.getCareerSummaryParagraphs() == null || profile.getCareerSummaryParagraphs().isEmpty()) && draftProfile.getCareerSummaryParagraphs() != null) {
                profile.setCareerSummaryParagraphs(draftProfile.getCareerSummaryParagraphs());
            }
            if (!StringUtils.hasText(profile.getChinaRelatedStatementsSummary())) {
                profile.setChinaRelatedStatementsSummary(draftProfile.getChinaRelatedStatementsSummary());
            }
            if ((profile.getChinaRelatedStatementsSummaryParagraphs() == null || profile.getChinaRelatedStatementsSummaryParagraphs().isEmpty()) && draftProfile.getChinaRelatedStatementsSummaryParagraphs() != null) {
                profile.setChinaRelatedStatementsSummaryParagraphs(draftProfile.getChinaRelatedStatementsSummaryParagraphs());
            }
            if (!StringUtils.hasText(profile.getPoliticalTendencySummary())) {
                profile.setPoliticalTendencySummary(draftProfile.getPoliticalTendencySummary());
            }
            if ((profile.getPoliticalTendencySummaryParagraphs() == null || profile.getPoliticalTendencySummaryParagraphs().isEmpty()) && draftProfile.getPoliticalTendencySummaryParagraphs() != null) {
                profile.setPoliticalTendencySummaryParagraphs(draftProfile.getPoliticalTendencySummaryParagraphs());
            }
            if (!StringUtils.hasText(profile.getContactInformationSummary())) {
                profile.setContactInformationSummary(draftProfile.getContactInformationSummary());
            }
            if ((profile.getContactInformationSummaryParagraphs() == null || profile.getContactInformationSummaryParagraphs().isEmpty()) && draftProfile.getContactInformationSummaryParagraphs() != null) {
                profile.setContactInformationSummaryParagraphs(draftProfile.getContactInformationSummaryParagraphs());
            }
            if (!StringUtils.hasText(profile.getFamilyMemberSituationSummary())) {
                profile.setFamilyMemberSituationSummary(draftProfile.getFamilyMemberSituationSummary());
            }
            if ((profile.getFamilyMemberSituationSummaryParagraphs() == null || profile.getFamilyMemberSituationSummaryParagraphs().isEmpty()) && draftProfile.getFamilyMemberSituationSummaryParagraphs() != null) {
                profile.setFamilyMemberSituationSummaryParagraphs(draftProfile.getFamilyMemberSituationSummaryParagraphs());
            }
            if (!StringUtils.hasText(profile.getMisconductSummary())) {
                profile.setMisconductSummary(draftProfile.getMisconductSummary());
            }
            if ((profile.getMisconductSummaryParagraphs() == null || profile.getMisconductSummaryParagraphs().isEmpty()) && draftProfile.getMisconductSummaryParagraphs() != null) {
                profile.setMisconductSummaryParagraphs(draftProfile.getMisconductSummaryParagraphs());
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

    SearchLanguageInferenceResult parseSearchLanguageInferenceResult(String content) {
        try {
            JsonNode json = objectMapper.readTree(content);
            Map<String, String> localizedNames = new LinkedHashMap<>();
            JsonNode namesNode = json.path("localizedNames");
            if (namesNode.isObject()) {
                namesNode.fields().forEachRemaining(entry -> {
                    String value = trimToNull(entry.getValue().asText(null));
                    if (StringUtils.hasText(entry.getKey()) && StringUtils.hasText(value)) {
                        localizedNames.put(entry.getKey().trim(), value);
                    }
                });
            }
            return new SearchLanguageInferenceResult()
                    .setPrimaryNationality(trimToNull(json.path("primaryNationality").asText(null)))
                    .setRecommendedLanguages(readStringList(json.path("recommendedLanguages")))
                    .setLocalizedNames(localizedNames)
                    .setReason(trimToNull(json.path("reason").asText(null)))
                    .setConfidence(json.path("confidence").isNumber() ? json.path("confidence").asDouble() : null);
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("INVALID_RESPONSE: DeepSeek 搜索语言推断不是合法 JSON", ex);
        }
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
            case "summaryParagraphs" -> profile.setSummaryParagraphs(readParagraphSummaryItems(valueNode));
            case "educationSummaryParagraphs" -> profile.setEducationSummaryParagraphs(readParagraphSummaryItems(valueNode));
            case "familyBackgroundSummaryParagraphs" -> profile.setFamilyBackgroundSummaryParagraphs(readParagraphSummaryItems(valueNode));
            case "careerSummaryParagraphs" -> profile.setCareerSummaryParagraphs(readParagraphSummaryItems(valueNode));
            case "chinaRelatedStatementsSummaryParagraphs" -> profile.setChinaRelatedStatementsSummaryParagraphs(readParagraphSummaryItems(valueNode));
            case "politicalTendencySummaryParagraphs" -> profile.setPoliticalTendencySummaryParagraphs(readParagraphSummaryItems(valueNode));
            case "contactInformationSummaryParagraphs" -> profile.setContactInformationSummaryParagraphs(readParagraphSummaryItems(valueNode));
            case "familyMemberSituationSummaryParagraphs" -> profile.setFamilyMemberSituationSummaryParagraphs(readParagraphSummaryItems(valueNode));
            case "misconductSummaryParagraphs" -> profile.setMisconductSummaryParagraphs(readParagraphSummaryItems(valueNode));
            case "keyFacts" -> profile.setKeyFacts(readStringList(valueNode));
            case "tags" -> profile.setTags(readStringList(valueNode));
            case "articleSources" -> profile.setArticleSources(readArticleCitations(valueNode));
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
                    .setSummary(trimToNull(item.path("summary").asText(null)))
                    .setSourceIds(readIntegerList(item.path("sourceIds")))
                    .setSourceUrls(readStringList(item.path("sourceUrls"))));
        }
        return List.copyOf(items);
    }

    private List<ParagraphSummaryItem> readParagraphSummaryItems(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<ParagraphSummaryItem> items = new java.util.ArrayList<>();
        for (JsonNode item : arrayNode) {
            items.add(new ParagraphSummaryItem()
                    .setText(trimToNull(item.path("text").asText(null)))
                    .setSourceIds(readIntegerList(item.path("sourceIds")))
                    .setSourceUrls(readStringList(item.path("sourceUrls")))
                    // 关键逻辑：兼容模型直接返回完整 sources 对象，避免前端参考脚标因只解析 sourceUrls 而丢失。
                    .setSources(readParagraphSources(item.path("sources"))));
        }
        return List.copyOf(items);
    }

    private List<ParagraphSource> readParagraphSources(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<ParagraphSource> items = new java.util.ArrayList<>();
        for (JsonNode item : arrayNode) {
            items.add(new ParagraphSource()
                    .setTitle(trimToNull(item.path("title").asText(null)))
                    .setUrl(trimToNull(item.path("url").asText(null)))
                    .setSource(trimToNull(item.path("source").asText(null)))
                    .setPublishedAt(firstNonBlank(
                            trimToNull(item.path("publishedAt").asText(null)),
                            trimToNull(item.path("published_at").asText(null))
                    )));
        }
        return List.copyOf(items);
    }

    private Map<String, Object> paragraphArraySchema() {
        return Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "text", Map.of("type", "string"),
                                "sourceIds", Map.of("type", "array", "items", Map.of("type", "integer")),
                                "sourceUrls", Map.of("type", "array", "items", Map.of("type", "string")),
                                "sources", Map.of(
                                        "type", "array",
                                        "items", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "title", Map.of("type", "string"),
                                                        "url", Map.of("type", "string"),
                                                        "source", Map.of("type", "string"),
                                                        "publishedAt", Map.of("type", "string")
                                                ),
                                                "additionalProperties", false
                                        )
                                )
                        ),
                        "required", List.of("text", "sourceIds"),
                        "additionalProperties", false
                )
        );
    }

    private Map<String, Object> articleCitationArraySchema() {
        return Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "id", Map.of("type", "integer"),
                                "title", Map.of("type", "string"),
                                "url", Map.of("type", "string"),
                                "source", Map.of("type", "string"),
                                "publishedAt", Map.of("type", "string")
                        ),
                        "required", List.of("id", "url"),
                        "additionalProperties", false
                )
        );
    }

    private List<Integer> readIntegerList(JsonNode arrayNode) {
        Set<Integer> values = new LinkedHashSet<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                if (item != null && item.isInt()) {
                    values.add(item.asInt());
                }
            }
        }
        return List.copyOf(values);
    }

    private List<ArticleCitation> readArticleCitations(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<ArticleCitation> items = new java.util.ArrayList<>();
        for (JsonNode item : arrayNode) {
            items.add(new ArticleCitation()
                    .setId(item.path("id").isInt() ? item.path("id").asInt() : null)
                    .setTitle(trimToNull(item.path("title").asText(null)))
                    .setUrl(trimToNull(item.path("url").asText(null)))
                    .setSource(trimToNull(item.path("source").asText(null)))
                    .setPublishedAt(firstNonBlank(
                            trimToNull(item.path("publishedAt").asText(null)),
                            trimToNull(item.path("published_at").asText(null))
                    )));
        }
        return List.copyOf(items);
    }

    private String buildArticleCitationContext(List<PageSummary> pageSummaries) {
        if (pageSummaries == null || pageSummaries.isEmpty()) {
            return "无";
        }
        return pageSummaries.stream()
                .map(summary -> "文章[" + firstNonBlank(summary.getSourceId() == null ? null : String.valueOf(summary.getSourceId()), "?")
                        + "] title=" + firstNonBlank(summary.getTitle(), "")
                        + " url=" + firstNonBlank(summary.getSourceUrl(), ""))
                .collect(Collectors.joining("\n"));
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
            if (isDegeneratedRepeatedContent(content)) {
                throw new ApiCallException("DEGRADED_RESPONSE: " + scene + "返回疑似退化文本，content=" + previewContent(content));
            }
            throw new ApiCallException("INVALID_RESPONSE: " + scene + "未返回 JSON，content=" + previewContent(content));
        }
        return objectMapper.readTree(candidate);
    }

    private boolean isDegeneratedRepeatedContent(String content) {
        String normalized = trimToNull(content);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        String compact = normalized.replaceAll("\\s+", "");
        if (compact.length() < MAX_REPEATED_UNIT_LENGTH * MIN_REPEATED_UNIT_OCCURRENCES) {
            return false;
        }
        if (compact.indexOf('\uFFFD') >= 0) {
            return true;
        }
        for (int unitLength = 1; unitLength <= Math.min(MAX_REPEATED_UNIT_LENGTH, compact.length() / MIN_REPEATED_UNIT_OCCURRENCES); unitLength++) {
            String unit = compact.substring(0, unitLength);
            if (!StringUtils.hasText(unit)) {
                continue;
            }
            int matchedLength = countLeadingRepeatedLength(compact, unit);
            int occurrences = matchedLength / unitLength;
            if (occurrences >= MIN_REPEATED_UNIT_OCCURRENCES
                    && matchedLength >= (int) Math.floor(compact.length() * MIN_REPEATED_UNIT_COVERAGE)) {
                return true;
            }
        }
        return false;
    }

    private int countLeadingRepeatedLength(String content, String unit) {
        int offset = 0;
        while (offset + unit.length() <= content.length()
                && content.regionMatches(offset, unit, 0, unit.length())) {
            offset += unit.length();
        }
        return offset;
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
                只能基于这里提供的篇级摘要归纳，不能要求补充完整正文，不能输出解释性文本。
                如果某个分段证据不足，也必须继续返回 JSON，并把该分段 summary 写成“未见可靠公开信息”或“公开资料有限”。
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

    private String extractPlainTextPageSummary(String content) {
        String normalized = normalizeJsonContent(content);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.startsWith("{") || normalized.startsWith("[") || normalized.contains("<invoke")) {
            return null;
        }
        String cleaned = stripProtocolArtifacts(normalized);
        if (!StringUtils.hasText(cleaned) || cleaned.contains("｜DSML｜")) {
            return null;
        }
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.length() < 8) {
            return null;
        }
        if (PAGE_SUMMARY_REFUSAL_PATTERN.matcher(cleaned).find()) {
            return null;
        }
        if (!MEANINGFUL_TEXT_PATTERN.matcher(cleaned).find()) {
            return null;
        }
        return cleaned;
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
