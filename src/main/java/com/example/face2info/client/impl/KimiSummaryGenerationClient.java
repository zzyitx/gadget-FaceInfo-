package com.example.face2info.client.impl;

import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.KimiApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ParagraphSummaryItem;
import com.example.face2info.entity.internal.PersonBasicInfo;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SectionSummaryItem;
import com.example.face2info.entity.internal.SectionedSummary;
import com.example.face2info.entity.internal.TopicExpansionDecision;
import com.example.face2info.entity.internal.TopicExpansionQuery;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.InMemoryMultipartFile;
import com.example.face2info.util.LogSanitizer;
import com.example.face2info.util.RetryUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "face2info.api.summary", name = "provider", havingValue = "kimi")
public class KimiSummaryGenerationClient implements SummaryGenerationClient {

    private static final String PAGE_SUMMARY_FUNCTION_NAME = "submit_page_summary";
    private static final String PERSON_PROFILE_FUNCTION_NAME = "submit_person_profile";
    private static final String SECTION_SUMMARY_FUNCTION_NAME = "submit_section_summary";
    private static final String TOPIC_EXPANSION_FUNCTION_NAME = "submit_topic_expansion";
    private static final String SECTIONED_FAMILY_SUMMARY_FUNCTION_NAME = "submit_sectioned_family_summary";
    private static final String FACE_ENHANCE_FUNCTION_NAME = "submit_enhanced_face_image";
    private static final String PROFILE_JUDGEMENT_FUNCTION_NAME = "submit_profile_judgement";
    private static final Pattern SERIALIZED_PARAMETER_PATTERN = Pattern.compile(
            "<[^>]*parameter\\s+name\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</[^>]*parameter>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final RestTemplate restTemplate;
    private final ApiProperties properties;
    private final ObjectMapper objectMapper;

    public KimiSummaryGenerationClient(@Qualifier("kimiRestTemplate") RestTemplate restTemplate,
                                       ApiProperties properties,
                                       ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageSummary summarizePage(String fallbackName, PageContent page) {
        KimiApiProperties kimi = properties.getApi().getKimi();
        validateConfig(kimi);
        log.info("Kimi 页面摘要开始 fallbackName={} url={} title={}",
                fallbackName,
                page == null ? null : page.getUrl(),
                page == null ? null : page.getTitle());

        return RetryUtils.execute("Kimi 页面摘要", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
            JsonNode body = callKimi(kimi, buildPageRequest(kimi, fallbackName, page));
            PageSummary summary = parsePageSummary(page, body);
            log.info("Kimi 页面摘要成功 url={} summaryLength={} tagCount={} factCount={}",
                    summary.getSourceUrl(),
                    summary.getSummary() == null ? 0 : summary.getSummary().length(),
                    summary.getTags() == null ? 0 : summary.getTags().size(),
                    summary.getKeyFacts() == null ? 0 : summary.getKeyFacts().size());
            return summary;
        });
    }


    @Override
    public MultipartFile enhanceFaceImageByUrl(String imageUrl, String filename, String contentType) {
        KimiApiProperties kimi = properties.getApi().getKimi();
        validateConfig(kimi);

        log.info("Kimi 人脸增强开始 imageUrl={} fileName={} contentType={}", imageUrl, filename, contentType);

        return RetryUtils.execute("Kimi 人脸增强", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
            JsonNode body = callKimi(kimi, buildFaceEnhanceRequestByUrl(kimi, imageUrl, filename, contentType));
            MultipartFile enhanced = parseEnhancedImage(filename, contentType, body);

            log.info("Kimi 人脸增强成功 originalName={} enhancedName={} enhancedSize={}",
                    filename,
                    enhanced.getOriginalFilename(),
                    enhanced.getSize());

            return enhanced;
        });
    }

    @Override
    public ResolvedPersonProfile summarizePersonFromPageSummaries(String fallbackName, List<PageSummary> pageSummaries) {
        KimiApiProperties kimi = properties.getApi().getKimi();
        validateConfig(kimi);
        int pageSummaryCount = pageSummaries == null ? 0 : pageSummaries.size();
        log.info("Kimi 最终画像摘要开始 fallbackName={} pageSummaryCount={}", fallbackName, pageSummaryCount);

        return RetryUtils.execute("Kimi 人物总结", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
            JsonNode body = callKimi(kimi, buildPersonRequest(kimi, fallbackName, pageSummaries));
            ResolvedPersonProfile profile = parseProfileFromPageSummaries(fallbackName, pageSummaries, body);
            log.info("Kimi 最终画像摘要成功 resolvedName={} summaryLength={} tagCount={} evidenceUrlCount={}",
                    profile.getResolvedName(),
                    profile.getSummary() == null ? 0 : profile.getSummary().length(),
                    profile.getTags() == null ? 0 : profile.getTags().size(),
                    profile.getEvidenceUrls() == null ? 0 : profile.getEvidenceUrls().size());
            return profile;
        });
    }

    @Override
    public String summarizeSectionFromPageSummaries(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        KimiApiProperties kimi = properties.getApi().getKimi();
        validateConfig(kimi);
        int pageSummaryCount = pageSummaries == null ? 0 : pageSummaries.size();
        log.info("Kimi 主题摘要开始 resolvedName={} sectionType={} pageSummaryCount={}",
                resolvedName, sectionType, pageSummaryCount);

        return RetryUtils.execute("Kimi 主题摘要", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
            JsonNode body = callKimi(kimi, buildSectionRequest(kimi, resolvedName, sectionType, pageSummaries));
            String summary = parseSectionSummary(body);
            log.info("Kimi 主题摘要成功 resolvedName={} sectionType={} summaryLength={}",
                    resolvedName, sectionType, summary == null ? 0 : summary.length());
            return summary;
        });
    }

    @Override
    public TopicExpansionDecision expandTopicQueriesFromPageSummaries(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        KimiApiProperties kimi = properties.getApi().getKimi();
        validateConfig(kimi);
        return RetryUtils.execute("Kimi 主题扩展推断", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
            JsonNode body = callKimi(kimi, buildTopicExpansionRequest(kimi, resolvedName, sectionType, pageSummaries));
            return parseTopicExpansionDecision(body);
        });
    }

    @Override
    public SectionedSummary summarizeSectionedSectionFromPageSummaries(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        KimiApiProperties kimi = properties.getApi().getKimi();
        validateConfig(kimi);
        return RetryUtils.execute("Kimi 主题分段摘要", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
            JsonNode body = callKimi(kimi, buildSectionedTopicRequest(kimi, resolvedName, sectionType, pageSummaries));
            return parseSectionedSummary(body);
        });
    }

    @Override
    public ResolvedPersonProfile applyComprehensiveJudgement(String fallbackName,
                                                             List<PageSummary> pageSummaries,
                                                             ResolvedPersonProfile draftProfile) {
        KimiApiProperties kimi = properties.getApi().getKimi();
        validateConfig(kimi);
        log.info("Kimi 综合判断开始 fallbackName={} pageSummaryCount={}",
                fallbackName,
                pageSummaries == null ? 0 : pageSummaries.size());

        return RetryUtils.execute("Kimi 综合判断", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
            JsonNode body = callKimi(kimi, buildJudgementRequest(kimi, fallbackName, pageSummaries, draftProfile));
            ResolvedPersonProfile judged = parseJudgedProfile(fallbackName, pageSummaries, draftProfile, body);
            log.info("Kimi 综合判断成功 resolvedName={} summaryLength={}",
                    judged.getResolvedName(),
                    judged.getSummary() == null ? 0 : judged.getSummary().length());
            return judged;
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
            throw new ApiCallException("CONFIG_MISSING: Kimi 配置不完整");
        }
    }

    private JsonNode callKimi(KimiApiProperties kimi, Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(kimi.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        log.info("Kimi 请求已发送 model={} url={}", kimi.getModel(), LogSanitizer.maskUrl(kimi.getBaseUrl()));
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


    /**
     * 构建请求（已改为返回 imageUrl，不再使用 base64）
     */
    private Map<String, Object> buildFaceEnhanceRequestByUrl(KimiApiProperties kimi,
                                                             String imageUrl,
                                                             String filename,
                                                             String contentType) {

        return Map.of(
                "model", kimi.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", kimi.getSystemPrompt()),
                        Map.of("role", "user", "content",
                                buildFaceEnhancePromptByUrl(imageUrl, filename, contentType))
                ),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", FACE_ENHANCE_FUNCTION_NAME,
                                "description", "提交增强人脸图像",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "imageUrl", Map.of("type", "string"),
                                                "filename", Map.of("type", "string"),
                                                "contentType", Map.of("type", "string")
                                        ),
                                        "required", List.of("imageUrl"),
                                        "additionalProperties", false
                                )
                        )
                )),
                "tool_choice", Map.of(
                        "type", "function",
                        "function", Map.of("name", FACE_ENHANCE_FUNCTION_NAME)
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
                                "parameters", profileSchema()
                        )
                )),
                "tool_choice", Map.of(
                        "type", "function",
                        "function", Map.of("name", PERSON_PROFILE_FUNCTION_NAME)
                )
        );
    }

    private Map<String, Object> buildJudgementRequest(KimiApiProperties kimi,
                                                      String fallbackName,
                                                      List<PageSummary> pageSummaries,
                                                      ResolvedPersonProfile draftProfile) {
        return Map.of(
                "model", kimi.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", kimi.getSystemPrompt()),
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
                "tool_choice", Map.of(
                        "type", "function",
                        "function", Map.of("name", PROFILE_JUDGEMENT_FUNCTION_NAME)
                )
        );
    }

    private Map<String, Object> buildSectionRequest(KimiApiProperties kimi,
                                                    String resolvedName,
                                                    String sectionType,
                                                    List<PageSummary> pageSummaries) {
        return Map.of(
                "model", kimi.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", kimi.getSystemPrompt()),
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
                                                "summary", Map.of("type", "string")
                                        ),
                                        "required", List.of("summary"),
                                        "additionalProperties", false
                                )
                        )
                )),
                "tool_choice", Map.of(
                        "type", "function",
                        "function", Map.of("name", SECTION_SUMMARY_FUNCTION_NAME)
                )
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
        // 关键逻辑：单页正文先截断再摘要，避免超长正文把页面摘要请求直接打满上下文。
        return """
                正文过长，以下内容已按长度截断（原始长度=%d，保留前 %d 字符）：
                %s
                """.formatted(page.getContent().length(), maxLength, page.getContent().substring(0, maxLength));
    }


    /**
     * Prompt：要求返回图片URL（关键改动）
     */
    private String buildFaceEnhancePromptByUrl(String imageUrl, String filename, String contentType) {
        return """
                请对输入图片进行人脸增强（提高清晰度、去噪、细节优化），要求必须保持人物特征不变。
                
                要求：
                1. 输出增强后的图片，并提供可访问的图片URL
                2. 不要返回 base64
                3. 返回 JSON 格式如下：
                
                {
                  "imageUrl": "增强后的图片访问地址",
                  "filename": "%s",
                  "contentType": "%s"
                }
                
                原始图片URL: %s
                """.formatted(filename, contentType, imageUrl);
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
                JSON 字段固定为 resolvedName、description、summary、summaryParagraphs、educationSummary、educationSummaryParagraphs、familyBackgroundSummary、familyBackgroundSummaryParagraphs、careerSummary、careerSummaryParagraphs、chinaRelatedStatementsSummary、chinaRelatedStatementsSummaryParagraphs、politicalTendencySummary、politicalTendencySummaryParagraphs、contactInformationSummary、contactInformationSummaryParagraphs、familyMemberSituationSummary、familyMemberSituationSummaryParagraphs、misconductSummary、misconductSummaryParagraphs、keyFacts、tags、wikipedia、officialWebsite、basicInfo、evidenceUrls。
                summary 只写人物主体信息与关键细节，必须详细、清晰，不要简短结论，也不要重复 educationSummary、familyBackgroundSummary、careerSummary、chinaRelatedStatementsSummary、politicalTendencySummary、contactInformationSummary、familyMemberSituationSummary、misconductSummary 的内容。
                所有 *Paragraphs 字段必须返回数组；数组元素字段固定为 text、sourceUrls。sourceUrls 只能填写上方篇级摘要中已出现的 sourceUrl。
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
                请基于页面摘要集合和最终总结草稿进行一次综合判断，输出更稳健的人物最终画像。
                必须满足以下约束：
                1. 只能通过函数 submit_profile_judgement 返回结果，禁止输出解释、道歉、思考过程、Markdown 代码块或任何额外文本。
                2. 返回内容语言必须为中文。
                3. 即使结论不确定，也必须按既定字段返回 JSON；不允许自然语言拒答。
                JSON 字段固定为 resolvedName、description、summary、summaryParagraphs、educationSummary、educationSummaryParagraphs、familyBackgroundSummary、familyBackgroundSummaryParagraphs、careerSummary、careerSummaryParagraphs、chinaRelatedStatementsSummary、chinaRelatedStatementsSummaryParagraphs、politicalTendencySummary、politicalTendencySummaryParagraphs、contactInformationSummary、contactInformationSummaryParagraphs、familyMemberSituationSummary、familyMemberSituationSummaryParagraphs、misconductSummary、misconductSummaryParagraphs、keyFacts、tags、wikipedia、officialWebsite、basicInfo、evidenceUrls。
                summary 只写人物主体信息与关键细节，必须详细、清晰，不要简短结论，也不要重复 educationSummary、familyBackgroundSummary、careerSummary、chinaRelatedStatementsSummary、politicalTendencySummary、contactInformationSummary、familyMemberSituationSummary、misconductSummary 的内容。
                所有 *Paragraphs 字段必须返回数组；数组元素字段固定为 text、sourceUrls。sourceUrls 只能填写上方篇级摘要中已出现的 sourceUrl。
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

    private String buildSectionPrompt(String resolvedName,
                                      String sectionType,
                                      List<PageSummary> pageSummaries) {
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

    /**
     * 解析 Kimi 返回并下载高清化图片字节。
     */
    private MultipartFile parseEnhancedImage(String originalFilename,
                                             String originalContentType,
                                             JsonNode body) {

        String content = extractStructuredPayload(body, FACE_ENHANCE_FUNCTION_NAME);

        try {
            JsonNode json = readStructuredJson(content, "Kimi 人脸增强");

            String imageUrl = trimToNull(json.path("imageUrl").asText(null));
            if (!StringUtils.hasText(imageUrl)) {
                throw new ApiCallException("EMPTY_RESPONSE: Kimi未返回图片URL");
            }

            String filename = firstNonBlank(
                    trimToNull(json.path("filename").asText(null)),
                    originalFilename,
                    "enhanced-face.jpg"
            );

            String contentType = firstNonBlank(
                    trimToNull(json.path("contentType").asText(null)),
                    originalContentType,
                    "image/jpeg"
            );

            // 下载图片
            byte[] bytes = downloadImage(imageUrl);

            // 校验（防止脏数据）
            if (bytes.length < 10 * 1024) {
                throw new ApiCallException("INVALID_IMAGE: 图片过小，疑似异常");
            }

            return new InMemoryMultipartFile(filename, contentType, bytes);

        } catch (Exception ex) {
            log.warn("Kimi 人脸增强结果解析失败 error={}", ex.getMessage(), ex);
            throw new ApiCallException("INVALID_RESPONSE: 图片处理失败", ex);
        }
    }

    private PageSummary parsePageSummary(PageContent page, JsonNode body) {
        String content = extractStructuredPayload(body, PAGE_SUMMARY_FUNCTION_NAME);
        try {
            JsonNode json = readStructuredJson(content, "Kimi 页面摘要");
            String summary = json.path("summary").asText(null);
            if (!StringUtils.hasText(summary)) {
                throw new ApiCallException("EMPTY_RESPONSE: Kimi 页面摘要为空");
            }

            return new PageSummary()
                    .setSourceUrl(firstNonBlank(trimToNull(json.path("sourceUrl").asText(null)), page == null ? null : page.getUrl()))
                    .setTitle(firstNonBlank(trimToNull(json.path("title").asText(null)), page == null ? null : page.getTitle()))
                    .setSummary(summary.trim())
                    .setKeyFacts(readStringList(json.path("keyFacts")))
                    .setTags(readStringList(json.path("tags")));
        } catch (JsonProcessingException ex) {
            log.warn("Kimi 页面摘要解析失败 error={}", ex.getMessage(), ex);
            throw new ApiCallException("INVALID_RESPONSE: Kimi 页面摘要不是合法 JSON", ex);
        }
    }

    private ResolvedPersonProfile parseProfileFromPageSummaries(String fallbackName,
                                                                List<PageSummary> pageSummaries,
                                                                JsonNode body) {
        String content = extractStructuredPayload(body, PERSON_PROFILE_FUNCTION_NAME);
        try {
            JsonNode json = readStructuredJson(content, "Kimi 最终画像");
            return toProfile(json, fallbackName, pageSummaries == null ? List.of() : pageSummaries, null);
        } catch (JsonProcessingException ex) {
            log.warn("Kimi 最终画像解析失败 fallbackName={} error={}", fallbackName, ex.getMessage(), ex);
            throw new ApiCallException("INVALID_RESPONSE: Kimi 返回内容不是合法 JSON", ex);
        }
    }

    private String parseSectionSummary(JsonNode body) {
        String content = extractStructuredPayload(body, SECTION_SUMMARY_FUNCTION_NAME);
        try {
            JsonNode json = readStructuredJson(content, "Kimi 主题摘要");
            return trimToNull(json.path("summary").asText(null));
        } catch (JsonProcessingException ex) {
            log.warn("Kimi 主题摘要解析失败 error={}", ex.getMessage(), ex);
            throw new ApiCallException("INVALID_RESPONSE: Kimi 主题摘要不是合法 JSON", ex);
        }
    }

    private TopicExpansionDecision parseTopicExpansionDecision(JsonNode body) {
        String content = extractStructuredPayload(body, TOPIC_EXPANSION_FUNCTION_NAME);
        try {
            JsonNode json = readStructuredJson(content, "Kimi 主题扩展推断");
            List<TopicExpansionQuery> expansionQueries = List.of();
            JsonNode queryNodes = json.path("expansionQueries");
            if (queryNodes.isArray()) {
                expansionQueries = readExpansionQueries(queryNodes);
            }
            return new TopicExpansionDecision()
                    .setShouldExpand(json.path("shouldExpand").asBoolean(false))
                    .setExpansionQueries(expansionQueries);
        } catch (JsonProcessingException ex) {
            log.warn("Kimi 主题扩展推断解析失败 error={}", ex.getMessage(), ex);
            throw new ApiCallException("INVALID_RESPONSE: Kimi 主题扩展推断不是合法 JSON", ex);
        }
    }

    private SectionedSummary parseSectionedSummary(JsonNode body) {
        String content = extractStructuredPayload(body, SECTIONED_FAMILY_SUMMARY_FUNCTION_NAME);
        try {
            JsonNode json = readStructuredJson(content, "Kimi 家族成员分段摘要");
            return new SectionedSummary().setSections(readSectionSummaryItems(json.path("sections")));
        } catch (JsonProcessingException ex) {
            log.warn("Kimi 家族成员分段摘要解析失败 error={}", ex.getMessage(), ex);
            throw new ApiCallException("INVALID_RESPONSE: Kimi 家族成员分段摘要不是合法 JSON", ex);
        }
    }

    private ResolvedPersonProfile parseJudgedProfile(String fallbackName,
                                                     List<PageSummary> pageSummaries,
                                                     ResolvedPersonProfile draftProfile,
                                                     JsonNode body) {
        String content = extractStructuredPayload(body, PROFILE_JUDGEMENT_FUNCTION_NAME);
        try {
            JsonNode json = readStructuredJson(content, "Kimi 综合判断");
            return toProfile(json, fallbackName, pageSummaries == null ? List.of() : pageSummaries, draftProfile);
        } catch (JsonProcessingException ex) {
            log.warn("Kimi 综合判断结果解析失败 fallbackName={} error={}", fallbackName, ex.getMessage(), ex);
            throw new ApiCallException("INVALID_RESPONSE: Kimi 综合判断内容不是合法 JSON", ex);
        }
    }

    /**
     * 下载图片
     */
    private byte[] downloadImage(String url) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("下载图片失败");
            }

            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new RuntimeException("图片为空");
            }

            return body;

        } catch (Exception e) {
            throw new RuntimeException("图片下载失败: " + url, e);
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
                .setWikipedia(trimToNull(json.path("wikipedia").asText(null)))
                .setOfficialWebsite(trimToNull(json.path("officialWebsite").asText(null)))
                .setBasicInfo(readBasicInfo(json.path("basicInfo")))
                .setEvidenceUrls(evidenceUrls);

        // 某些模型会把后续 parameter 片段串进 summary 字段里，这里优先把被污染的结构化字段回收出来，
        // 保证最终响应保持稳定，避免前端把协议碎片直接渲染出来。
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
            if (!StringUtils.hasText(profile.getWikipedia())) {
                profile.setWikipedia(draftProfile.getWikipedia());
            }
            if (!StringUtils.hasText(profile.getOfficialWebsite())) {
                profile.setOfficialWebsite(draftProfile.getOfficialWebsite());
            }
            if ((profile.getBasicInfo() == null || isBasicInfoEmpty(profile.getBasicInfo())) && draftProfile.getBasicInfo() != null) {
                profile.setBasicInfo(draftProfile.getBasicInfo());
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
            case "wikipedia" -> profile.setWikipedia(trimToNull(valueNode.asText(null)));
            case "officialWebsite" -> profile.setOfficialWebsite(trimToNull(valueNode.asText(null)));
            case "basicInfo" -> profile.setBasicInfo(readBasicInfo(valueNode));
            default -> {
            }
        }
    }

    private boolean isBasicInfoEmpty(PersonBasicInfo basicInfo) {
        if (basicInfo == null) {
            return true;
        }
        return !StringUtils.hasText(basicInfo.getBirthDate())
                && (basicInfo.getEducation() == null || basicInfo.getEducation().isEmpty())
                && (basicInfo.getOccupations() == null || basicInfo.getOccupations().isEmpty())
                && (basicInfo.getBiographies() == null || basicInfo.getBiographies().isEmpty());
    }

    private String extractContent(JsonNode body) {
        String content = body == null ? null : body.path("choices").path(0).path("message").path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new ApiCallException("EMPTY_RESPONSE: Kimi 返回内容为空");
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
                    .setSourceUrls(readStringList(item.path("sourceUrls"))));
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
                                "sourceUrls", Map.of("type", "array", "items", Map.of("type", "string"))
                        ),
                        "required", List.of("text", "sourceUrls"),
                        "additionalProperties", false
                )
        );
    }

    // 这里强制模型返回 term/section/reason 三元组，服务层才能把扩展理由精确挂到对应小标题下。
    private Map<String, Object> buildTopicExpansionRequest(KimiApiProperties kimi,
                                                           String resolvedName,
                                                           String sectionType,
                                                           List<PageSummary> pageSummaries) {
        return Map.of(
                "model", kimi.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", kimi.getSystemPrompt()),
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
    private Map<String, Object> buildSectionedTopicRequest(KimiApiProperties kimi,
                                                           String resolvedName,
                                                           String sectionType,
                                                           List<PageSummary> pageSummaries) {
        return Map.of(
                "model", kimi.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", kimi.getSystemPrompt()),
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

    private String stripDataUrlPrefix(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        int index = value.indexOf(",");
        if (value.startsWith("data:") && index > 0) {
            return value.substring(index + 1);
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
        // 部分模型会在 JSON 前后包解释文本，这里优先从正文里截取首个平衡的 JSON 片段。
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
                    // 只记录最外层起点，避免把说明文字里的局部括号当成完整 JSON。
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

    private String previewContent(String content) {
        String normalized = trimToNull(content);
        if (normalized == null) {
            return "<empty>";
        }
        String singleLine = normalized.replaceAll("\\s+", " ");
        return singleLine.length() <= 120 ? singleLine : singleLine.substring(0, 120) + "...";
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
                log.debug("serialized parameter json parse failed value={} error={}", previewContent(value), ex.getMessage());
            }
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "null".equalsIgnoreCase(value)) {
            try {
                return objectMapper.readTree(value.toLowerCase());
            } catch (JsonProcessingException ex) {
                log.debug("serialized parameter literal parse failed value={} error={}", value, ex.getMessage());
            }
        }
        if (value.matches("-?\\d+(\\.\\d+)?")) {
            try {
                return objectMapper.readTree(value);
            } catch (JsonProcessingException ex) {
                log.debug("serialized parameter number parse failed value={} error={}", value, ex.getMessage());
            }
        }
        return objectMapper.getNodeFactory().textNode(value);
    }
}
