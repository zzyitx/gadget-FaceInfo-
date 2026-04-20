package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.client.impl.DeepSeekSummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.DerivedTopicRequest;
import com.example.face2info.entity.internal.DerivedTopicType;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ParagraphSource;
import com.example.face2info.entity.internal.ParagraphSummaryItem;
import com.example.face2info.entity.internal.PersonAggregate;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SectionSummaryItem;
import com.example.face2info.entity.internal.SectionedSummary;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.internal.TopicExpansionDecision;
import com.example.face2info.entity.internal.TopicExpansionQuery;
import com.example.face2info.entity.internal.TopicQueryDecision;
import com.example.face2info.entity.internal.WebEvidence;
import com.example.face2info.entity.response.SocialAccount;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.service.DerivedTopicQueryService;
import com.example.face2info.service.InformationAggregationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InformationAggregationServiceImpl implements InformationAggregationService {

    private static final String SUMMARY_WARNING = "正文智能处理暂时不可用";
    private static final String JUDGEMENT_WARNING = "综合判断暂时不可用";
    private static final String LLM_FAILURE_MESSAGE = "大模型提取人物信息失败";
    private static final String LLM_SUFFIX = " (由大模型总结)";
    private static final String SOCIAL_PLACEHOLDER_PLATFORM = "pending";
    private static final String SOCIAL_PLACEHOLDER_URL = "#";
    private static final String SOCIAL_PLACEHOLDER_USERNAME = "功能正在开发中";
    private static final String SECONDARY_SEARCH_WARNING = "secondary_profile_search_unavailable";
    private static final String SECONDARY_SEARCH_SOURCE = "serper_google_search";
    private static final String EDUCATION_SECTION = "education";
    private static final String FAMILY_SECTION = "family";
    private static final String CAREER_SECTION = "career";
    private static final String CHINA_RELATED_STATEMENTS_SECTION = "china_related_statements";
    private static final String POLITICAL_VIEW_SECTION = "political_view";
    private static final String CONTACT_INFORMATION_SECTION = "contact_information";
    private static final String FAMILY_MEMBER_SITUATION_SECTION = "family_member_situation";
    private static final String MISCONDUCT_SECTION = "misconduct";
    private static final String INFERENCE_REASON_PREFIX = "扩展检索依据：";
    private static final List<String> EXPANSION_SOURCE_SITE_TERMS = List.of(
            "wikipedia.org",
            "wikipedia",
            "wikidata.org",
            "wikidata",
            "维基百科",
            "百度百科",
            "baike.baidu.com",
            "baidu baike"
    );
    private static final Map<String, Map<String, String>> DERIVED_SECTION_TITLE_MAPS = Map.of(
            CHINA_RELATED_STATEMENTS_SECTION, linkedSectionTitles(
                    "涉华言论", "一、涉华言论",
                    "中国评价", "二、中国评价",
                    "国际关系", "三、国际关系",
                    "相关争议", "四、相关争议"
            ),
            POLITICAL_VIEW_SECTION, linkedSectionTitles(
                    "政治倾向", "一、政治倾向",
                    "党派与组织", "二、党派与组织",
                    "政治理念", "三、政治理念",
                    "政策立场", "四、政策立场"
            ),
            CONTACT_INFORMATION_SECTION, linkedSectionTitles(
                    "公开通讯", "一、公开通讯",
                    "办公电话", "二、办公电话",
                    "官方邮箱", "三、官方邮箱",
                    "认证社交账号", "四、认证社交账号",
                    "其他联系方式", "五、其他联系方式"
            ),
            FAMILY_MEMBER_SITUATION_SECTION, linkedSectionTitles(
                    "家庭成员", "一、家庭成员",
                    "亲属信息", "二、亲属信息",
                    "经商与投资", "三、经商与投资",
                    "争议与纠纷", "四、争议与纠纷"
            ),
            MISCONDUCT_SECTION, linkedSectionTitles(
                    "违法记录", "一、违法记录",
                    "行政处罚", "二、行政处罚",
                    "负面事件", "三、负面事件",
                    "失信信息", "四、失信信息"
            )
    );
    private static final Set<String> VIDEO_PLATFORM_HOSTS = Set.of(
            "youtube.com",
            "youtu.be",
            "bilibili.com",
            "b23.tv",
            "v.qq.com",
            "youku.com",
            "ixigua.com"
    );

    @SuppressWarnings("unused")
    private final GoogleSearchClient googleSearchClient;
    @SuppressWarnings("unused")
    private final SerpApiClient serpApiClient;
    private final JinaReaderClient jinaReaderClient;
    private final SummaryGenerationClient summaryGenerationClient;
    private final DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient;
    private final ThreadPoolTaskExecutor executor;
    private final ApiProperties properties;
    private final DerivedTopicQueryService derivedTopicQueryService;

    @Autowired
    public InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                             SerpApiClient serpApiClient,
                                             JinaReaderClient jinaReaderClient,
                                             SummaryGenerationClient summaryGenerationClient,
                                             @Nullable DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient,
                                             @Qualifier("face2InfoExecutor") ThreadPoolTaskExecutor executor,
                                             ApiProperties properties,
                                             DerivedTopicQueryService derivedTopicQueryService) {
        this.googleSearchClient = googleSearchClient;
        this.serpApiClient = serpApiClient;
        this.jinaReaderClient = jinaReaderClient;
        this.summaryGenerationClient = summaryGenerationClient;
        this.deepSeekSummaryGenerationClient = deepSeekSummaryGenerationClient;
        this.executor = executor;
        this.properties = properties;
        this.derivedTopicQueryService = derivedTopicQueryService;
    }

    InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      JinaReaderClient jinaReaderClient,
                                      SummaryGenerationClient summaryGenerationClient,
                                      @Nullable DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient,
                                      ThreadPoolTaskExecutor executor) {
        this(googleSearchClient, serpApiClient, jinaReaderClient, summaryGenerationClient,
                deepSeekSummaryGenerationClient, executor, new ApiProperties(), new PassThroughDerivedTopicQueryService());
    }

    InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      JinaReaderClient jinaReaderClient,
                                      SummaryGenerationClient summaryGenerationClient,
                                      @Nullable DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient,
                                      ThreadPoolTaskExecutor executor,
                                      ApiProperties properties) {
        this(googleSearchClient, serpApiClient, jinaReaderClient, summaryGenerationClient,
                deepSeekSummaryGenerationClient, executor, properties, new PassThroughDerivedTopicQueryService());
    }

    InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      JinaReaderClient jinaReaderClient,
                                      SummaryGenerationClient summaryGenerationClient,
                                      ThreadPoolTaskExecutor executor,
                                      ApiProperties properties) {
        this(googleSearchClient, serpApiClient, jinaReaderClient, summaryGenerationClient,
                null, executor, properties, new PassThroughDerivedTopicQueryService());
    }

    InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      JinaReaderClient jinaReaderClient,
                                      SummaryGenerationClient summaryGenerationClient,
                                      ThreadPoolTaskExecutor executor) {
        this(googleSearchClient, serpApiClient, jinaReaderClient, summaryGenerationClient,
                null, executor, new ApiProperties(), new PassThroughDerivedTopicQueryService());
    }

    @Override
    public AggregationResult aggregate(RecognitionEvidence evidence) {
        AggregationResult result = new AggregationResult();
        if (evidence == null) {
            log.warn("信息聚合跳过：缺少识别证据");
            return result.setErrors(List.of("缺少识别证据"));
        }

        result.getErrors().addAll(evidence.getErrors());
        ResolvedPersonProfile profile;
        try {
            profile = resolveProfileFromEvidence(
                    evidence.getWebEvidences(),
                    firstSeedQuery(evidence),
                    result.getWarnings()
            );
        } catch (ApiCallException ex) {
            if (isLlmProfileFailure(ex)) {
                result.getErrors().add(LLM_FAILURE_MESSAGE);
                log.error("人物信息大模型提取失败 seed={} error={}", firstSeedQuery(evidence), ex.getMessage(), ex);
                return result;
            }
            throw ex;
        }
        String resolvedName = resolveNameOrFallback(profile, evidence);
        EnrichedProfile enrichedProfile = enrichProfileByResolvedName(
                profile,
                resolvedName,
                result.getWarnings()
        );
        profile = enrichedProfile.profile();
        profile = enrichProfileSectionsByResolvedName(profile, resolvedName);
        resolvedName = resolveNameOrFallback(profile, evidence);
        if (!StringUtils.hasText(resolvedName)) {
            result.getErrors().add("未能从识别证据中解析人物名称");
            String finalSummary = buildFinalSummary(profile);
            result.setPerson(new PersonAggregate()
                    .setDescription(appendSuffix(cleanText(profile.getDescription()), LLM_SUFFIX))
                    .setSummary(appendSuffix(finalSummary, LLM_SUFFIX))
                    .setSummaryParagraphs(profile.getSummaryParagraphs())
                    // 这三个字段分别对应前端的教育、家庭、职业独立区域，避免继续依赖单一长摘要。
                    .setEducationSummary(profile.getEducationSummary())
                    .setEducationSummaryParagraphs(profile.getEducationSummaryParagraphs())
                    .setFamilyBackgroundSummary(profile.getFamilyBackgroundSummary())
                    .setFamilyBackgroundSummaryParagraphs(profile.getFamilyBackgroundSummaryParagraphs())
                    .setCareerSummary(profile.getCareerSummary())
                    .setCareerSummaryParagraphs(profile.getCareerSummaryParagraphs())
                    .setChinaRelatedStatementsSummary(profile.getChinaRelatedStatementsSummary())
                    .setChinaRelatedStatementsSummaryParagraphs(profile.getChinaRelatedStatementsSummaryParagraphs())
                    .setPoliticalTendencySummary(profile.getPoliticalTendencySummary())
                    .setPoliticalTendencySummaryParagraphs(profile.getPoliticalTendencySummaryParagraphs())
                    .setContactInformationSummary(profile.getContactInformationSummary())
                    .setContactInformationSummaryParagraphs(profile.getContactInformationSummaryParagraphs())
                    .setFamilyMemberSituationSummary(profile.getFamilyMemberSituationSummary())
                    .setFamilyMemberSituationSummaryParagraphs(profile.getFamilyMemberSituationSummaryParagraphs())
                    .setMisconductSummary(profile.getMisconductSummary())
                    .setMisconductSummaryParagraphs(profile.getMisconductSummaryParagraphs())
                    .setImageUrl(cleanText(enrichedProfile.imageUrl()))
                    .setBasicInfo(profile.getBasicInfo())
                    .setOfficialWebsite(profile.getOfficialWebsite())
                    .setWikipedia(profile.getWikipedia()));
            return result;
        }

        String finalResolvedName = resolvedName;
        CompletableFuture<List<SocialAccount>> socialFuture = CompletableFuture
                .supplyAsync(() -> collectSocialAccounts(finalResolvedName), executor)
                .orTimeout(10, TimeUnit.SECONDS);

        result.setPerson(buildPersonFromProfile(profile, finalResolvedName, enrichedProfile.imageUrl()));
        result.setSocialAccounts(deduplicateSocialAccounts(joinTask("社交账号", socialFuture, List.of(), result.getErrors())));
        return result;
    }

    ResolvedPersonProfile resolveProfileFromEvidence(List<WebEvidence> evidences, String fallbackName) {
        return resolveProfileFromEvidence(evidences, fallbackName, new ArrayList<>());
    }

    ResolvedPersonProfile resolveProfileFromEvidence(List<WebEvidence> evidences,
                                                     String fallbackName,
                                                     List<String> warnings) {
        List<String> urls = selectTopUrls(evidences);
        if (urls.isEmpty()) {
            return new ResolvedPersonProfile().setResolvedName(fallbackName);
        }

        List<PageContent> pages = List.of();
        try {
            pages = jinaReaderClient.readPages(urls);
        } catch (RuntimeException ex) {
            log.warn("Jina 正文提取失败 fallbackName={} urlCount={} error={}", fallbackName, urls.size(), ex.getMessage(), ex);
        }
        if (pages == null || pages.isEmpty()) {
            pages = buildFallbackPages(evidences, urls);
        }
        if (pages.isEmpty()) {
            return new ResolvedPersonProfile()
                    .setResolvedName(fallbackName)
                    .setEvidenceUrls(urls);
        }

        List<PageSummary> pageSummaries = collectPageSummaries(fallbackName, pages);
        if (pageSummaries.isEmpty()) {
            warnings.add(SUMMARY_WARNING);
            return new ResolvedPersonProfile()
                    .setResolvedName(fallbackName)
                    .setEvidenceUrls(urls);
        }

        ResolvedPersonProfile profile;
        try {
            profile = summarizePersonFromPageSummariesWithFallback(fallbackName, pageSummaries, true);
            if (profile == null) {
                warnings.add(SUMMARY_WARNING);
                return new ResolvedPersonProfile().setResolvedName(fallbackName).setEvidenceUrls(urls);
            }
            if (profile.getEvidenceUrls() == null || profile.getEvidenceUrls().isEmpty()) {
                profile.setEvidenceUrls(pageSummaries.stream()
                        .map(PageSummary::getSourceUrl)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList());
            }
            enrichProfileParagraphSources(profile, pageSummaries);
        } catch (RuntimeException ex) {
            if (isLlmProfileFailure(ex)) {
                throw ex;
            }
            log.error("Kimi 最终总结失败 fallbackName={} pageSummaryCount={} category={} error={}",
                    fallbackName, pageSummaries.size(), classifySummaryFailure(ex), ex.getMessage(), ex);
            warnings.add(SUMMARY_WARNING);
            return new ResolvedPersonProfile()
                    .setResolvedName(fallbackName)
                    .setEvidenceUrls(urls);
        }

        ResolvedPersonProfile judgedProfile = applyComprehensiveJudgement(fallbackName, pageSummaries, profile, warnings);
        enrichProfileParagraphSources(judgedProfile, pageSummaries);
        return judgedProfile;
    }

    private ResolvedPersonProfile applyComprehensiveJudgement(String fallbackName,
                                                              List<PageSummary> pageSummaries,
                                                              ResolvedPersonProfile profile,
                                                              List<String> warnings) {
        if (deepSeekSummaryGenerationClient == null) {
            return applyComprehensiveJudgementWithSingleProvider(fallbackName, pageSummaries, profile, warnings);
        }
        try {
            ResolvedPersonProfile judged = deepSeekSummaryGenerationClient.applyComprehensiveJudgement(
                    fallbackName,
                    pageSummaries,
                    profile
            );
            return completeJudgedProfile(judged, profile, warnings);
        } catch (RuntimeException deepSeekEx) {
            log.error("综合判断DeepSeek失败 fallbackName={} pageSummaryCount={} category={} error={}",
                    fallbackName, pageSummaries.size(),
                    classifySummaryFailure(deepSeekEx), deepSeekEx.getMessage(), deepSeekEx);
            try {
                ResolvedPersonProfile judged = summaryGenerationClient.applyComprehensiveJudgement(
                        fallbackName,
                        pageSummaries,
                        profile
                );
                return completeJudgedProfile(judged, profile, warnings);
            } catch (RuntimeException kimiEx) {
                log.error("综合判断Kimi兜底失败 fallbackName={} pageSummaryCount={} category={} error={}",
                        fallbackName, pageSummaries.size(),
                        classifySummaryFailure(kimiEx), kimiEx.getMessage(), kimiEx);
                throw new ApiCallException("LLM_PROFILE_FAILED: " + LLM_FAILURE_MESSAGE, kimiEx);
            }
        }
    }

    private PersonAggregate buildPersonFromProfile(ResolvedPersonProfile profile, String resolvedName, String imageUrl) {
        String shortDescription = cleanText(profile.getDescription());
        String longSummary = buildFinalSummary(profile);
        return new PersonAggregate()
                .setName(resolvedName)
                .setDescription(appendSuffix(StringUtils.hasText(shortDescription) ? shortDescription : longSummary, LLM_SUFFIX))
                .setSummary(appendSuffix(longSummary, LLM_SUFFIX))
                .setSummaryParagraphs(profile.getSummaryParagraphs())
                // 这三个字段分别向前端输出独立内容块，方便单独渲染教育、家庭和职业信息。
                .setEducationSummary(profile.getEducationSummary())
                .setEducationSummaryParagraphs(profile.getEducationSummaryParagraphs())
                .setFamilyBackgroundSummary(profile.getFamilyBackgroundSummary())
                .setFamilyBackgroundSummaryParagraphs(profile.getFamilyBackgroundSummaryParagraphs())
                .setCareerSummary(profile.getCareerSummary())
                .setCareerSummaryParagraphs(profile.getCareerSummaryParagraphs())
                .setChinaRelatedStatementsSummary(profile.getChinaRelatedStatementsSummary())
                .setChinaRelatedStatementsSummaryParagraphs(profile.getChinaRelatedStatementsSummaryParagraphs())
                .setPoliticalTendencySummary(profile.getPoliticalTendencySummary())
                .setPoliticalTendencySummaryParagraphs(profile.getPoliticalTendencySummaryParagraphs())
                .setContactInformationSummary(profile.getContactInformationSummary())
                .setContactInformationSummaryParagraphs(profile.getContactInformationSummaryParagraphs())
                .setFamilyMemberSituationSummary(profile.getFamilyMemberSituationSummary())
                .setFamilyMemberSituationSummaryParagraphs(profile.getFamilyMemberSituationSummaryParagraphs())
                .setMisconductSummary(profile.getMisconductSummary())
                .setMisconductSummaryParagraphs(profile.getMisconductSummaryParagraphs())
                .setImageUrl(cleanText(imageUrl))
                .setWikipedia(cleanText(profile.getWikipedia()))
                .setOfficialWebsite(cleanText(profile.getOfficialWebsite()))
                .setTags(profile.getTags() == null ? List.of() : profile.getTags())
                .setBasicInfo(profile.getBasicInfo())
                .setEvidenceUrls(profile.getEvidenceUrls());
    }

    String buildFinalSummary(ResolvedPersonProfile profile) {
        if (profile == null) {
            return null;
        }
        // summary 只承载人物主体画像，教育/家庭/职业改由独立字段输出，避免正文和分区内容重复。
        return cleanText(profile.getSummary());
    }

    private ResolvedPersonProfile copyProfile(ResolvedPersonProfile profile) {
        if (profile == null) {
            return null;
        }
        return new ResolvedPersonProfile()
                .setResolvedName(profile.getResolvedName())
                .setDescription(profile.getDescription())
                .setSummary(profile.getSummary())
                .setSummaryParagraphs(profile.getSummaryParagraphs())
                .setEducationSummary(profile.getEducationSummary())
                .setEducationSummaryParagraphs(profile.getEducationSummaryParagraphs())
                .setFamilyBackgroundSummary(profile.getFamilyBackgroundSummary())
                .setFamilyBackgroundSummaryParagraphs(profile.getFamilyBackgroundSummaryParagraphs())
                .setCareerSummary(profile.getCareerSummary())
                .setCareerSummaryParagraphs(profile.getCareerSummaryParagraphs())
                .setChinaRelatedStatementsSummary(profile.getChinaRelatedStatementsSummary())
                .setChinaRelatedStatementsSummaryParagraphs(profile.getChinaRelatedStatementsSummaryParagraphs())
                .setPoliticalTendencySummary(profile.getPoliticalTendencySummary())
                .setPoliticalTendencySummaryParagraphs(profile.getPoliticalTendencySummaryParagraphs())
                .setContactInformationSummary(profile.getContactInformationSummary())
                .setContactInformationSummaryParagraphs(profile.getContactInformationSummaryParagraphs())
                .setFamilyMemberSituationSummary(profile.getFamilyMemberSituationSummary())
                .setFamilyMemberSituationSummaryParagraphs(profile.getFamilyMemberSituationSummaryParagraphs())
                .setMisconductSummary(profile.getMisconductSummary())
                .setMisconductSummaryParagraphs(profile.getMisconductSummaryParagraphs())
                .setKeyFacts(profile.getKeyFacts())
                .setTags(profile.getTags())
                .setEvidenceUrls(profile.getEvidenceUrls())
                .setWikipedia(profile.getWikipedia())
                .setOfficialWebsite(profile.getOfficialWebsite())
                .setBasicInfo(profile.getBasicInfo());
    }

    String summarizeSection(String resolvedName, String sectionType, String query) {
        try {
            SectionSearchBundle bundle = collectSectionSearchBundle(resolvedName, sectionType, query);
            if (bundle.pageSummaries().isEmpty()) {
                return null;
            }
            if (isDerivedSectionedTopic(sectionType)) {
                String sectionedSummary = summarizeSectionedTopic(resolvedName, sectionType, bundle.pageSummaries(), bundle.expansionQueries());
                if (StringUtils.hasText(sectionedSummary)) {
                    return sectionedSummary;
                }
            }
            return summarizeSectionWithFallback(resolvedName, sectionType, bundle.pageSummaries());
        } catch (RuntimeException ex) {
            log.warn("section summary failed resolvedName={} sectionType={} query={} error={}",
                    resolvedName, sectionType, query, ex.getMessage(), ex);
            return null;
        }
    }

    private List<ParagraphSummaryItem> summarizeSectionParagraphs(String resolvedName, String sectionType, String query) {
        try {
            SectionSearchBundle bundle = collectSectionSearchBundle(resolvedName, sectionType, query);
            if (bundle.pageSummaries().isEmpty()) {
                return List.of();
            }
            return summarizeSectionParagraphsWithFallback(resolvedName, sectionType, bundle.pageSummaries());
        } catch (RuntimeException ex) {
            log.warn("section paragraph summary failed resolvedName={} sectionType={} query={} error={}",
                    resolvedName, sectionType, query, ex.getMessage(), ex);
            return List.of();
        }
    }

    private static final class PassThroughDerivedTopicQueryService implements DerivedTopicQueryService {

        @Override
        public TopicQueryDecision resolveQuery(DerivedTopicRequest request) {
            return new TopicQueryDecision().setFinalQuery(request == null ? null : request.getRawQuery());
        }
    }

    private SectionEnrichmentResult joinSectionEnrichmentFuture(CompletableFuture<SectionEnrichmentResult> future) {
        try {
            SectionEnrichmentResult result = future.join();
            return result == null ? SectionEnrichmentResult.empty() : result;
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.warn("section enrichment future failed error={}", cause.getMessage(), cause);
            return SectionEnrichmentResult.empty();
        }
    }

    private List<PageSummary> collectPageSummaries(String fallbackName, List<PageContent> pages) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }

        List<PageSummary> pageSummaries = new ArrayList<>();
        for (PageContent page : pages) {
            if (page == null || !StringUtils.hasText(page.getContent())) {
                continue;
            }
            // 视频页通常只有播放器或字幕片段，送去做人物网页摘要只会放大模型拒答和噪声。
            if (shouldSkipSummaryUrl(page.getUrl())) {
                log.info("跳过非正文页面 fallbackName={} url={}", fallbackName, page.getUrl());
                continue;
            }
            try {
                PageSummary pageSummary = summarizePageWithRouting(fallbackName, page);
                if (pageSummary == null || !StringUtils.hasText(pageSummary.getSummary())) {
                    continue;
                }
                if (!StringUtils.hasText(pageSummary.getSourceUrl())) {
                    pageSummary.setSourceUrl(page.getUrl());
                }
                if (!StringUtils.hasText(pageSummary.getTitle())) {
                    pageSummary.setTitle(page.getTitle());
                }
                pageSummaries.add(pageSummary);
            } catch (RuntimeException ex) {
                log.warn("页面摘要失败 fallbackName={} url={} category={} error={}",
                        fallbackName, page.getUrl(), classifySummaryFailure(ex), ex.getMessage(), ex);
            }
        }
        return pageSummaries;
    }

    private EnrichedProfile enrichProfileByResolvedName(ResolvedPersonProfile profile,
                                                        String resolvedName,
                                                        List<String> warnings) {
        if (!shouldRunSecondarySearch(profile, resolvedName)) {
            return new EnrichedProfile(profile, null);
        }

        SerpApiResponse searchResponse;
        try {
            searchResponse = googleSearchClient.googleSearch(resolvedName);
        } catch (RuntimeException ex) {
            log.warn("secondary search failed resolvedName={} error={}", resolvedName, ex.getMessage(), ex);
            warnings.add(SECONDARY_SEARCH_WARNING);
            return new EnrichedProfile(profile, null);
        }

        if (searchResponse == null || searchResponse.getRoot() == null) {
            return new EnrichedProfile(profile, null);
        }

        String imageUrl = extractKnowledgeGraphImageUrl(searchResponse.getRoot());
        List<WebEvidence> webEvidences = extractSearchOrganicWebEvidence(searchResponse.getRoot());
        if (webEvidences.isEmpty()) {
            return new EnrichedProfile(profile, imageUrl);
        }

        ResolvedPersonProfile mergedProfile = summarizeSecondaryEvidence(
                resolvedName,
                webEvidences,
                profile,
                warnings
        );
        return new EnrichedProfile(mergedProfile, imageUrl);
    }

    private ResolvedPersonProfile enrichProfileSectionsByResolvedName(ResolvedPersonProfile profile, String resolvedName) {
        if (profile == null || !StringUtils.hasText(resolvedName) || !StringUtils.hasText(profile.getSummary())) {
            return profile;
        }

        CompletableFuture<SectionEnrichmentResult> educationFuture = CompletableFuture.supplyAsync(
                () -> summarizeSectionEnrichment(resolvedName, EDUCATION_SECTION, resolvedName + "的教育经历"), executor);
        CompletableFuture<SectionEnrichmentResult> familyFuture = CompletableFuture.supplyAsync(
                () -> summarizeSectionEnrichment(resolvedName, FAMILY_SECTION, resolvedName + "的家庭背景"), executor);
        CompletableFuture<SectionEnrichmentResult> careerFuture = CompletableFuture.supplyAsync(
                () -> summarizeSectionEnrichment(resolvedName, CAREER_SECTION, resolvedName + "的职业经历"), executor);
        // 关键逻辑：新增主题统一走派生主题摘要链路，前端直接按独立区域渲染，不再依赖长摘要二次拆分。
        CompletableFuture<SectionEnrichmentResult> chinaRelatedStatementsFuture = CompletableFuture.supplyAsync(
                () -> summarizeSectionEnrichment(resolvedName, CHINA_RELATED_STATEMENTS_SECTION,
                        resolvedName + " 涉华言论 中国评价 中美关系 中欧关系"), executor);
        CompletableFuture<SectionEnrichmentResult> politicalViewFuture = CompletableFuture.supplyAsync(
                () -> summarizeSectionEnrichment(resolvedName, POLITICAL_VIEW_SECTION,
                        resolvedName + " 政治倾向 政党 政治理念 政策立场"), executor);
        CompletableFuture<SectionEnrichmentResult> contactInformationFuture = CompletableFuture.supplyAsync(
                () -> summarizeSectionEnrichment(resolvedName, CONTACT_INFORMATION_SECTION,
                        resolvedName + " 公开通讯 办公电话 官方邮箱 认证社交账号 联系方式"), executor);
        CompletableFuture<SectionEnrichmentResult> familyMemberSituationFuture = CompletableFuture.supplyAsync(
                () -> summarizeSectionEnrichment(resolvedName, FAMILY_MEMBER_SITUATION_SECTION,
                        resolvedName + " 家族成员 亲属 经商 在华投资 商业纠纷"), executor);
        CompletableFuture<SectionEnrichmentResult> misconductFuture = CompletableFuture.supplyAsync(
                () -> summarizeSectionEnrichment(resolvedName, MISCONDUCT_SECTION,
                        resolvedName + " 违法记录 行政处罚 负面事件 失信"), executor);

        ResolvedPersonProfile enriched = copyProfile(profile);
        SectionEnrichmentResult educationResult = joinSectionEnrichmentFuture(educationFuture);
        SectionEnrichmentResult familyResult = joinSectionEnrichmentFuture(familyFuture);
        SectionEnrichmentResult careerResult = joinSectionEnrichmentFuture(careerFuture);
        SectionEnrichmentResult chinaRelatedStatementsResult = joinSectionEnrichmentFuture(chinaRelatedStatementsFuture);
        SectionEnrichmentResult politicalViewResult = joinSectionEnrichmentFuture(politicalViewFuture);
        SectionEnrichmentResult contactInformationResult = joinSectionEnrichmentFuture(contactInformationFuture);
        SectionEnrichmentResult familyMemberSituationResult = joinSectionEnrichmentFuture(familyMemberSituationFuture);
        SectionEnrichmentResult misconductResult = joinSectionEnrichmentFuture(misconductFuture);

        enriched.setEducationSummary(firstNonBlankText(educationResult.summary(), profile.getEducationSummary()));
        enriched.setEducationSummaryParagraphs(pickParagraphs(educationResult.paragraphs(), profile.getEducationSummaryParagraphs()));
        enriched.setFamilyBackgroundSummary(firstNonBlankText(familyResult.summary(), profile.getFamilyBackgroundSummary()));
        enriched.setFamilyBackgroundSummaryParagraphs(pickParagraphs(familyResult.paragraphs(), profile.getFamilyBackgroundSummaryParagraphs()));
        enriched.setCareerSummary(firstNonBlankText(careerResult.summary(), profile.getCareerSummary()));
        enriched.setCareerSummaryParagraphs(pickParagraphs(careerResult.paragraphs(), profile.getCareerSummaryParagraphs()));
        enriched.setChinaRelatedStatementsSummary(firstNonBlankText(
                chinaRelatedStatementsResult.summary(), profile.getChinaRelatedStatementsSummary()));
        enriched.setChinaRelatedStatementsSummaryParagraphs(pickParagraphs(
                chinaRelatedStatementsResult.paragraphs(), profile.getChinaRelatedStatementsSummaryParagraphs()));
        enriched.setPoliticalTendencySummary(firstNonBlankText(
                politicalViewResult.summary(), profile.getPoliticalTendencySummary()));
        enriched.setPoliticalTendencySummaryParagraphs(pickParagraphs(
                politicalViewResult.paragraphs(), profile.getPoliticalTendencySummaryParagraphs()));
        enriched.setContactInformationSummary(firstNonBlankText(
                contactInformationResult.summary(), profile.getContactInformationSummary()));
        enriched.setContactInformationSummaryParagraphs(pickParagraphs(
                contactInformationResult.paragraphs(), profile.getContactInformationSummaryParagraphs()));
        enriched.setFamilyMemberSituationSummary(firstNonBlankText(
                familyMemberSituationResult.summary(), profile.getFamilyMemberSituationSummary()));
        enriched.setFamilyMemberSituationSummaryParagraphs(pickParagraphs(
                familyMemberSituationResult.paragraphs(), profile.getFamilyMemberSituationSummaryParagraphs()));
        enriched.setMisconductSummary(firstNonBlankText(
                misconductResult.summary(), profile.getMisconductSummary()));
        enriched.setMisconductSummaryParagraphs(pickParagraphs(
                misconductResult.paragraphs(), profile.getMisconductSummaryParagraphs()));
        return enriched;
    }

    private SectionEnrichmentResult summarizeSectionEnrichment(String resolvedName, String sectionType, String fallbackQuery) {
        try {
            SectionSearchBundle bundle = collectSectionSearchBundle(resolvedName, sectionType, fallbackQuery);
            if (bundle.pageSummaries() == null || bundle.pageSummaries().isEmpty()) {
                return SectionEnrichmentResult.empty();
            }
            String summary = null;
            if (isDerivedSectionedTopic(sectionType)) {
                summary = summarizeSectionedTopic(resolvedName, sectionType, bundle.pageSummaries(), bundle.expansionQueries());
            }
            if (!StringUtils.hasText(summary)) {
                summary = summarizeSectionWithFallback(resolvedName, sectionType, bundle.pageSummaries());
            }
            return new SectionEnrichmentResult(
                    summary,
                    summarizeSectionParagraphsWithFallback(resolvedName, sectionType, bundle.pageSummaries())
            );
        } catch (RuntimeException ex) {
            log.warn("section enrichment failed resolvedName={} sectionType={} query={} error={}",
                    resolvedName, sectionType, fallbackQuery, ex.getMessage(), ex);
            return SectionEnrichmentResult.empty();
        }
    }

    private boolean shouldRunSecondarySearch(ResolvedPersonProfile profile, String resolvedName) {
        return StringUtils.hasText(resolvedName)
                && profile != null
                && (StringUtils.hasText(profile.getDescription()) || StringUtils.hasText(profile.getSummary()));
    }

    private String extractKnowledgeGraphImageUrl(com.fasterxml.jackson.databind.JsonNode root) {
        return cleanText(firstNonBlank(root.path("knowledgeGraph"), "imageUrl"));
    }

    private List<WebEvidence> extractSearchOrganicWebEvidence(com.fasterxml.jackson.databind.JsonNode root) {
        List<WebEvidence> evidences = new ArrayList<>();
        collectKnowledgeGraphEvidence(evidences, root.path("knowledgeGraph"));
        collectOrganicEvidence(evidences, root.path("organic"));
        return deduplicateWebEvidencePrioritized(evidences);
    }

    private void collectKnowledgeGraphEvidence(List<WebEvidence> evidences, com.fasterxml.jackson.databind.JsonNode knowledgeGraph) {
        if (knowledgeGraph == null || knowledgeGraph.isMissingNode()) {
            return;
        }
        String url = cleanText(firstNonBlank(knowledgeGraph, "descriptionLink"));
        if (!StringUtils.hasText(url)) {
            return;
        }
        evidences.add(new WebEvidence()
                .setUrl(url)
                .setTitle(cleanText(firstNonBlank(knowledgeGraph, "title")))
                .setSource(cleanText(firstNonBlank(knowledgeGraph, "descriptionSource")))
                .setSnippet(cleanText(firstNonBlank(knowledgeGraph, "description")))
                .setSourceEngine(SECONDARY_SEARCH_SOURCE));
    }

    private void collectOrganicEvidence(List<WebEvidence> evidences, com.fasterxml.jackson.databind.JsonNode organicNodes) {
        if (organicNodes == null || !organicNodes.isArray()) {
            return;
        }
        for (com.fasterxml.jackson.databind.JsonNode node : organicNodes) {
            String url = cleanText(firstNonBlank(node, "link"));
            if (!StringUtils.hasText(url)) {
                continue;
            }
            evidences.add(new WebEvidence()
                    .setUrl(url)
                    .setTitle(cleanText(firstNonBlank(node, "title")))
                    .setSource(cleanText(firstNonBlank(node, "source")))
                    .setSnippet(cleanText(firstNonBlank(node, "snippet")))
                    .setSourceEngine(SECONDARY_SEARCH_SOURCE));
        }
    }

    private List<WebEvidence> deduplicateWebEvidencePrioritized(List<WebEvidence> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return List.of();
        }
        List<WebEvidence> sorted = new ArrayList<>(evidences);
        sorted.sort(Comparator.comparingInt(this::searchEvidencePriority));
        Set<String> seen = new LinkedHashSet<>();
        List<WebEvidence> deduplicated = new ArrayList<>();
        for (WebEvidence evidence : sorted) {
            String key = StringUtils.hasText(evidence.getUrl())
                    ? evidence.getUrl()
                    : (evidence.getSourceEngine() + "|" + evidence.getTitle());
            if (seen.add(key)) {
                deduplicated.add(evidence);
            }
        }
        return deduplicated;
    }

    // 派生主题统一走“两阶段搜索”：先跑基础拆分 query，再根据首轮摘要做模型扩展。
    private SectionSearchBundle collectSectionSearchBundle(String resolvedName, String sectionType, String fallbackQuery) {
        List<String> baseQueries = buildBaseQueries(resolvedName, sectionType);
        if (baseQueries.isEmpty() && StringUtils.hasText(fallbackQuery)) {
            baseQueries = List.of(fallbackQuery);
        }
        List<PageSummary> firstRound = collectPageSummariesForQueries(resolvedName, sectionType, baseQueries);
        List<TopicExpansionQuery> expansionQueries = sanitizeExpansionQueries(
                expandTopicQueriesWithFallback(resolvedName, sectionType, firstRound)
        );
        List<String> secondRoundQueries = expansionQueries.stream()
                .map(TopicExpansionQuery::getTerm)
                .filter(StringUtils::hasText)
                .map(term -> resolvedName + " " + term)
                .toList();
        List<PageSummary> mergedPageSummaries = mergePageSummaries(
                firstRound,
                collectPageSummariesForQueries(resolvedName, sectionType, secondRoundQueries)
        );
        return new SectionSearchBundle(mergedPageSummaries, expansionQueries);
    }

    private List<String> buildBaseQueries(String resolvedName, String sectionType) {
        if (!StringUtils.hasText(resolvedName) || !StringUtils.hasText(sectionType)) {
            return List.of();
        }
        // 关键逻辑：本地 application.yml 若遗漏拆分模板，仍需回退到内建基础查询词，
        // 否则派生主题会重新退回单条长 query，直接缩小搜索召回范围。
        List<String> templates = properties.getApi().getQueryRewrite().resolveBaseQueryTemplates(sectionType);
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }
        return templates.stream()
                .map(template -> template == null ? null : template.formatted(resolvedName))
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<PageSummary> collectPageSummariesForQueries(String resolvedName, String sectionType, List<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }
        List<PageSummary> collected = new ArrayList<>();
        for (String query : queries) {
            collected.addAll(collectPageSummariesForSingleQuery(resolvedName, sectionType, query));
        }
        return collected;
    }

    private List<PageSummary> collectPageSummariesForSingleQuery(String resolvedName, String sectionType, String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        try {
            String finalQuery = resolveSectionQuery(resolvedName, sectionType, query);
            SerpApiResponse searchResponse = googleSearchClient.googleSearch(finalQuery);
            if (searchResponse == null || searchResponse.getRoot() == null) {
                return List.of();
            }
            List<WebEvidence> evidences = extractSearchOrganicWebEvidence(searchResponse.getRoot());
            List<String> urls = selectTopUrls(evidences);
            if (urls.isEmpty()) {
                return List.of();
            }
            List<PageContent> pages = List.of();
            try {
                pages = jinaReaderClient.readPages(urls);
            } catch (RuntimeException ex) {
                log.warn("section jina read failed resolvedName={} sectionType={} query={} urlCount={} error={}",
                        resolvedName, sectionType, finalQuery, urls.size(), ex.getMessage(), ex);
            }
            if (pages == null || pages.isEmpty()) {
                pages = buildFallbackPages(evidences, urls);
            }
            if (pages == null || pages.isEmpty()) {
                return List.of();
            }
            return collectPageSummaries(resolvedName, pages);
        } catch (RuntimeException ex) {
            log.warn("section query failed resolvedName={} sectionType={} query={} error={}",
                    resolvedName, sectionType, query, ex.getMessage(), ex);
            return List.of();
        }
    }

    private String resolveSectionQuery(String resolvedName, String sectionType, String query) {
        TopicQueryDecision decision = derivedTopicQueryService.resolveQuery(new DerivedTopicRequest()
                .setResolvedName(resolvedName)
                .setTopicType(DerivedTopicType.fromSectionType(sectionType))
                .setRawQuery(query));
        return decision == null || !StringUtils.hasText(decision.getFinalQuery()) ? query : decision.getFinalQuery();
    }

    // 扩展词推断只负责给出“继续搜什么”和“为什么搜”，不直接改写最终摘要。
    private TopicExpansionDecision expandTopicQueriesWithFallback(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        if (!StringUtils.hasText(resolvedName)
                || !StringUtils.hasText(sectionType)
                || pageSummaries == null
                || pageSummaries.isEmpty()
                || !properties.getApi().getQueryRewrite().getExpandEnabledTopics().contains(sectionType)) {
            return new TopicExpansionDecision().setShouldExpand(false).setExpansionQueries(List.of());
        }
        try {
            if (deepSeekSummaryGenerationClient != null) {
                return deepSeekSummaryGenerationClient.expandTopicQueriesFromPageSummaries(resolvedName, sectionType, pageSummaries);
            }
        } catch (RuntimeException ex) {
            log.warn("deepseek topic expansion failed resolvedName={} sectionType={} error={}",
                    resolvedName, sectionType, ex.getMessage(), ex);
        }
        try {
            return summaryGenerationClient.expandTopicQueriesFromPageSummaries(resolvedName, sectionType, pageSummaries);
        } catch (RuntimeException ex) {
            log.warn("topic expansion failed resolvedName={} sectionType={} error={}",
                    resolvedName, sectionType, ex.getMessage(), ex);
            return new TopicExpansionDecision().setShouldExpand(false).setExpansionQueries(List.of());
        }
    }

    private List<TopicExpansionQuery> sanitizeExpansionQueries(TopicExpansionDecision decision) {
        if (decision == null || !Boolean.TRUE.equals(decision.getShouldExpand()) || decision.getExpansionQueries() == null) {
            return List.of();
        }
        int maxQueryCount = properties.getApi().getQueryRewrite().getExpandMaxQueryCount();
        int maxTermLength = properties.getApi().getQueryRewrite().getExpandMaxTermLength();
        Map<String, TopicExpansionQuery> deduplicated = new LinkedHashMap<>();
        for (TopicExpansionQuery query : decision.getExpansionQueries()) {
            if (query == null || !StringUtils.hasText(query.getTerm())) {
                continue;
            }
            String normalizedTerm = sanitizeExpansionTerm(query.getTerm());
            if (!StringUtils.hasText(normalizedTerm)) {
                continue;
            }
            if (normalizedTerm.length() > maxTermLength) {
                continue;
            }
            deduplicated.putIfAbsent(normalizedTerm, new TopicExpansionQuery()
                    .setTerm(normalizedTerm)
                    .setSection(cleanText(query.getSection()))
                    .setReason(cleanText(query.getReason())));
            if (deduplicated.size() >= maxQueryCount) {
                break;
            }
        }
        return List.copyOf(deduplicated.values());
    }

    private String sanitizeExpansionTerm(String term) {
        if (!StringUtils.hasText(term)) {
            return null;
        }
        String sanitized = term.trim().replaceAll("\\s+", " ");
        for (String sourceSiteTerm : EXPANSION_SOURCE_SITE_TERMS) {
            sanitized = stripExpansionSourceSiteToken(sanitized, sourceSiteTerm);
        }
        sanitized = sanitized
                .replaceAll("\\s+", " ")
                .replaceAll("^[,，;；:：|/\\\\-]+", "")
                .replaceAll("[,，;；:：|/\\\\-]+$", "")
                .trim();
        return StringUtils.hasText(sanitized) ? sanitized : null;
    }

    private String stripExpansionSourceSiteToken(String term, String sourceSiteTerm) {
        if (!StringUtils.hasText(term) || !StringUtils.hasText(sourceSiteTerm)) {
            return term;
        }
        return term.replaceAll("(?i)(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(sourceSiteTerm) + "(?![\\p{L}\\p{N}])", " ");
    }

    private List<PageSummary> mergePageSummaries(List<PageSummary> primary, List<PageSummary> secondary) {
        Map<String, PageSummary> deduplicated = new LinkedHashMap<>();
        if (primary != null) {
            primary.forEach(summary -> putPageSummary(deduplicated, summary));
        }
        if (secondary != null) {
            secondary.forEach(summary -> putPageSummary(deduplicated, summary));
        }
        return List.copyOf(deduplicated.values());
    }

    private void putPageSummary(Map<String, PageSummary> target, PageSummary summary) {
        if (target == null || summary == null) {
            return;
        }
        String key = StringUtils.hasText(summary.getSourceUrl())
                ? summary.getSourceUrl()
                : ((summary.getTitle() == null ? "" : summary.getTitle()) + "|" + (summary.getSummary() == null ? "" : summary.getSummary()));
        target.putIfAbsent(key, summary);
    }

    private boolean isDerivedSectionedTopic(String sectionType) {
        return DERIVED_SECTION_TITLE_MAPS.containsKey(sectionType);
    }

    private String summarizeSectionedTopic(String resolvedName,
                                           String sectionType,
                                           List<PageSummary> pageSummaries,
                                           List<TopicExpansionQuery> expansionQueries) {
        try {
            if (deepSeekSummaryGenerationClient != null) {
                String deepSeekSummary = formatSectionedTopicSummary(
                        sectionType,
                        deepSeekSummaryGenerationClient.summarizeSectionedSectionFromPageSummaries(resolvedName, sectionType, pageSummaries),
                        expansionQueries
                );
                if (StringUtils.hasText(deepSeekSummary)) {
                    return deepSeekSummary;
                }
            }
        } catch (RuntimeException ex) {
            log.warn("deepseek sectioned summary failed resolvedName={} sectionType={} error={}",
                    resolvedName, sectionType, ex.getMessage(), ex);
        }
        try {
            String kimiSummary = formatSectionedTopicSummary(
                    sectionType,
                    summaryGenerationClient.summarizeSectionedSectionFromPageSummaries(resolvedName, sectionType, pageSummaries),
                    expansionQueries
            );
            if (StringUtils.hasText(kimiSummary)) {
                return kimiSummary;
            }
        } catch (RuntimeException ex) {
            log.warn("sectioned summary failed resolvedName={} sectionType={} error={}",
                    resolvedName, sectionType, ex.getMessage(), ex);
        }
        return null;
    }

    private List<ParagraphSummaryItem> summarizeSectionParagraphsWithFallback(String resolvedName,
                                                                              String sectionType,
                                                                              List<PageSummary> pageSummaries) {
        if (pageSummaries == null || pageSummaries.isEmpty()) {
            return List.of();
        }
        try {
            if (deepSeekSummaryGenerationClient != null) {
                List<ParagraphSummaryItem> paragraphs = toParagraphSummaryItems(
                        deepSeekSummaryGenerationClient.summarizeSectionedSectionFromPageSummaries(resolvedName, sectionType, pageSummaries)
                );
                if (!paragraphs.isEmpty()) {
                    return paragraphs;
                }
            }
        } catch (RuntimeException ex) {
            log.warn("deepseek section paragraphs failed resolvedName={} sectionType={} error={}",
                    resolvedName, sectionType, ex.getMessage(), ex);
        }
        try {
            return toParagraphSummaryItems(
                    summaryGenerationClient.summarizeSectionedSectionFromPageSummaries(resolvedName, sectionType, pageSummaries)
            );
        } catch (RuntimeException ex) {
            log.warn("section paragraphs failed resolvedName={} sectionType={} error={}",
                    resolvedName, sectionType, ex.getMessage(), ex);
            return List.of();
        }
    }

    // 所有基础派生主题都按固定分段输出，并把扩展检索理由挂到对应分段下，避免理由脱离上下文。
    private String formatSectionedTopicSummary(String sectionType,
                                               SectionedSummary sectionedSummary,
                                               List<TopicExpansionQuery> expansionQueries) {
        Map<String, String> sectionTitles = DERIVED_SECTION_TITLE_MAPS.get(sectionType);
        if (sectionTitles == null || sectionTitles.isEmpty()) {
            return null;
        }
        Map<String, List<TopicExpansionQuery>> reasonsBySection = groupExpansionQueriesBySection(sectionTitles, expansionQueries);
        Map<String, SectionSummaryItem> summariesBySection = new LinkedHashMap<>();
        if (sectionedSummary != null && sectionedSummary.getSections() != null) {
            for (SectionSummaryItem item : sectionedSummary.getSections()) {
                if (item == null || !StringUtils.hasText(item.getSection()) || !StringUtils.hasText(item.getSummary())) {
                    continue;
                }
                summariesBySection.putIfAbsent(item.getSection().trim(), item);
            }
        }
        String content = sectionTitles.keySet().stream()
                .map(section -> formatSectionedTopicItem(
                        sectionTitles.get(section),
                        summariesBySection.get(section),
                        reasonsBySection.getOrDefault(section, List.of())
                ))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n"));
        return StringUtils.hasText(content) ? content.trim() : null;
    }

    private Map<String, List<TopicExpansionQuery>> groupExpansionQueriesBySection(Map<String, String> sectionTitles,
                                                                                  List<TopicExpansionQuery> expansionQueries) {
        Map<String, List<TopicExpansionQuery>> grouped = new LinkedHashMap<>();
        if (sectionTitles == null || sectionTitles.isEmpty() || expansionQueries == null || expansionQueries.isEmpty()) {
            return grouped;
        }
        String defaultSection = sectionTitles.keySet().iterator().next();
        for (TopicExpansionQuery query : expansionQueries) {
            if (query == null || !StringUtils.hasText(query.getTerm())) {
                continue;
            }
            String section = StringUtils.hasText(query.getSection()) && sectionTitles.containsKey(query.getSection().trim())
                    ? query.getSection().trim()
                    : defaultSection;
            grouped.computeIfAbsent(section, key -> new ArrayList<>()).add(query);
        }
        return grouped;
    }

    private String formatSectionedTopicItem(String title,
                                            SectionSummaryItem item,
                                            List<TopicExpansionQuery> expansionQueries) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        if (item != null && StringUtils.hasText(item.getSummary())) {
            lines.add(item.getSummary().trim());
        }
        String inferenceLine = formatInferenceReasonLine(expansionQueries);
        if (StringUtils.hasText(inferenceLine)) {
            lines.add(inferenceLine);
        }
        if (lines.isEmpty()) {
            return null;
        }
        return title + "\n" + String.join("\n", lines);
    }

    private String formatInferenceReasonLine(List<TopicExpansionQuery> expansionQueries) {
        if (expansionQueries == null || expansionQueries.isEmpty()) {
            return null;
        }
        String content = expansionQueries.stream()
                .filter(query -> query != null && StringUtils.hasText(query.getTerm()))
                .map(query -> {
                    String term = query.getTerm().trim();
                    String reason = StringUtils.hasText(query.getReason()) ? query.getReason().trim() : "首轮资料存在相关线索";
                    return term + "（" + reason + "）";
                })
                .distinct()
                .collect(Collectors.joining("；"));
        return StringUtils.hasText(content) ? INFERENCE_REASON_PREFIX + content : null;
    }

    private List<ParagraphSummaryItem> toParagraphSummaryItems(SectionedSummary sectionedSummary) {
        if (sectionedSummary == null || sectionedSummary.getSections() == null || sectionedSummary.getSections().isEmpty()) {
            return List.of();
        }
        List<ParagraphSummaryItem> paragraphs = new ArrayList<>();
        for (SectionSummaryItem item : sectionedSummary.getSections()) {
            if (item == null || !StringUtils.hasText(item.getSummary())) {
                continue;
            }
            paragraphs.add(new ParagraphSummaryItem()
                    .setText(item.getSummary().trim())
                    .setSourceUrls(item.getSourceUrls())
                    .setSources(resolveParagraphSources(item.getSources(), item.getSourceUrls())));
        }
        return paragraphs;
    }

    private List<ParagraphSource> resolveParagraphSources(List<ParagraphSource> directSources, List<String> sourceUrls) {
        if (directSources != null && !directSources.isEmpty()) {
            return directSources;
        }
        if (sourceUrls == null || sourceUrls.isEmpty()) {
            return List.of();
        }
        List<ParagraphSource> sources = new ArrayList<>();
        for (String sourceUrl : sourceUrls) {
            if (!StringUtils.hasText(sourceUrl)) {
                continue;
            }
            sources.add(new ParagraphSource()
                    .setTitle(sourceUrl)
                    .setUrl(sourceUrl)
                    .setSource(extractHostLabel(sourceUrl)));
        }
        return sources;
    }

    private String extractHostLabel(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            return uri.getHost();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record SectionSearchBundle(List<PageSummary> pageSummaries, List<TopicExpansionQuery> expansionQueries) {
    }

    private record SectionEnrichmentResult(String summary, List<ParagraphSummaryItem> paragraphs) {

        private static SectionEnrichmentResult empty() {
            return new SectionEnrichmentResult(null, List.of());
        }
    }

    private static Map<String, String> linkedSectionTitles(String... values) {
        Map<String, String> titles = new LinkedHashMap<>();
        for (int index = 0; values != null && index + 1 < values.length; index += 2) {
            titles.put(values[index], values[index + 1]);
        }
        return java.util.Collections.unmodifiableMap(titles);
    }

    private int searchEvidencePriority(WebEvidence evidence) {
        String normalized = normalizeSearchText((evidence == null ? null : evidence.getUrl()) + " " + (evidence == null ? null : evidence.getTitle()));
        if (normalized.contains("wikipedia.org") || normalized.contains("维基百科")) {
            return 0;
        }
        if (normalized.contains("baike.baidu.com") || normalized.contains("百度百科")) {
            return 1;
        }
        return 2;
    }

    private String normalizeSearchText(String text) {
        return text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
    }

    private ResolvedPersonProfile summarizeSecondaryEvidence(String resolvedName,
                                                             List<WebEvidence> evidences,
                                                             ResolvedPersonProfile baseProfile,
                                                             List<String> warnings) {
        List<String> urls = selectTopUrls(evidences);
        if (urls.isEmpty()) {
            return baseProfile;
        }

        List<PageContent> pages = List.of();
        try {
            pages = jinaReaderClient.readPages(urls);
        } catch (RuntimeException ex) {
            log.warn("secondary jina read failed resolvedName={} urlCount={} error={}", resolvedName, urls.size(), ex.getMessage(), ex);
        }
        if (pages == null || pages.isEmpty()) {
            pages = buildFallbackPages(evidences, urls);
        }
        if (pages.isEmpty()) {
            warnings.add(SECONDARY_SEARCH_WARNING);
            return baseProfile;
        }

        List<PageSummary> pageSummaries = collectPageSummaries(resolvedName, pages);
        if (pageSummaries.isEmpty()) {
            warnings.add(SECONDARY_SEARCH_WARNING);
            return baseProfile;
        }

        ResolvedPersonProfile secondaryProfile;
        try {
            secondaryProfile = summarizePersonFromPageSummariesWithFallback(resolvedName, pageSummaries, false);
            if (secondaryProfile == null) {
                warnings.add(SECONDARY_SEARCH_WARNING);
                return baseProfile;
            }
            if (secondaryProfile.getEvidenceUrls() == null || secondaryProfile.getEvidenceUrls().isEmpty()) {
                secondaryProfile.setEvidenceUrls(pageSummaries.stream()
                        .map(PageSummary::getSourceUrl)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList());
            }
        } catch (RuntimeException ex) {
            log.warn("secondary final summary failed resolvedName={} pageSummaryCount={} category={} error={}",
                    resolvedName, pageSummaries.size(), classifySummaryFailure(ex), ex.getMessage(), ex);
            warnings.add(SECONDARY_SEARCH_WARNING);
            return baseProfile;
        }

        ResolvedPersonProfile judgedSecondaryProfile = applyComprehensiveJudgement(
                resolvedName,
                pageSummaries,
                secondaryProfile,
                warnings
        );
        return mergeProfiles(baseProfile, judgedSecondaryProfile, resolvedName);
    }

    private ResolvedPersonProfile mergeProfiles(ResolvedPersonProfile baseProfile,
                                                ResolvedPersonProfile secondaryProfile,
                                                String fallbackName) {
        if (baseProfile == null) {
            return secondaryProfile == null ? new ResolvedPersonProfile().setResolvedName(fallbackName) : secondaryProfile;
        }
        if (secondaryProfile == null) {
            return baseProfile;
        }

        return new ResolvedPersonProfile()
                .setResolvedName(firstNonBlankText(secondaryProfile.getResolvedName(), baseProfile.getResolvedName(), fallbackName))
                .setDescription(firstNonBlankText(secondaryProfile.getDescription(), baseProfile.getDescription()))
                .setSummary(firstNonBlankText(secondaryProfile.getSummary(), baseProfile.getSummary()))
                .setSummaryParagraphs(pickParagraphs(secondaryProfile.getSummaryParagraphs(), baseProfile.getSummaryParagraphs()))
                .setEducationSummary(firstNonBlankText(secondaryProfile.getEducationSummary(), baseProfile.getEducationSummary()))
                .setEducationSummaryParagraphs(pickParagraphs(secondaryProfile.getEducationSummaryParagraphs(), baseProfile.getEducationSummaryParagraphs()))
                .setFamilyBackgroundSummary(firstNonBlankText(secondaryProfile.getFamilyBackgroundSummary(), baseProfile.getFamilyBackgroundSummary()))
                .setFamilyBackgroundSummaryParagraphs(pickParagraphs(secondaryProfile.getFamilyBackgroundSummaryParagraphs(), baseProfile.getFamilyBackgroundSummaryParagraphs()))
                .setCareerSummary(firstNonBlankText(secondaryProfile.getCareerSummary(), baseProfile.getCareerSummary()))
                .setCareerSummaryParagraphs(pickParagraphs(secondaryProfile.getCareerSummaryParagraphs(), baseProfile.getCareerSummaryParagraphs()))
                .setChinaRelatedStatementsSummary(firstNonBlankText(
                        secondaryProfile.getChinaRelatedStatementsSummary(), baseProfile.getChinaRelatedStatementsSummary()))
                .setChinaRelatedStatementsSummaryParagraphs(pickParagraphs(
                        secondaryProfile.getChinaRelatedStatementsSummaryParagraphs(), baseProfile.getChinaRelatedStatementsSummaryParagraphs()))
                .setPoliticalTendencySummary(firstNonBlankText(
                        secondaryProfile.getPoliticalTendencySummary(), baseProfile.getPoliticalTendencySummary()))
                .setPoliticalTendencySummaryParagraphs(pickParagraphs(
                        secondaryProfile.getPoliticalTendencySummaryParagraphs(), baseProfile.getPoliticalTendencySummaryParagraphs()))
                .setContactInformationSummary(firstNonBlankText(
                        secondaryProfile.getContactInformationSummary(), baseProfile.getContactInformationSummary()))
                .setContactInformationSummaryParagraphs(pickParagraphs(
                        secondaryProfile.getContactInformationSummaryParagraphs(), baseProfile.getContactInformationSummaryParagraphs()))
                .setFamilyMemberSituationSummary(firstNonBlankText(
                        secondaryProfile.getFamilyMemberSituationSummary(), baseProfile.getFamilyMemberSituationSummary()))
                .setFamilyMemberSituationSummaryParagraphs(pickParagraphs(
                        secondaryProfile.getFamilyMemberSituationSummaryParagraphs(), baseProfile.getFamilyMemberSituationSummaryParagraphs()))
                .setMisconductSummary(firstNonBlankText(
                        secondaryProfile.getMisconductSummary(), baseProfile.getMisconductSummary()))
                .setMisconductSummaryParagraphs(pickParagraphs(
                        secondaryProfile.getMisconductSummaryParagraphs(), baseProfile.getMisconductSummaryParagraphs()))
                .setWikipedia(firstNonBlankText(secondaryProfile.getWikipedia(), baseProfile.getWikipedia()))
                .setOfficialWebsite(firstNonBlankText(secondaryProfile.getOfficialWebsite(), baseProfile.getOfficialWebsite()))
                .setTags(pickList(secondaryProfile.getTags(), baseProfile.getTags()))
                .setKeyFacts(pickList(secondaryProfile.getKeyFacts(), baseProfile.getKeyFacts()))
                .setBasicInfo(mergeBasicInfo(baseProfile.getBasicInfo(), secondaryProfile.getBasicInfo()))
                .setEvidenceUrls(mergeEvidenceUrls(baseProfile.getEvidenceUrls(), secondaryProfile.getEvidenceUrls()));
    }

    private com.example.face2info.entity.internal.PersonBasicInfo mergeBasicInfo(com.example.face2info.entity.internal.PersonBasicInfo baseInfo,
                                                                                  com.example.face2info.entity.internal.PersonBasicInfo secondaryInfo) {
        if (baseInfo == null && secondaryInfo == null) {
            return null;
        }
        if (baseInfo == null) {
            return secondaryInfo;
        }
        if (secondaryInfo == null) {
            return baseInfo;
        }
        return new com.example.face2info.entity.internal.PersonBasicInfo()
                .setBirthDate(firstNonBlankText(secondaryInfo.getBirthDate(), baseInfo.getBirthDate()))
                .setEducation(pickList(secondaryInfo.getEducation(), baseInfo.getEducation()))
                .setOccupations(pickList(secondaryInfo.getOccupations(), baseInfo.getOccupations()))
                .setBiographies(pickList(secondaryInfo.getBiographies(), baseInfo.getBiographies()));
    }

    private List<String> mergeEvidenceUrls(List<String> primary, List<String> secondary) {
        Set<String> merged = new LinkedHashSet<>();
        if (secondary != null) {
            secondary.stream().filter(StringUtils::hasText).forEach(merged::add);
        }
        if (primary != null) {
            primary.stream().filter(StringUtils::hasText).forEach(merged::add);
        }
        return new ArrayList<>(merged);
    }

    private List<String> pickList(List<String> preferred, List<String> fallback) {
        List<String> preferredCleaned = preferred == null ? List.of() : preferred.stream().filter(StringUtils::hasText).toList();
        if (!preferredCleaned.isEmpty()) {
            return preferredCleaned;
        }
        return fallback == null ? List.of() : fallback.stream().filter(StringUtils::hasText).toList();
    }

    private List<ParagraphSummaryItem> pickParagraphs(List<ParagraphSummaryItem> preferred, List<ParagraphSummaryItem> fallback) {
        if (preferred != null && !preferred.isEmpty()) {
            return preferred;
        }
        return fallback == null ? List.of() : fallback;
    }

    private void enrichProfileParagraphSources(ResolvedPersonProfile profile, List<PageSummary> pageSummaries) {
        if (profile == null) {
            return;
        }
        Map<String, PageSummary> summariesByUrl = new LinkedHashMap<>();
        if (pageSummaries != null) {
            for (PageSummary pageSummary : pageSummaries) {
                if (pageSummary != null && StringUtils.hasText(pageSummary.getSourceUrl())) {
                    summariesByUrl.putIfAbsent(pageSummary.getSourceUrl(), pageSummary);
                }
            }
        }
        profile.setSummaryParagraphs(enrichParagraphs(profile.getSummaryParagraphs(), summariesByUrl));
        profile.setEducationSummaryParagraphs(enrichParagraphs(profile.getEducationSummaryParagraphs(), summariesByUrl));
        profile.setFamilyBackgroundSummaryParagraphs(enrichParagraphs(profile.getFamilyBackgroundSummaryParagraphs(), summariesByUrl));
        profile.setCareerSummaryParagraphs(enrichParagraphs(profile.getCareerSummaryParagraphs(), summariesByUrl));
        profile.setChinaRelatedStatementsSummaryParagraphs(enrichParagraphs(profile.getChinaRelatedStatementsSummaryParagraphs(), summariesByUrl));
        profile.setPoliticalTendencySummaryParagraphs(enrichParagraphs(profile.getPoliticalTendencySummaryParagraphs(), summariesByUrl));
        profile.setContactInformationSummaryParagraphs(enrichParagraphs(profile.getContactInformationSummaryParagraphs(), summariesByUrl));
        profile.setFamilyMemberSituationSummaryParagraphs(enrichParagraphs(profile.getFamilyMemberSituationSummaryParagraphs(), summariesByUrl));
        profile.setMisconductSummaryParagraphs(enrichParagraphs(profile.getMisconductSummaryParagraphs(), summariesByUrl));
    }

    private List<ParagraphSummaryItem> enrichParagraphs(List<ParagraphSummaryItem> paragraphs, Map<String, PageSummary> summariesByUrl) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return List.of();
        }
        List<ParagraphSummaryItem> enriched = new ArrayList<>();
        for (ParagraphSummaryItem paragraph : paragraphs) {
            if (paragraph == null) {
                continue;
            }
            List<ParagraphSource> directSources = paragraph.getSources();
            if (directSources == null || directSources.isEmpty()) {
                directSources = new ArrayList<>();
                if (paragraph.getSourceUrls() != null) {
                    for (String sourceUrl : paragraph.getSourceUrls()) {
                        if (!StringUtils.hasText(sourceUrl)) {
                            continue;
                        }
                        PageSummary pageSummary = summariesByUrl == null ? null : summariesByUrl.get(sourceUrl);
                        directSources.add(new ParagraphSource()
                                .setTitle(pageSummary != null && StringUtils.hasText(pageSummary.getTitle()) ? pageSummary.getTitle() : sourceUrl)
                                .setUrl(sourceUrl)
                                .setSource(extractHostLabel(sourceUrl)));
                    }
                }
            }
            enriched.add(new ParagraphSummaryItem()
                    .setText(paragraph.getText())
                    .setSourceUrls(paragraph.getSourceUrls())
                    .setSources(directSources));
        }
        return enriched;
    }

    private String firstNonBlankText(String... values) {
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

    private String firstNonBlank(com.fasterxml.jackson.databind.JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String classifySummaryFailure(RuntimeException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("CONFIG_MISSING")) {
            return "CONFIG_MISSING";
        }
        if (message.contains("INVALID_RESPONSE")) {
            return "INVALID_RESPONSE";
        }
        if (message.contains("EMPTY_RESPONSE")) {
            return "EMPTY_RESPONSE";
        }
        if (message.toLowerCase().contains("timeout")) {
            return "TIMEOUT";
        }
        return "HTTP_ERROR";
    }

    private List<String> selectTopUrls(List<WebEvidence> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return List.of();
        }
        int maxPageReads = Math.max(1, properties.getApi().getJina().getMaxPageReads());
        Set<String> urls = new LinkedHashSet<>();
        evidences.stream()
                .filter(item -> StringUtils.hasText(item.getUrl()))
                // 在读正文之前先过滤明显非文章类链接，避免浪费 Jina 和 LLM 配额。
                .filter(item -> !shouldSkipSummaryUrl(item.getUrl()))
                .forEach(item -> {
                    if (urls.size() < maxPageReads) {
                        urls.add(item.getUrl());
                    }
                });
        return new ArrayList<>(urls);
    }

    private List<PageContent> buildFallbackPages(List<WebEvidence> evidences, List<String> selectedUrls) {
        if (evidences == null || evidences.isEmpty() || selectedUrls == null || selectedUrls.isEmpty()) {
            return List.of();
        }
        Set<String> selected = new LinkedHashSet<>(selectedUrls);
        List<PageContent> pages = new ArrayList<>();
        for (WebEvidence evidence : evidences) {
            if (evidence == null || !selected.contains(evidence.getUrl())) {
                continue;
            }
            if (shouldSkipSummaryUrl(evidence.getUrl())) {
                continue;
            }
            String content = buildFallbackContent(evidence);
            if (!StringUtils.hasText(content)) {
                continue;
            }
            pages.add(new PageContent()
                    .setUrl(evidence.getUrl())
                    .setTitle(evidence.getTitle())
                    .setContent(content)
                    .setSourceEngine(StringUtils.hasText(evidence.getSourceEngine()) ? evidence.getSourceEngine() : "evidence"));
        }
        return pages;
    }

    private String buildFallbackContent(WebEvidence evidence) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(evidence.getSnippet())) {
            parts.add(evidence.getSnippet().trim());
        }
        if (StringUtils.hasText(evidence.getSource())) {
            parts.add(evidence.getSource().trim());
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("\n", parts);
    }

    private boolean shouldSkipSummaryUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return false;
            }
            String normalizedHost = host.toLowerCase();
            // 这里先按域名黑名单拦截主流视频平台；后续若接入更多来源，再扩展规则即可。
            return VIDEO_PLATFORM_HOSTS.stream()
                    .anyMatch(platform -> normalizedHost.equals(platform) || normalizedHost.endsWith("." + platform));
        } catch (IllegalArgumentException ex) {
            log.debug("url parse failed while checking summary url url={} error={}", url, ex.getMessage());
            return false;
        }
    }

    private String resolveNameOrFallback(ResolvedPersonProfile profile, RecognitionEvidence evidence) {
        if (profile != null && StringUtils.hasText(profile.getResolvedName())) {
            return profile.getResolvedName();
        }
        return firstSeedQuery(evidence);
    }

    private String firstSeedQuery(RecognitionEvidence evidence) {
        if (evidence == null || evidence.getSeedQueries().isEmpty()) {
            return null;
        }
        return evidence.getSeedQueries().get(0);
    }

    private record EnrichedProfile(ResolvedPersonProfile profile, String imageUrl) {
    }

    private List<SocialAccount> collectSocialAccounts(String name) {
        log.info("社交账号聚合跳过 resolvedName={} 原因=功能开发中", name);
        return List.of(new SocialAccount()
                .setPlatform(SOCIAL_PLACEHOLDER_PLATFORM)
                .setUrl(SOCIAL_PLACEHOLDER_URL)
                .setUsername(SOCIAL_PLACEHOLDER_USERNAME));
    }

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String appendSuffix(String content, String suffix) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        if (content.endsWith(suffix)) {
            return content;
        }
        return content + suffix;
    }

    private PageSummary summarizePageWithRouting(String fallbackName, PageContent page) {
        boolean preferDeepSeek = shouldUseDeepSeekForPage(page);
        if (preferDeepSeek && deepSeekSummaryGenerationClient != null) {
            try {
                return deepSeekSummaryGenerationClient.summarizePage(fallbackName, page);
            } catch (RuntimeException deepSeekEx) {
                log.warn("页面摘要DeepSeek失败 fallbackName={} url={} category={} error={}",
                        fallbackName,
                        page == null ? null : page.getUrl(),
                        classifySummaryFailure(deepSeekEx),
                        deepSeekEx.getMessage(),
                        deepSeekEx);
                return summaryGenerationClient.summarizePage(fallbackName, page);
            }
        }
        try {
            return summaryGenerationClient.summarizePage(fallbackName, page);
        } catch (RuntimeException kimiEx) {
            if (deepSeekSummaryGenerationClient == null) {
                throw kimiEx;
            }
            log.warn("页面摘要Kimi失败 fallbackName={} url={} category={} error={}",
                    fallbackName,
                    page == null ? null : page.getUrl(),
                    classifySummaryFailure(kimiEx),
                    kimiEx.getMessage(),
                    kimiEx);
            return deepSeekSummaryGenerationClient.summarizePage(fallbackName, page);
        }
    }

    private boolean shouldUseDeepSeekForPage(PageContent page) {
        if (deepSeekSummaryGenerationClient == null || !properties.getApi().getSummary().isPageRoutingEnabled()) {
            return false;
        }
        String title = page == null ? null : page.getTitle();
        String content = page == null ? null : page.getContent();
        if (isStructuredPage(title, content)) {
            return false;
        }
        return !StringUtils.hasText(content)
                || content.length() >= properties.getApi().getSummary().getLongContentThreshold()
                || !isStructuredPage(title, content);
    }

    private boolean isStructuredPage(String title, String content) {
        List<String> keywords = properties.getApi().getSummary().getStructuredPageKeywords();
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        String normalized = (title == null ? "" : title) + "\n" + (content == null ? "" : content);
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String summarizeSectionWithFallback(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        if (deepSeekSummaryGenerationClient == null) {
            return cleanText(summaryGenerationClient.summarizeSectionFromPageSummaries(resolvedName, sectionType, pageSummaries));
        }
        try {
            return cleanText(deepSeekSummaryGenerationClient.summarizeSectionFromPageSummaries(resolvedName, sectionType, pageSummaries));
        } catch (RuntimeException deepSeekEx) {
            log.warn("主题摘要DeepSeek失败 resolvedName={} sectionType={} category={} error={}",
                    resolvedName, sectionType, classifySummaryFailure(deepSeekEx), deepSeekEx.getMessage(), deepSeekEx);
            try {
                return cleanText(summaryGenerationClient.summarizeSectionFromPageSummaries(resolvedName, sectionType, pageSummaries));
            } catch (RuntimeException kimiEx) {
                log.warn("主题摘要Kimi兜底失败 resolvedName={} sectionType={} category={} error={}",
                        resolvedName, sectionType, classifySummaryFailure(kimiEx), kimiEx.getMessage(), kimiEx);
                return null;
            }
        }
    }

    private ResolvedPersonProfile summarizePersonFromPageSummariesWithFallback(String fallbackName,
                                                                               List<PageSummary> pageSummaries,
                                                                               boolean failHard) {
        if (deepSeekSummaryGenerationClient == null) {
            return summaryGenerationClient.summarizePersonFromPageSummaries(fallbackName, pageSummaries);
        }
        try {
            return deepSeekSummaryGenerationClient.summarizePersonFromPageSummaries(fallbackName, pageSummaries);
        } catch (RuntimeException deepSeekEx) {
            log.error("最终画像DeepSeek失败 fallbackName={} pageSummaryCount={} category={} error={}",
                    fallbackName,
                    pageSummaries == null ? 0 : pageSummaries.size(),
                    classifySummaryFailure(deepSeekEx),
                    deepSeekEx.getMessage(),
                    deepSeekEx);
            try {
                return summaryGenerationClient.summarizePersonFromPageSummaries(fallbackName, pageSummaries);
            } catch (RuntimeException kimiEx) {
                log.error("最终画像Kimi兜底失败 fallbackName={} pageSummaryCount={} category={} error={}",
                        fallbackName,
                        pageSummaries == null ? 0 : pageSummaries.size(),
                        classifySummaryFailure(kimiEx),
                        kimiEx.getMessage(),
                        kimiEx);
                if (failHard) {
                    throw new ApiCallException("LLM_PROFILE_FAILED: " + LLM_FAILURE_MESSAGE, kimiEx);
                }
                throw kimiEx;
            }
        }
    }

    private ResolvedPersonProfile completeJudgedProfile(ResolvedPersonProfile judged,
                                                        ResolvedPersonProfile profile,
                                                        List<String> warnings) {
        if (judged == null) {
            warnings.add(JUDGEMENT_WARNING);
            return profile;
        }
        if (!StringUtils.hasText(judged.getResolvedName()) && StringUtils.hasText(profile.getResolvedName())) {
            judged.setResolvedName(profile.getResolvedName());
        }
        if ((judged.getEvidenceUrls() == null || judged.getEvidenceUrls().isEmpty()) && profile.getEvidenceUrls() != null) {
            judged.setEvidenceUrls(profile.getEvidenceUrls());
        }
        return judged;
    }

    private ResolvedPersonProfile applyComprehensiveJudgementWithSingleProvider(String fallbackName,
                                                                                List<PageSummary> pageSummaries,
                                                                                ResolvedPersonProfile profile,
                                                                                List<String> warnings) {
        try {
            ResolvedPersonProfile judged = summaryGenerationClient.applyComprehensiveJudgement(
                    fallbackName,
                    pageSummaries,
                    profile
            );
            return completeJudgedProfile(judged, profile, warnings);
        } catch (RuntimeException ex) {
            log.warn("综合判断失败 fallbackName={} pageSummaryCount={} error={}",
                    fallbackName, pageSummaries.size(), ex.getMessage(), ex);
            warnings.add(JUDGEMENT_WARNING);
            return profile;
        }
    }

    private boolean isLlmProfileFailure(RuntimeException ex) {
        return ex.getMessage() != null && ex.getMessage().contains("LLM_PROFILE_FAILED");
    }

    private List<SocialAccount> deduplicateSocialAccounts(List<SocialAccount> accounts) {
        Map<String, SocialAccount> deduplicated = new LinkedHashMap<>();
        for (SocialAccount account : accounts) {
            if (!StringUtils.hasText(account.getUrl())) {
                continue;
            }
            deduplicated.putIfAbsent(account.getPlatform(), account);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private <T> T joinTask(String label, CompletableFuture<T> future, T fallback, List<String> errors) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.error("{}任务失败 error={}", label, cause.getMessage(), cause);
            errors.add(label + "获取失败: " + cause.getMessage());
            return fallback;
        }
    }
}
