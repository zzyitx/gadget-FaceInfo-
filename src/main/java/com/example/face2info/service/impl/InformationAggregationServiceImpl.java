package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.client.MaigretClient;
import com.example.face2info.client.RealtimeTranslationClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.client.impl.DeepSeekSummaryGenerationClient;
import com.example.face2info.client.impl.NoopMaigretClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.ArticleCitation;
import com.example.face2info.entity.internal.DigitalFootprintQuery;
import com.example.face2info.entity.internal.DerivedTopicRequest;
import com.example.face2info.entity.internal.DerivedTopicType;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ParagraphSource;
import com.example.face2info.entity.internal.ParagraphSummaryItem;
import com.example.face2info.entity.internal.PersonAggregate;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import com.example.face2info.entity.internal.SearchQueryTask;
import com.example.face2info.entity.internal.SectionSummaryItem;
import com.example.face2info.entity.internal.SectionedSummary;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.internal.TopicQueryDecision;
import com.example.face2info.entity.internal.WebEvidence;
import com.example.face2info.entity.response.ImageMatch;
import com.example.face2info.entity.response.SocialAccount;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.service.DerivedTopicQueryService;
import com.example.face2info.service.DigitalFootprintQueryBuilder;
import com.example.face2info.service.InformationAggregationService;
import com.example.face2info.service.MultilingualQueryPlanningService;
import com.example.face2info.service.PrimarySearchQueryBuilder;
import com.example.face2info.service.SearchLanguageProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final List<String> SECTION_NUMBER_LABELS = List.of(
            "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"
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
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");
    private static final Pattern HANDLE_PATTERN = Pattern.compile("(?<![\\w.])@([A-Za-z0-9._-]{2,64})");
    private static final int CONTACT_INFORMATION_QUERY_LIMIT = 10;
    private static final int SOCIAL_ACCOUNT_QUERY_LIMIT = 12;
    private static final int SOCIAL_USERNAME_QUERY_LIMIT = 6;
    private static final int SECTION_TOPIC_MAX_PAGE_READS = 20;
    private static final int LOCAL_PROFILE_FALLBACK_MAX_SUMMARY_CHARS = 900;
    private static final int LOCAL_PROFILE_FALLBACK_PARAGRAPH_COUNT = 3;
    private static final int LOCAL_PROFILE_FALLBACK_PARAGRAPH_MAX_CHARS = 260;
    private static final int LOCAL_PROFILE_FALLBACK_FACT_COUNT = 8;

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
    private final SearchLanguageProfileService searchLanguageProfileService;
    private final MultilingualQueryPlanningService multilingualQueryPlanningService;
    private final DigitalFootprintQueryBuilder digitalFootprintQueryBuilder;
    private final PrimarySearchQueryBuilder primarySearchQueryBuilder;
    private final MaigretClient maigretClient;

    @Autowired
    public InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                             SerpApiClient serpApiClient,
                                             JinaReaderClient jinaReaderClient,
                                             SummaryGenerationClient summaryGenerationClient,
                                             @Nullable DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient,
                                             @Qualifier("face2InfoExecutor") ThreadPoolTaskExecutor executor,
                                             ApiProperties properties,
                                             DerivedTopicQueryService derivedTopicQueryService,
                                             SearchLanguageProfileService searchLanguageProfileService,
                                             MultilingualQueryPlanningService multilingualQueryPlanningService,
                                             DigitalFootprintQueryBuilder digitalFootprintQueryBuilder,
                                             PrimarySearchQueryBuilder primarySearchQueryBuilder,
                                             @Nullable MaigretClient maigretClient) {
        this.googleSearchClient = googleSearchClient;
        this.serpApiClient = serpApiClient;
        this.jinaReaderClient = jinaReaderClient;
        this.summaryGenerationClient = summaryGenerationClient;
        this.deepSeekSummaryGenerationClient = deepSeekSummaryGenerationClient;
        this.executor = executor;
        this.properties = properties;
        this.derivedTopicQueryService = derivedTopicQueryService;
        this.searchLanguageProfileService = searchLanguageProfileService;
        this.multilingualQueryPlanningService = multilingualQueryPlanningService;
        this.digitalFootprintQueryBuilder = digitalFootprintQueryBuilder;
        this.primarySearchQueryBuilder = primarySearchQueryBuilder;
        this.maigretClient = maigretClient == null ? new NoopMaigretClient() : maigretClient;
    }

    InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      JinaReaderClient jinaReaderClient,
                                      SummaryGenerationClient summaryGenerationClient,
                                      @Nullable DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient,
                                      ThreadPoolTaskExecutor executor,
                                      ApiProperties properties,
                                      DerivedTopicQueryService derivedTopicQueryService,
                                      SearchLanguageProfileService searchLanguageProfileService,
                                      MultilingualQueryPlanningService multilingualQueryPlanningService,
                                      DigitalFootprintQueryBuilder digitalFootprintQueryBuilder,
                                      PrimarySearchQueryBuilder primarySearchQueryBuilder) {
        this(googleSearchClient, serpApiClient, jinaReaderClient, summaryGenerationClient,
                deepSeekSummaryGenerationClient, executor, properties, derivedTopicQueryService,
                searchLanguageProfileService, multilingualQueryPlanningService, digitalFootprintQueryBuilder,
                primarySearchQueryBuilder, new NoopMaigretClient());
    }

    InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      JinaReaderClient jinaReaderClient,
                                      SummaryGenerationClient summaryGenerationClient,
                                      @Nullable DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient,
                                      ThreadPoolTaskExecutor executor) {
        this(googleSearchClient, serpApiClient, jinaReaderClient, summaryGenerationClient,
                deepSeekSummaryGenerationClient, executor, new ApiProperties(), new PassThroughDerivedTopicQueryService(),
                new SearchLanguageProfileServiceImpl(summaryGenerationClient), new MultilingualQueryPlanningServiceImpl(noopTranslationClient()),
                new DigitalFootprintQueryBuilderImpl(deepSeekSummaryGenerationClient, summaryGenerationClient,
                        new SearchTemplateQueryBuilderImpl(new ApiProperties())),
                new PrimarySearchQueryBuilderImpl(deepSeekSummaryGenerationClient, summaryGenerationClient,
                        new SearchTemplateQueryBuilderImpl(new ApiProperties())));
    }

    InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      JinaReaderClient jinaReaderClient,
                                      SummaryGenerationClient summaryGenerationClient,
                                      @Nullable DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient,
                                      ThreadPoolTaskExecutor executor,
                                      ApiProperties properties) {
        this(googleSearchClient, serpApiClient, jinaReaderClient, summaryGenerationClient,
                deepSeekSummaryGenerationClient, executor, properties, new PassThroughDerivedTopicQueryService(),
                new SearchLanguageProfileServiceImpl(summaryGenerationClient), new MultilingualQueryPlanningServiceImpl(noopTranslationClient()),
                new DigitalFootprintQueryBuilderImpl(deepSeekSummaryGenerationClient, summaryGenerationClient,
                        new SearchTemplateQueryBuilderImpl(properties)),
                new PrimarySearchQueryBuilderImpl(deepSeekSummaryGenerationClient, summaryGenerationClient,
                        new SearchTemplateQueryBuilderImpl(properties)));
    }

    InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      JinaReaderClient jinaReaderClient,
                                      SummaryGenerationClient summaryGenerationClient,
                                      ThreadPoolTaskExecutor executor,
                                      ApiProperties properties) {
        this(googleSearchClient, serpApiClient, jinaReaderClient, summaryGenerationClient,
                null, executor, properties, new PassThroughDerivedTopicQueryService(),
                new SearchLanguageProfileServiceImpl(summaryGenerationClient), new MultilingualQueryPlanningServiceImpl(noopTranslationClient()),
                new DigitalFootprintQueryBuilderImpl(null, summaryGenerationClient,
                        new SearchTemplateQueryBuilderImpl(properties)),
                new PrimarySearchQueryBuilderImpl(null, summaryGenerationClient,
                        new SearchTemplateQueryBuilderImpl(properties)));
    }

    InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      JinaReaderClient jinaReaderClient,
                                      SummaryGenerationClient summaryGenerationClient,
                                      ThreadPoolTaskExecutor executor) {
        this(googleSearchClient, serpApiClient, jinaReaderClient, summaryGenerationClient,
                null, executor, new ApiProperties(), new PassThroughDerivedTopicQueryService(),
                new SearchLanguageProfileServiceImpl(summaryGenerationClient), new MultilingualQueryPlanningServiceImpl(noopTranslationClient()),
                new DigitalFootprintQueryBuilderImpl(null, summaryGenerationClient,
                        new SearchTemplateQueryBuilderImpl(new ApiProperties())),
                new PrimarySearchQueryBuilderImpl(null, summaryGenerationClient,
                        new SearchTemplateQueryBuilderImpl(new ApiProperties())));
    }

    InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      JinaReaderClient jinaReaderClient,
                                      SummaryGenerationClient summaryGenerationClient,
                                      @Nullable DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient,
                                      ThreadPoolTaskExecutor executor,
                                      ApiProperties properties,
                                      DerivedTopicQueryService derivedTopicQueryService) {
        this(googleSearchClient, serpApiClient, jinaReaderClient, summaryGenerationClient,
                deepSeekSummaryGenerationClient, executor, properties, derivedTopicQueryService,
                new SearchLanguageProfileServiceImpl(summaryGenerationClient), new MultilingualQueryPlanningServiceImpl(noopTranslationClient()),
                new DigitalFootprintQueryBuilderImpl(deepSeekSummaryGenerationClient, summaryGenerationClient,
                        new SearchTemplateQueryBuilderImpl(properties)),
                new PrimarySearchQueryBuilderImpl(deepSeekSummaryGenerationClient, summaryGenerationClient,
                        new SearchTemplateQueryBuilderImpl(properties)));
    }

    InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      JinaReaderClient jinaReaderClient,
                                      SummaryGenerationClient summaryGenerationClient,
                                      @Nullable DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient,
                                      ThreadPoolTaskExecutor executor,
                                      ApiProperties properties,
                                      MultilingualQueryPlanningService multilingualQueryPlanningService) {
        this(googleSearchClient, serpApiClient, jinaReaderClient, summaryGenerationClient,
                deepSeekSummaryGenerationClient, executor, properties, new PassThroughDerivedTopicQueryService(),
                new SearchLanguageProfileServiceImpl(summaryGenerationClient), multilingualQueryPlanningService,
                new DigitalFootprintQueryBuilderImpl(deepSeekSummaryGenerationClient, summaryGenerationClient,
                        new SearchTemplateQueryBuilderImpl(properties)),
                new PrimarySearchQueryBuilderImpl(deepSeekSummaryGenerationClient, summaryGenerationClient,
                        new SearchTemplateQueryBuilderImpl(properties)));
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
                    result.getWarnings(),
                    evidence.getVisionModelSummaries()
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
        SearchLanguageProfile languageProfile = searchLanguageProfileService.resolveProfile(resolvedName, profile);
        EnrichedProfile enrichedProfile = enrichProfileByResolvedName(
                profile,
                resolvedName,
                languageProfile,
                result.getWarnings()
        );
        profile = enrichProfileSectionsByResolvedName(profile, resolvedName, languageProfile);
        appendArticleMatchSources(profile, evidence.getArticleImageMatches());
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
        result.setPerson(buildPersonFromProfile(profile, finalResolvedName, enrichedProfile.imageUrl()));
        result.setSocialAccounts(List.of());
        return result;
    }

    ResolvedPersonProfile resolveProfileFromEvidence(List<WebEvidence> evidences, String fallbackName) {
        return resolveProfileFromEvidence(evidences, fallbackName, new ArrayList<>());
    }

    ResolvedPersonProfile resolveProfileFromEvidence(List<WebEvidence> evidences,
                                                     String fallbackName,
                                                     List<String> warnings) {
        return resolveProfileFromEvidence(evidences, fallbackName, warnings, List.of());
    }

    ResolvedPersonProfile resolveProfileFromEvidence(List<WebEvidence> evidences,
                                                     String fallbackName,
                                                     List<String> warnings,
                                                     List<PageSummary> directPageSummaries) {
        List<String> urls = selectTopUrls(evidences);
        List<PageSummary> modelPageSummaries = normalizeDirectPageSummaries(directPageSummaries);
        if (urls.isEmpty() && modelPageSummaries.isEmpty()) {
            return new ResolvedPersonProfile();
        }

        List<PageContent> pages = List.of();
        if (!urls.isEmpty()) {
            try {
                pages = jinaReaderClient.readPages(urls);
            } catch (RuntimeException ex) {
                log.warn("Jina 正文提取失败 fallbackName={} urlCount={} error={}", fallbackName, urls.size(), ex.getMessage(), ex);
            }
        }
        if (pages == null || pages.isEmpty()) {
            pages = buildFallbackPages(evidences, urls);
        }
        if (pages.isEmpty() && modelPageSummaries.isEmpty()) {
            return new ResolvedPersonProfile()
                    .setEvidenceUrls(urls);
        }

        List<PageSummary> pageSummaries = new ArrayList<>(modelPageSummaries);
        pageSummaries.addAll(collectPageSummaries(fallbackName, pages));
        if (pageSummaries.isEmpty()) {
            warnings.add(SUMMARY_WARNING);
            return new ResolvedPersonProfile()
                    .setEvidenceUrls(urls);
        }

        ResolvedPersonProfile profile;
        try {
            profile = summarizePersonFromPageSummariesWithFallback(fallbackName, pageSummaries, true);
            if (profile == null) {
                warnings.add(SUMMARY_WARNING);
                return new ResolvedPersonProfile().setEvidenceUrls(urls);
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
                    .setEvidenceUrls(urls);
        }

        ResolvedPersonProfile judgedProfile = applyComprehensiveJudgement(resolvedNameForModelCall(fallbackName, profile),
                pageSummaries, profile, warnings);
        enrichProfileParagraphSources(judgedProfile, pageSummaries);
        return judgedProfile;
    }

    private ResolvedPersonProfile applyComprehensiveJudgement(String fallbackName,
                                                              List<PageSummary> pageSummaries,
                                                              ResolvedPersonProfile profile,
                                                              List<String> warnings) {
        String effectiveName = resolvedNameForModelCall(fallbackName, profile);
        if (deepSeekSummaryGenerationClient == null) {
            return applyComprehensiveJudgementWithSingleProvider(fallbackName, pageSummaries, profile, warnings);
        }
        try {
            ResolvedPersonProfile judged = deepSeekSummaryGenerationClient.applyComprehensiveJudgement(
                    effectiveName,
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
                        effectiveName,
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
                .setArticleSources(profile.getArticleSources())
                .setTotalArticlesRead(profile.getTotalArticlesRead())
                .setFinalArticlesUsed(profile.getFinalArticlesUsed())
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
                .setArticleSources(profile.getArticleSources())
                .setEvidenceUrls(profile.getEvidenceUrls())
                .setWikipedia(profile.getWikipedia())
                .setOfficialWebsite(profile.getOfficialWebsite())
                .setBasicInfo(profile.getBasicInfo());
    }

    String summarizeSection(String resolvedName, String sectionType, String query) {
        try {
            SearchLanguageProfile languageProfile = searchLanguageProfileService.resolveProfile(
                    resolvedName,
                    new ResolvedPersonProfile().setResolvedName(resolvedName)
            );
            SectionSearchBundle bundle = collectSectionSearchBundle(resolvedName, languageProfile, null, sectionType, query);
            if (bundle.pageSummaries().isEmpty()) {
                return null;
            }
            if (isDerivedSectionedTopic(sectionType)) {
                String sectionedSummary = summarizeSectionedTopic(resolvedName, sectionType, bundle.pageSummaries());
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
            SearchLanguageProfile languageProfile = searchLanguageProfileService.resolveProfile(
                    resolvedName,
                    new ResolvedPersonProfile().setResolvedName(resolvedName)
            );
            SectionSearchBundle bundle = collectSectionSearchBundle(resolvedName, languageProfile, null, sectionType, query);
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

    @FunctionalInterface
    private interface SectionEnrichmentSupplier {

        SectionEnrichmentResult get();
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

        assignSourceIdsToPages(pages);
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
                if (pageSummary.getSourceId() == null) {
                    pageSummary.setSourceId(page.getSourceId());
                }
                pageSummaries.add(pageSummary);
            } catch (RuntimeException ex) {
                log.warn("页面摘要失败 fallbackName={} url={} category={} error={}",
                        fallbackName, page.getUrl(), classifySummaryFailure(ex), ex.getMessage(), ex);
            }
        }
        attachArticleCitations(pageSummaries);
        return pageSummaries;
    }

    private EnrichedProfile enrichProfileByResolvedName(ResolvedPersonProfile profile,
                                                        String resolvedName,
                                                        SearchLanguageProfile languageProfile,
                                                        List<String> warnings) {
        if (!shouldRunSecondarySearch(profile, resolvedName)) {
            return new EnrichedProfile(profile, null);
        }

        List<SearchQueryTask> tasks = mergeSearchTasks(
                multilingualQueryPlanningService.planSecondaryProfileQueries(languageProfile),
                buildPrimarySearchTasks(
                        primarySearchQueryBuilder.buildSecondaryProfileQueries(resolvedName, languageProfile, profile),
                        "primary_precise"
                )
        );
        List<SerpApiResponse> searchResponses = executeSearchTasks(tasks, resolvedName, "secondary_profile");
        if (searchResponses.isEmpty()) {
            return new EnrichedProfile(profile, null);
        }

        String imageUrl = extractKnowledgeGraphImageUrl(searchResponses);
        List<WebEvidence> webEvidences = extractSearchOrganicWebEvidence(searchResponses);
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

    private ResolvedPersonProfile enrichProfileSectionsByResolvedName(ResolvedPersonProfile profile,
                                                                      String resolvedName,
                                                                      SearchLanguageProfile languageProfile) {
        if (profile == null || !StringUtils.hasText(resolvedName) || !StringUtils.hasText(profile.getSummary())) {
            return profile;
        }

        CompletableFuture<SectionEnrichmentResult> familyFuture = submitSectionEnrichmentTask(
                () -> summarizeSectionEnrichment(resolvedName, languageProfile, profile, FAMILY_SECTION, resolvedName + "的家庭背景"));

        ResolvedPersonProfile enriched = copyProfile(profile);
        SectionEnrichmentResult familyResult = joinSectionEnrichmentFuture(familyFuture);
        enriched.setFamilyBackgroundSummary(firstNonBlankText(familyResult.summary(), profile.getFamilyBackgroundSummary()));
        enriched.setFamilyBackgroundSummaryParagraphs(pickParagraphs(familyResult.paragraphs(), profile.getFamilyBackgroundSummaryParagraphs()));
        return enriched;
    }

    private SectionEnrichmentResult summarizeSectionEnrichment(String resolvedName,
                                                              SearchLanguageProfile languageProfile,
                                                              @Nullable ResolvedPersonProfile profile,
                                                              String sectionType,
                                                              String fallbackQuery) {
        try {
            SectionSearchBundle bundle = collectSectionSearchBundle(resolvedName, languageProfile, profile, sectionType, fallbackQuery);
            if (bundle.pageSummaries() == null || bundle.pageSummaries().isEmpty()) {
                return SectionEnrichmentResult.empty();
            }
            String summary = null;
            if (isDerivedSectionedTopic(sectionType)) {
                summary = summarizeSectionedTopic(resolvedName, sectionType, bundle.pageSummaries());
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

    private String extractKnowledgeGraphImageUrl(List<SerpApiResponse> responses) {
        if (responses == null) {
            return null;
        }
        for (SerpApiResponse response : responses) {
            if (response == null || response.getRoot() == null) {
                continue;
            }
            String imageUrl = extractKnowledgeGraphImageUrl(response.getRoot());
            if (StringUtils.hasText(imageUrl)) {
                return imageUrl;
            }
        }
        return null;
    }

    private List<WebEvidence> extractSearchOrganicWebEvidence(com.fasterxml.jackson.databind.JsonNode root) {
        List<WebEvidence> evidences = new ArrayList<>();
        collectKnowledgeGraphEvidence(evidences, root.path("knowledgeGraph"));
        collectOrganicEvidence(evidences, root.path("organic"));
        return deduplicateWebEvidencePrioritized(evidences);
    }

    private List<WebEvidence> extractSearchOrganicWebEvidence(List<SerpApiResponse> responses) {
        List<WebEvidence> evidences = new ArrayList<>();
        if (responses == null) {
            return evidences;
        }
        for (SerpApiResponse response : responses) {
            if (response == null || response.getRoot() == null) {
                continue;
            }
            evidences.addAll(extractSearchOrganicWebEvidence(response.getRoot()));
        }
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

    // 派生主题只执行配置化基础检索，避免模型扩展词继续放大噪声。
    private SectionSearchBundle collectSectionSearchBundle(String resolvedName,
                                                           SearchLanguageProfile languageProfile,
                                                           @Nullable ResolvedPersonProfile profile,
                                                           String sectionType,
                                                           String fallbackQuery) {
        List<String> baseQueries = mergeDistinctQueries(
                buildBaseQueries(resolvedName, sectionType),
                primarySearchQueryBuilder.buildSectionQueries(resolvedName, languageProfile, profile, sectionType)
        );
        if (baseQueries.isEmpty() && StringUtils.hasText(fallbackQuery)) {
            baseQueries = List.of(extractFallbackTerm(languageProfile, resolvedName, fallbackQuery));
        }
        List<SearchQueryTask> firstRoundTasks = multilingualQueryPlanningService.planSectionQueries(
                languageProfile,
                sectionType,
                extractQueryTerms(languageProfile, resolvedName, baseQueries)
        );
        Set<String> seenUrls = new LinkedHashSet<>();
        SectionTopicReadBudget readBudget = new SectionTopicReadBudget(SECTION_TOPIC_MAX_PAGE_READS);
        List<PageSummary> firstRound = collectPageSummariesForTasks(
                resolvedName,
                sectionType,
                firstRoundTasks,
                seenUrls,
                readBudget
        );
        if (CONTACT_INFORMATION_SECTION.equals(sectionType)) {
            firstRound = mergePageSummaries(
                    firstRound,
                    collectPageSummariesForQueries(
                            resolvedName,
                            sectionType,
                            digitalFootprintQueryBuilder.build(resolvedName, languageProfile).stream()
                                    .filter(this::isContactInformationQuery)
                                    .sorted(Comparator.comparing(query -> query.getPriority() == null ? Integer.MAX_VALUE : query.getPriority()))
                                    .limit(CONTACT_INFORMATION_QUERY_LIMIT)
                                    .map(DigitalFootprintQuery::getQueryText)
                                    .toList(),
                            seenUrls,
                            readBudget
                    )
            );
        }
        return new SectionSearchBundle(firstRound);
    }

    private List<String> buildBaseQueries(String resolvedName, String sectionType) {
        if (!StringUtils.hasText(resolvedName) || !StringUtils.hasText(sectionType)) {
            return List.of();
        }
        // 关键逻辑：本地 application.yml 若遗漏拆分模板，仍需回退到内建基础查询词，
        // 否则派生主题会重新退回单条长 query，直接缩小搜索召回范围。
        List<String> templates = properties.getSearch().resolveQueryTemplates(sectionType);
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }
        return templates.stream()
                .map(template -> template == null ? null : template
                        .replace("{name}", resolvedName)
                        .replace("{native_name}", resolvedName)
                        .replace("{english_name}", resolvedName))
                .filter(template -> template == null || !template.matches(".*\\{[a-zA-Z_]+}.*"))
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

    private List<PageSummary> collectPageSummariesForQueries(String resolvedName,
                                                             String sectionType,
                                                             List<String> queries,
                                                             Set<String> seenUrls,
                                                             SectionTopicReadBudget readBudget) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }
        List<PageSummary> collected = new ArrayList<>();
        for (String query : queries) {
            if (readBudget != null && readBudget.isExhausted()) {
                break;
            }
            try {
                String finalQuery = resolveSectionQuery(resolvedName, sectionType, query);
                collected.addAll(collectPageSummariesFromSearchResponse(
                        resolvedName,
                        sectionType,
                        googleSearchClient.googleSearch(finalQuery),
                        seenUrls,
                        readBudget
                ));
            } catch (RuntimeException ex) {
                log.warn("section query failed resolvedName={} sectionType={} query={} error={}",
                        resolvedName, sectionType, query, ex.getMessage(), ex);
            }
        }
        return collected;
    }

    private List<PageSummary> collectPageSummariesForTasks(String resolvedName,
                                                           String sectionType,
                                                           List<SearchQueryTask> tasks,
                                                           Set<String> seenUrls,
                                                           SectionTopicReadBudget readBudget) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<PageSummary> collected = new ArrayList<>();
        for (SerpApiResponse response : executeSearchTasks(tasks, resolvedName, sectionType)) {
            if (readBudget != null && readBudget.isExhausted()) {
                break;
            }
            collected.addAll(collectPageSummariesFromSearchResponse(resolvedName, sectionType, response, seenUrls, readBudget));
        }
        return collected;
    }

    private List<SerpApiResponse> executeSearchTasks(List<SearchQueryTask> tasks, String resolvedName, String sectionType) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        // 线程池工作线程内如果再次并发提交同池任务，容易在高峰期形成自我阻塞，这里直接串行回退保证链路可完成。
        if (isExecutorWorkerThread()) {
            List<SerpApiResponse> responses = new ArrayList<>();
            for (SearchQueryTask task : tasks) {
                SerpApiResponse response = runSearchTaskSync(task, resolvedName, sectionType);
                if (response != null && response.getRoot() != null) {
                    responses.add(response);
                }
            }
            return responses;
        }
        List<CompletableFuture<SerpApiResponse>> futures = new ArrayList<>();
        for (SearchQueryTask task : tasks) {
            try {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        String finalQuery = resolveSectionQuery(resolvedName, sectionType, task.getQueryText());
                        log.info("section search round={} language={} query=\"{}\" resolvedName={} sectionType={}",
                                task.getSourceReason(), task.getLanguageCode(), finalQuery, resolvedName, sectionType);
                        return googleSearchClient.googleSearch(finalQuery);
                    } catch (RuntimeException ex) {
                        log.warn("multilingual search failed round={} language={} query={} error={}",
                                task.getSourceReason(), task.getLanguageCode(), task.getQueryText(), ex.getMessage(), ex);
                        return null;
                    }
                }, executor));
            } catch (TaskRejectedException ex) {
                log.warn("multilingual search executor saturated, fallback to sync round={} language={} query={} error={}",
                        task.getSourceReason(), task.getLanguageCode(), task.getQueryText(), ex.getMessage());
                futures.add(CompletableFuture.completedFuture(runSearchTaskSync(task, resolvedName, sectionType)));
            }
        }

        List<SerpApiResponse> responses = new ArrayList<>();
        for (CompletableFuture<SerpApiResponse> future : futures) {
            try {
                SerpApiResponse response = future.join();
                if (response != null && response.getRoot() != null) {
                    responses.add(response);
                }
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                log.warn("multilingual search future failed error={}", cause.getMessage(), cause);
            }
        }
        return responses;
    }

    private List<SearchQueryTask> buildPrimarySearchTasks(List<String> queries, String sourceReason) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }
        List<SearchQueryTask> tasks = new ArrayList<>();
        int priority = 1;
        for (String query : queries) {
            if (!StringUtils.hasText(query)) {
                continue;
            }
            tasks.add(new SearchQueryTask()
                    .setQueryText(query.trim())
                    .setLanguageCode(detectQueryLanguage(query))
                    .setQueryKind("primary_precise")
                    .setSourceReason(sourceReason)
                    .setPriority(priority++));
        }
        return tasks;
    }

    private List<SearchQueryTask> mergeSearchTasks(List<SearchQueryTask> first, List<SearchQueryTask> second) {
        LinkedHashMap<String, SearchQueryTask> deduplicated = new LinkedHashMap<>();
        int priority = 1;
        List<SearchQueryTask> merged = new ArrayList<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        for (SearchQueryTask task : merged) {
            if (task == null || !StringUtils.hasText(task.getQueryText())) {
                continue;
            }
            String key = task.getQueryText().trim().toLowerCase(Locale.ROOT);
            if (deduplicated.containsKey(key)) {
                continue;
            }
            deduplicated.put(key, new SearchQueryTask()
                    .setQueryText(task.getQueryText().trim())
                    .setLanguageCode(task.getLanguageCode())
                    .setQueryKind(task.getQueryKind())
                    .setSourceReason(task.getSourceReason())
                    .setPriority(priority++));
        }
        return new ArrayList<>(deduplicated.values());
    }

    private List<String> mergeDistinctQueries(List<String> first, List<String> second) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (first != null) {
            first.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(merged::add);
        }
        if (second != null) {
            second.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(merged::add);
        }
        return new ArrayList<>(merged);
    }

    private String detectQueryLanguage(String query) {
        if (!StringUtils.hasText(query)) {
            return "unknown";
        }
        boolean hasHan = query.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
        boolean hasAsciiLetter = query.chars().anyMatch(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'));
        if (hasHan && hasAsciiLetter) {
            return "mixed";
        }
        if (hasHan) {
            return "zh";
        }
        if (hasAsciiLetter) {
            return "en";
        }
        return "unknown";
    }

    private boolean isExecutorWorkerThread() {
        String threadNamePrefix = executor.getThreadNamePrefix();
        String currentThreadName = Thread.currentThread().getName();
        return StringUtils.hasText(threadNamePrefix) && StringUtils.hasText(currentThreadName)
                && currentThreadName.startsWith(threadNamePrefix);
    }

    private SerpApiResponse runSearchTaskSync(SearchQueryTask task, String resolvedName, String sectionType) {
        try {
            String finalQuery = resolveSectionQuery(resolvedName, sectionType, task.getQueryText());
            log.info("section search round={} language={} query=\"{}\" resolvedName={} sectionType={}",
                    task.getSourceReason(), task.getLanguageCode(), finalQuery, resolvedName, sectionType);
            return googleSearchClient.googleSearch(finalQuery);
        } catch (RuntimeException ex) {
            log.warn("multilingual search failed in sync fallback round={} language={} query={} error={}",
                    task.getSourceReason(), task.getLanguageCode(), task.getQueryText(), ex.getMessage(), ex);
            return null;
        }
    }

    private CompletableFuture<SectionEnrichmentResult> submitSectionEnrichmentTask(SectionEnrichmentSupplier supplier) {
        try {
            return CompletableFuture.supplyAsync(supplier::get, executor);
        } catch (TaskRejectedException ex) {
            // 富化摘要属于降级可接受链路，线程池打满时同步执行比直接丢弃分段结果更稳妥。
            log.warn("section enrichment executor saturated, fallback to sync error={}", ex.getMessage());
            return CompletableFuture.completedFuture(supplier.get());
        }
    }

    private List<String> extractQueryTerms(SearchLanguageProfile languageProfile, String resolvedName, List<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String query : queries) {
            String term = extractFallbackTerm(languageProfile, resolvedName, query);
            if (StringUtils.hasText(term)) {
                if (isSameAsResolvedName(term, resolvedName, languageProfile)) {
                    continue;
                }
                terms.add(term);
            }
        }
        return terms;
    }

    private boolean isSameAsResolvedName(String term, String resolvedName, SearchLanguageProfile languageProfile) {
        if (!StringUtils.hasText(term)) {
            return true;
        }
        String normalized = term.trim();
        if (StringUtils.hasText(resolvedName) && normalized.equalsIgnoreCase(resolvedName.trim())) {
            return true;
        }
        if (languageProfile != null && languageProfile.getLocalizedNames() != null) {
            for (String name : languageProfile.getLocalizedNames().values()) {
                if (StringUtils.hasText(name) && normalized.equalsIgnoreCase(name.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String extractFallbackTerm(SearchLanguageProfile languageProfile, String resolvedName, String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        String normalized = query.trim();
        List<String> candidates = new ArrayList<>();
        if (languageProfile != null && languageProfile.getLocalizedNames() != null) {
            candidates.addAll(languageProfile.getLocalizedNames().values());
        }
        if (StringUtils.hasText(resolvedName)) {
            candidates.add(resolvedName);
            candidates.add(resolvedName.replaceAll("（.*?）", "").trim());
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate) && normalized.startsWith(candidate.trim())) {
                normalized = normalized.substring(candidate.trim().length()).trim();
                break;
            }
        }
        normalized = normalized
                .replaceFirst("^[的\\s:：,，;；/\\\\-]+", "")
                .replaceFirst("^['’]s\\s+", "")
                .trim();
        return StringUtils.hasText(normalized) ? normalized : query.trim();
    }

    private List<PageSummary> collectPageSummariesFromSearchResponse(String resolvedName,
                                                                     String sectionType,
                                                                     SerpApiResponse searchResponse,
                                                                     Set<String> seenUrls,
                                                                     SectionTopicReadBudget readBudget) {
        try {
            if (searchResponse == null || searchResponse.getRoot() == null) {
                return List.of();
            }
            if (readBudget != null && readBudget.isExhausted()) {
                return List.of();
            }
            List<WebEvidence> evidences = extractSearchOrganicWebEvidence(searchResponse.getRoot());
            if (evidences == null || evidences.isEmpty()) {
                return List.of();
            }
            List<WebEvidence> filtered = evidences.stream()
                    .filter(item -> item != null && StringUtils.hasText(item.getUrl()))
                    .filter(item -> !seenUrls.contains(normalizeDedupUrl(item.getUrl())))
                    .toList();
            if (filtered.isEmpty()) {
                return List.of();
            }
            int maxPageReads = readBudget == null ? Math.max(1, properties.getApi().getJina().getMaxPageReads()) : readBudget.remaining();
            List<String> urls = selectTopUrls(filtered, maxPageReads);
            if (urls.isEmpty()) {
                return List.of();
            }
            if (readBudget != null) {
                readBudget.consume(urls.size());
            }
            List<PageContent> pages = List.of();
            try {
                pages = jinaReaderClient.readPages(urls);
            } catch (RuntimeException ex) {
                log.warn("section jina read failed resolvedName={} sectionType={} urlCount={} error={}",
                        resolvedName, sectionType, urls.size(), ex.getMessage(), ex);
            }
            if (pages == null || pages.isEmpty()) {
                pages = buildFallbackPages(filtered, urls);
            }
            if (pages == null || pages.isEmpty()) {
                return List.of();
            }
            List<PageSummary> summaries = collectPageSummaries(resolvedName, pages);
            List<PageSummary> deduplicated = new ArrayList<>();
            for (PageSummary summary : summaries) {
                if (summary == null || !StringUtils.hasText(summary.getSourceUrl())) {
                    continue;
                }
                String normalizedUrl = normalizeDedupUrl(summary.getSourceUrl());
                if (seenUrls.add(normalizedUrl)) {
                    deduplicated.add(summary);
                }
            }
            return deduplicated;
        } catch (RuntimeException ex) {
            log.warn("section search response processing failed resolvedName={} sectionType={} error={}",
                    resolvedName, sectionType, ex.getMessage(), ex);
            return List.of();
        }
    }

    private String normalizeDedupUrl(String url) {
        return StringUtils.hasText(url) ? url.trim() : "";
    }

    private static RealtimeTranslationClient noopTranslationClient() {
        return (query, targetLanguageCode) -> null;
    }

    private List<PageSummary> collectPageSummariesForSingleQuery(String resolvedName, String sectionType, String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        try {
            String finalQuery = resolveSectionQuery(resolvedName, sectionType, query);
            SerpApiResponse searchResponse = googleSearchClient.googleSearch(finalQuery);
            return collectPageSummariesFromSearchResponse(resolvedName, sectionType, searchResponse);
        } catch (RuntimeException ex) {
            log.warn("section query failed resolvedName={} sectionType={} query={} error={}",
                    resolvedName, sectionType, query, ex.getMessage(), ex);
            return List.of();
        }
    }

    private List<PageSummary> collectPageSummariesFromSearchResponse(String resolvedName,
                                                                     String sectionType,
                                                                     SerpApiResponse searchResponse) {
        try {
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
                log.warn("section jina read failed resolvedName={} sectionType={} urlCount={} error={}",
                        resolvedName, sectionType, urls.size(), ex.getMessage(), ex);
            }
            if (pages == null || pages.isEmpty()) {
                pages = buildFallbackPages(evidences, urls);
            }
            if (pages == null || pages.isEmpty()) {
                return List.of();
            }
            return collectPageSummaries(resolvedName, pages);
        } catch (RuntimeException ex) {
            log.warn("section search response processing failed resolvedName={} sectionType={} error={}",
                    resolvedName, sectionType, ex.getMessage(), ex);
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

    // 只按配置化分段标题输出派生主题，避免把扩展检索理由混进最终摘要。
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

    private void assignSourceIdsToPages(List<PageContent> pages) {
        if (pages == null || pages.isEmpty()) {
            return;
        }
        Map<String, Integer> idsByKey = new LinkedHashMap<>();
        int nextId = 1;
        for (PageContent page : pages) {
            if (page == null) {
                continue;
            }
            String key = articleKey(page.getUrl(), page.getTitle());
            if (!StringUtils.hasText(key)) {
                continue;
            }
            Integer sourceId = idsByKey.get(key);
            if (sourceId == null) {
                sourceId = nextId++;
                idsByKey.put(key, sourceId);
            }
            page.setSourceId(sourceId);
        }
    }

    private void attachArticleCitations(List<PageSummary> pageSummaries) {
        if (pageSummaries == null || pageSummaries.isEmpty()) {
            return;
        }
        List<ArticleCitation> articleSources = buildArticleCitations(pageSummaries);
        Map<String, Integer> idsByKey = articleSources.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(
                        item -> articleKey(item.getUrl(), item.getTitle()),
                        ArticleCitation::getId,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        for (PageSummary pageSummary : pageSummaries) {
            if (pageSummary == null) {
                continue;
            }
            if (pageSummary.getSourceId() == null) {
                pageSummary.setSourceId(idsByKey.get(articleKey(pageSummary.getSourceUrl(), pageSummary.getTitle())));
            }
            pageSummary.setArticleSources(articleSources);
        }
    }

    private List<ArticleCitation> buildArticleCitations(List<PageSummary> pageSummaries) {
        if (pageSummaries == null || pageSummaries.isEmpty()) {
            return List.of();
        }
        Map<String, ArticleCitation> deduplicated = new LinkedHashMap<>();
        int nextId = 1;
        for (PageSummary summary : pageSummaries) {
            if (summary == null) {
                continue;
            }
            String key = articleKey(summary.getSourceUrl(), summary.getTitle());
            if (!StringUtils.hasText(key) || deduplicated.containsKey(key)) {
                continue;
            }
            Integer sourceId = summary.getSourceId();
            if (sourceId == null) {
                sourceId = nextId;
            }
            nextId = Math.max(nextId, sourceId + 1);
            deduplicated.put(key, new ArticleCitation()
                    .setId(sourceId)
                    .setTitle(summary.getTitle())
                    .setUrl(summary.getSourceUrl())
                    .setSource(firstNonBlankText(summary.getSourcePlatform(), extractHostLabel(summary.getSourceUrl())))
                    .setPublishedAt(summary.getPublishedAt()));
        }
        return List.copyOf(deduplicated.values());
    }

    private String articleKey(String url, String title) {
        if (StringUtils.hasText(url)) {
            return url.trim();
        }
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        return null;
    }

    private boolean isDerivedSectionedTopic(String sectionType) {
        return !properties.getSearch().resolveDerivedSectionTitles(sectionType).isEmpty();
    }

    private String summarizeSectionedTopic(String resolvedName,
                                           String sectionType,
                                           List<PageSummary> pageSummaries) {
        try {
            if (deepSeekSummaryGenerationClient != null) {
                String deepSeekSummary = formatSectionedTopicSummary(
                        sectionType,
                        deepSeekSummaryGenerationClient.summarizeSectionedSectionFromPageSummaries(resolvedName, sectionType, pageSummaries)
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
                    summaryGenerationClient.summarizeSectionedSectionFromPageSummaries(resolvedName, sectionType, pageSummaries)
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

    private String formatSectionedTopicSummary(String sectionType,
                                               SectionedSummary sectionedSummary) {
        Map<String, String> sectionTitles = resolveNumberedSectionTitles(sectionType);
        if (sectionTitles == null || sectionTitles.isEmpty()) {
            return null;
        }
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
                .map(section -> formatSectionedTopicItem(sectionTitles.get(section), summariesBySection.get(section)))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n"));
        return StringUtils.hasText(content) ? content.trim() : null;
    }

    private String formatSectionedTopicItem(String title, SectionSummaryItem item) {
        if (!StringUtils.hasText(title) || item == null || !StringUtils.hasText(item.getSummary())) {
            return null;
        }
        return title + "\n" + item.getSummary().trim();
    }

    private Map<String, String> resolveNumberedSectionTitles(String sectionType) {
        List<String> titles = properties.getSearch().resolveDerivedSectionTitles(sectionType);
        Map<String, String> numberedTitles = new LinkedHashMap<>();
        for (int index = 0; index < titles.size(); index++) {
            String numberLabel = index < SECTION_NUMBER_LABELS.size()
                    ? SECTION_NUMBER_LABELS.get(index)
                    : String.valueOf(index + 1);
            numberedTitles.put(titles.get(index), numberLabel + "、" + titles.get(index));
        }
        return numberedTitles;
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

    // 分段摘要转为结构化段落，供前端统一渲染来源脚注。
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
                    .setSourceIds(item.getSourceIds())
                    .setSourceUrls(item.getSourceUrls())
                    .setSources(resolveParagraphSources(item.getSources(), item.getSourceUrls(), item.getSourceIds(), Map.of(), Map.of())));
        }
        return paragraphs;
    }

    private List<ParagraphSource> resolveParagraphSources(List<ParagraphSource> directSources, List<String> sourceUrls) {
        return resolveParagraphSources(directSources, sourceUrls, List.of(), Map.of(), Map.of());
    }

    private List<ParagraphSource> resolveParagraphSources(List<ParagraphSource> directSources,
                                                          List<String> sourceUrls,
                                                          List<Integer> sourceIds,
                                                          Map<String, PageSummary> summariesByUrl,
                                                          Map<Integer, ArticleCitation> citationsById) {
        Map<String, ParagraphSource> deduplicated = new LinkedHashMap<>();
        if (sourceIds != null) {
            for (Integer sourceId : sourceIds) {
                if (sourceId == null) {
                    continue;
                }
                ArticleCitation citation = citationsById == null ? null : citationsById.get(sourceId);
                if (citation == null) {
                    continue;
                }
                ParagraphSource source = new ParagraphSource();
                source.setTitle(citation.getTitle());
                source.setUrl(citation.getUrl());
                source.setSource(citation.getSource());
                source.setPublishedAt(citation.getPublishedAt());
                deduplicated.putIfAbsent(paragraphSourceKey(source), source);
            }
        }
        if (directSources != null) {
            for (ParagraphSource directSource : directSources) {
                if (directSource == null) {
                    continue;
                }
                ParagraphSource source = new ParagraphSource();
                source.setTitle(directSource.getTitle());
                source.setUrl(directSource.getUrl());
                source.setSource(directSource.getSource());
                source.setPublishedAt(directSource.getPublishedAt());
                if (StringUtils.hasText(source.getUrl()) && summariesByUrl != null) {
                    PageSummary summary = summariesByUrl.get(source.getUrl());
                    if (summary != null) {
                        source.setTitle(firstNonBlankText(source.getTitle(), summary.getTitle(), source.getUrl()));
                        source.setSource(firstNonBlankText(source.getSource(), extractHostLabel(source.getUrl())));
                    }
                }
                if (!StringUtils.hasText(source.getSource())) {
                    source.setSource(extractHostLabel(source.getUrl()));
                }
                if (!StringUtils.hasText(source.getTitle())) {
                    source.setTitle(StringUtils.hasText(source.getUrl()) ? source.getUrl() : "来源文章");
                }
                deduplicated.putIfAbsent(paragraphSourceKey(source), source);
            }
        }
        if (directSources != null && !directSources.isEmpty()) {
            // 已通过 directSources 进入 deduplicated，继续合并 sourceUrls 以补齐漏掉的 URL。
        }
        if (sourceUrls != null) {
            for (String sourceUrl : sourceUrls) {
                if (!StringUtils.hasText(sourceUrl)) {
                    continue;
                }
                PageSummary summary = summariesByUrl == null ? null : summariesByUrl.get(sourceUrl);
                ParagraphSource source = new ParagraphSource()
                        .setTitle(summary != null ? firstNonBlankText(summary.getTitle(), sourceUrl) : sourceUrl)
                        .setUrl(sourceUrl)
                        .setSource(extractHostLabel(sourceUrl));
                deduplicated.putIfAbsent(paragraphSourceKey(source), source);
            }
        }
        return new ArrayList<>(deduplicated.values());
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

    private record SectionSearchBundle(List<PageSummary> pageSummaries) {
    }

    private record SectionEnrichmentResult(String summary, List<ParagraphSummaryItem> paragraphs) {

        private static SectionEnrichmentResult empty() {
            return new SectionEnrichmentResult(null, List.of());
        }
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
                resolvedNameForModelCall(resolvedName, secondaryProfile),
                pageSummaries,
                secondaryProfile,
                warnings
        );
        return mergeProfiles(baseProfile, judgedSecondaryProfile);
    }

    private ResolvedPersonProfile mergeProfiles(ResolvedPersonProfile baseProfile,
                                                ResolvedPersonProfile secondaryProfile) {
        if (baseProfile == null) {
            return secondaryProfile == null ? new ResolvedPersonProfile() : secondaryProfile;
        }
        if (secondaryProfile == null) {
            return baseProfile;
        }

        return new ResolvedPersonProfile()
                .setResolvedName(firstNonBlankText(baseProfile.getResolvedName(), secondaryProfile.getResolvedName()))
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
                .setArticleSources(pickArticleSources(secondaryProfile.getArticleSources(), baseProfile.getArticleSources()))
                .setTotalArticlesRead(firstNonNull(secondaryProfile.getTotalArticlesRead(), baseProfile.getTotalArticlesRead()))
                .setFinalArticlesUsed(firstNonNull(secondaryProfile.getFinalArticlesUsed(), baseProfile.getFinalArticlesUsed()))
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

    private List<ArticleCitation> pickArticleSources(List<ArticleCitation> preferred, List<ArticleCitation> fallback) {
        Map<String, ArticleCitation> merged = new LinkedHashMap<>();
        mergeArticleSourcesInto(merged, fallback);
        mergeArticleSourcesInto(merged, preferred);
        if (!merged.isEmpty()) {
            return new ArrayList<>(merged.values());
        }
        return List.of();
    }

    private void enrichProfileParagraphSources(ResolvedPersonProfile profile, List<PageSummary> pageSummaries) {
        if (profile == null) {
            return;
        }
        List<ArticleCitation> articleSources = pickArticleSources(profile.getArticleSources(), buildArticleCitations(pageSummaries));
        Map<String, PageSummary> summariesByUrl = new LinkedHashMap<>();
        if (pageSummaries != null) {
            for (PageSummary pageSummary : pageSummaries) {
                if (pageSummary != null && StringUtils.hasText(pageSummary.getSourceUrl())) {
                    summariesByUrl.putIfAbsent(pageSummary.getSourceUrl(), pageSummary);
                }
            }
        }
        profile.setArticleSources(articleSources);
        profile.setTotalArticlesRead(pageSummaries == null ? 0 : pageSummaries.size());
        profile.setFinalArticlesUsed(countReferencedArticles(profile));
        profile.setSummaryParagraphs(enrichParagraphs(profile.getSummaryParagraphs(), summariesByUrl, articleSources));
        profile.setEducationSummaryParagraphs(enrichParagraphs(profile.getEducationSummaryParagraphs(), summariesByUrl, articleSources));
        profile.setFamilyBackgroundSummaryParagraphs(enrichParagraphs(profile.getFamilyBackgroundSummaryParagraphs(), summariesByUrl, articleSources));
        profile.setCareerSummaryParagraphs(enrichParagraphs(profile.getCareerSummaryParagraphs(), summariesByUrl, articleSources));
        profile.setChinaRelatedStatementsSummaryParagraphs(enrichParagraphs(profile.getChinaRelatedStatementsSummaryParagraphs(), summariesByUrl, articleSources));
        profile.setPoliticalTendencySummaryParagraphs(enrichParagraphs(profile.getPoliticalTendencySummaryParagraphs(), summariesByUrl, articleSources));
        profile.setContactInformationSummaryParagraphs(enrichParagraphs(profile.getContactInformationSummaryParagraphs(), summariesByUrl, articleSources));
        profile.setFamilyMemberSituationSummaryParagraphs(enrichParagraphs(profile.getFamilyMemberSituationSummaryParagraphs(), summariesByUrl, articleSources));
        profile.setMisconductSummaryParagraphs(enrichParagraphs(profile.getMisconductSummaryParagraphs(), summariesByUrl, articleSources));
    }

    private List<ParagraphSummaryItem> enrichParagraphs(List<ParagraphSummaryItem> paragraphs,
                                                        Map<String, PageSummary> summariesByUrl,
                                                        List<ArticleCitation> articleSources) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return List.of();
        }
        Map<Integer, ArticleCitation> citationsById = new LinkedHashMap<>();
        Map<String, ArticleCitation> citationsByUrl = new LinkedHashMap<>();
        if (articleSources != null) {
            for (ArticleCitation articleSource : articleSources) {
                if (articleSource == null) {
                    continue;
                }
                if (articleSource.getId() != null) {
                    citationsById.putIfAbsent(articleSource.getId(), articleSource);
                }
                if (StringUtils.hasText(articleSource.getUrl())) {
                    citationsByUrl.putIfAbsent(articleSource.getUrl(), articleSource);
                }
            }
        }
        List<ParagraphSummaryItem> enriched = new ArrayList<>();
        for (ParagraphSummaryItem paragraph : paragraphs) {
            if (paragraph == null) {
                continue;
            }
            List<Integer> sourceIds = normalizeParagraphSourceIds(paragraph, citationsByUrl);
            if (sourceIds.isEmpty()) {
                sourceIds = extractCitationIds(paragraph.getText());
            }
            List<ParagraphSource> directSources = resolveParagraphSources(
                    paragraph.getSources(),
                    paragraph.getSourceUrls(),
                    sourceIds,
                    summariesByUrl,
                    citationsById
            );
            enriched.add(new ParagraphSummaryItem()
                    .setText(paragraph.getText())
                    .setSourceIds(sourceIds)
                    .setSourceUrls(paragraph.getSourceUrls())
                    .setSources(directSources));
        }
        return enriched;
    }

    private void appendArticleMatchSources(ResolvedPersonProfile profile, List<ImageMatch> articleImageMatches) {
        if (profile == null || articleImageMatches == null || articleImageMatches.isEmpty()) {
            return;
        }
        Map<String, ArticleCitation> merged = new LinkedHashMap<>();
        mergeArticleSourcesInto(merged, profile.getArticleSources());
        int nextId = nextArticleSourceId(merged.values());
        for (ImageMatch articleImageMatch : articleImageMatches) {
            if (articleImageMatch == null) {
                continue;
            }
            String key = articleKey(articleImageMatch.getLink(), articleImageMatch.getTitle());
            if (!StringUtils.hasText(key) || merged.containsKey(key)) {
                continue;
            }
            merged.put(key, new ArticleCitation()
                    .setId(nextId++)
                    .setTitle(firstNonBlankText(articleImageMatch.getTitle(), articleImageMatch.getLink(), "来源文章"))
                    .setUrl(articleImageMatch.getLink())
                    .setSource(firstNonBlankText(articleImageMatch.getSource(), extractHostLabel(articleImageMatch.getLink()))));
        }
        profile.setArticleSources(new ArrayList<>(merged.values()));
        if (profile.getFinalArticlesUsed() == null) {
            profile.setFinalArticlesUsed(countReferencedArticles(profile));
        }
    }

    private int nextArticleSourceId(Iterable<ArticleCitation> articleSources) {
        int nextId = 1;
        if (articleSources == null) {
            return nextId;
        }
        for (ArticleCitation articleSource : articleSources) {
            if (articleSource == null || articleSource.getId() == null) {
                continue;
            }
            nextId = Math.max(nextId, articleSource.getId() + 1);
        }
        return nextId;
    }

    private int countReferencedArticles(ResolvedPersonProfile profile) {
        if (profile == null) {
            return 0;
        }
        Set<Integer> referencedIds = new LinkedHashSet<>();
        for (List<ParagraphSummaryItem> paragraphs : allProfileParagraphGroups(profile)) {
            if (paragraphs == null) {
                continue;
            }
            for (ParagraphSummaryItem paragraph : paragraphs) {
                if (paragraph == null) {
                    continue;
                }
                if (paragraph.getSourceIds() != null) {
                    paragraph.getSourceIds().stream()
                            .filter(Objects::nonNull)
                            .filter(id -> id > 0)
                            .forEach(referencedIds::add);
                }
                extractCitationIds(paragraph.getText()).stream()
                        .filter(Objects::nonNull)
                        .filter(id -> id > 0)
                        .forEach(referencedIds::add);
            }
        }
        return referencedIds.size();
    }

    private List<List<ParagraphSummaryItem>> allProfileParagraphGroups(ResolvedPersonProfile profile) {
        return List.of(
                profile.getSummaryParagraphs(),
                profile.getEducationSummaryParagraphs(),
                profile.getFamilyBackgroundSummaryParagraphs(),
                profile.getCareerSummaryParagraphs(),
                profile.getChinaRelatedStatementsSummaryParagraphs(),
                profile.getPoliticalTendencySummaryParagraphs(),
                profile.getContactInformationSummaryParagraphs(),
                profile.getFamilyMemberSituationSummaryParagraphs(),
                profile.getMisconductSummaryParagraphs()
        );
    }

    private void mergeArticleSourcesInto(Map<String, ArticleCitation> target, List<ArticleCitation> articleSources) {
        if (target == null || articleSources == null) {
            return;
        }
        for (ArticleCitation articleSource : articleSources) {
            if (articleSource == null) {
                continue;
            }
            String key = articleCitationKey(articleSource);
            if (!StringUtils.hasText(key)) {
                continue;
            }
            ArticleCitation existing = target.get(key);
            if (existing == null) {
                target.put(key, new ArticleCitation()
                        .setId(articleSource.getId())
                        .setTitle(articleSource.getTitle())
                        .setUrl(articleSource.getUrl())
                        .setSource(articleSource.getSource())
                        .setPublishedAt(articleSource.getPublishedAt()));
                continue;
            }
            existing.setId(existing.getId() != null ? existing.getId() : articleSource.getId());
            existing.setTitle(firstNonBlankText(existing.getTitle(), articleSource.getTitle()));
            existing.setUrl(firstNonBlankText(existing.getUrl(), articleSource.getUrl()));
            existing.setSource(firstNonBlankText(existing.getSource(), articleSource.getSource()));
            existing.setPublishedAt(firstNonBlankText(existing.getPublishedAt(), articleSource.getPublishedAt()));
        }
    }

    private List<Integer> normalizeParagraphSourceIds(ParagraphSummaryItem paragraph,
                                                      Map<String, ArticleCitation> citationsByUrl) {
        LinkedHashSet<Integer> orderedIds = new LinkedHashSet<>();
        if (paragraph == null) {
            return List.of();
        }
        if (paragraph.getSourceIds() != null) {
            paragraph.getSourceIds().stream()
                    .filter(id -> id != null && id > 0)
                    .forEach(orderedIds::add);
        }
        if (paragraph.getSourceUrls() != null && citationsByUrl != null) {
            for (String sourceUrl : paragraph.getSourceUrls()) {
                if (!StringUtils.hasText(sourceUrl)) {
                    continue;
                }
                ArticleCitation citation = citationsByUrl.get(sourceUrl);
                if (citation != null && citation.getId() != null && citation.getId() > 0) {
                    orderedIds.add(citation.getId());
                }
            }
        }
        return new ArrayList<>(orderedIds);
    }

    private List<Integer> extractCitationIds(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        LinkedHashSet<Integer> orderedIds = new LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                int id = Integer.parseInt(matcher.group(1));
                if (id > 0) {
                    orderedIds.add(id);
                }
            } catch (NumberFormatException ignored) {
                // 忽略异常编号，保留其余可解析来源。
            }
        }
        return new ArrayList<>(orderedIds);
    }

    private String paragraphSourceKey(ParagraphSource source) {
        if (source == null) {
            return null;
        }
        if (StringUtils.hasText(source.getUrl())) {
            return source.getUrl().trim();
        }
        return (StringUtils.hasText(source.getTitle()) ? source.getTitle().trim() : "")
                + "|"
                + (StringUtils.hasText(source.getSource()) ? source.getSource().trim() : "");
    }

    private String articleCitationKey(ArticleCitation articleCitation) {
        if (articleCitation == null) {
            return null;
        }
        return articleKey(articleCitation.getUrl(), articleCitation.getTitle());
    }

    private <T> T firstNonNull(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
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
        return selectTopUrls(evidences, properties.getApi().getJina().getMaxPageReads());
    }

    private List<PageSummary> normalizeDirectPageSummaries(List<PageSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }
        List<PageSummary> normalized = new ArrayList<>();
        int nextId = 1;
        for (PageSummary summary : summaries) {
            if (summary == null || !StringUtils.hasText(summary.getSummary())) {
                continue;
            }
            if (summary.getSourceId() == null || summary.getSourceId() <= 0) {
                summary.setSourceId(nextId);
            }
            if (summary.getArticleSources() == null || summary.getArticleSources().isEmpty()) {
                summary.setArticleSources(List.of(new ArticleCitation()
                        .setId(summary.getSourceId())
                        .setTitle(summary.getTitle())
                        .setUrl(summary.getSourceUrl())
                        .setSource(summary.getSourcePlatform())));
            }
            normalized.add(summary);
            nextId = Math.max(nextId + 1, summary.getSourceId() + 1);
        }
        return normalized;
    }

    private List<String> selectTopUrls(List<WebEvidence> evidences, int maxPageReads) {
        if (evidences == null || evidences.isEmpty()) {
            return List.of();
        }
        int maxReads = Math.max(1, maxPageReads);
        Set<String> urls = new LinkedHashSet<>();
        evidences.stream()
                .filter(item -> StringUtils.hasText(item.getUrl()))
                // 在读正文之前先过滤明显非文章类链接，避免浪费 Jina 和 LLM 配额。
                .filter(item -> !shouldSkipSummaryUrl(item.getUrl()))
                .forEach(item -> {
                    if (urls.size() < maxReads) {
                        urls.add(item.getUrl());
                    }
                });
        return new ArrayList<>(urls);
    }

    private static final class SectionTopicReadBudget {

        private int remaining;

        private SectionTopicReadBudget(int maxReads) {
            this.remaining = Math.max(0, maxReads);
        }

        private int remaining() {
            return remaining;
        }

        private boolean isExhausted() {
            return remaining <= 0;
        }

        private void consume(int count) {
            remaining = Math.max(0, remaining - Math.max(0, count));
        }
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
        return null;
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
        if (!StringUtils.hasText(name)) {
            return List.of();
        }
        SearchLanguageProfile languageProfile = searchLanguageProfileService.resolveProfile(
                name,
                new ResolvedPersonProfile().setResolvedName(name)
        );
        List<SocialAccount> accounts = new ArrayList<>(digitalFootprintQueryBuilder.build(name, languageProfile).stream()
                .filter(this::isSocialProfileQuery)
                .sorted(Comparator.comparing(query -> query.getPriority() == null ? Integer.MAX_VALUE : query.getPriority()))
                .limit(SOCIAL_ACCOUNT_QUERY_LIMIT)
                .flatMap(query -> searchSocialAccounts(query).stream())
                .toList());
        accounts.addAll(collectMaigretSuspectedAccounts(name));
        return accounts.isEmpty() ? List.of() : deduplicateSocialAccounts(accounts);
    }

    private List<SocialAccount> collectMaigretSuspectedAccounts(String name) {
        List<String> usernames = inferLikelySocialUsernames(name).stream()
                .filter(StringUtils::hasText)
                .map(username -> username.replaceFirst("^@", "").trim())
                .filter(username -> username.matches("[A-Za-z0-9._-]{2,64}"))
                .distinct()
                .limit(properties.getApi().getMaigret().getMaxUsernames())
                .toList();
        if (usernames.isEmpty()) {
            return List.of();
        }
        List<SocialAccount> accounts = new ArrayList<>();
        for (String username : usernames) {
            try {
                accounts.addAll(maigretClient.findSuspectedAccounts(username));
            } catch (RuntimeException ex) {
                log.warn("Maigret suspected account search failed username={} error={}", username, ex.getMessage(), ex);
            }
        }
        return accounts;
    }

    private List<String> inferLikelySocialUsernames(String name) {
        List<WebEvidence> evidences = collectSocialUsernameSearchEvidence(name);
        if (evidences.isEmpty()) {
            return List.of();
        }
        try {
            // 先由模型从账号关键词搜索结果中做人物归属判断，减少把同名或组织账号交给 Maigret 扩散。
            List<String> modelUsernames = deepSeekSummaryGenerationClient == null
                    ? summaryGenerationClient.inferLikelySocialUsernames(name, evidences)
                    : deepSeekSummaryGenerationClient.inferLikelySocialUsernames(name, evidences);
            if (modelUsernames != null && !modelUsernames.isEmpty()) {
                return modelUsernames;
            }
        } catch (RuntimeException ex) {
            log.warn("social username model inference failed name={} error={}", name, ex.getMessage(), ex);
        }
        return extractUsernameCandidatesFromEvidence(evidences);
    }

    private List<WebEvidence> collectSocialUsernameSearchEvidence(String name) {
        List<WebEvidence> evidences = new ArrayList<>();
        for (String query : buildSocialUsernameQueries(name)) {
            try {
                SerpApiResponse response = googleSearchClient.googleSearch(query);
                if (response != null && response.getRoot() != null) {
                    evidences.addAll(extractSearchOrganicWebEvidence(response.getRoot()));
                }
            } catch (RuntimeException ex) {
                log.warn("social username query failed name={} query={} error={}", name, query, ex.getMessage(), ex);
            }
        }
        return deduplicateWebEvidencePrioritized(evidences);
    }

    private List<String> buildSocialUsernameQueries(String name) {
        if (!StringUtils.hasText(name)) {
            return List.of();
        }
        String normalizedName = name.trim();
        return List.of(
                        normalizedName + " Twitter username",
                        normalizedName + " X username",
                        normalizedName + " Instagram username",
                        normalizedName + " official social account",
                        normalizedName + " 社交账号 用户名",
                        normalizedName + " 推特 账号"
                ).stream()
                .distinct()
                .limit(SOCIAL_USERNAME_QUERY_LIMIT)
                .toList();
    }

    private List<String> extractUsernameCandidatesFromEvidence(List<WebEvidence> evidences) {
        LinkedHashSet<String> usernames = new LinkedHashSet<>();
        for (WebEvidence evidence : evidences) {
            String text = Stream.of(
                            cleanText(evidence == null ? null : evidence.getTitle()),
                            cleanText(evidence == null ? null : evidence.getSnippet()),
                            cleanText(evidence == null ? null : evidence.getUrl()))
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining(" "));
            // 模型不可用时只做保守兜底：从明确的 @handle 文本中提取候选用户名。
            Matcher matcher = HANDLE_PATTERN.matcher(text);
            while (matcher.find()) {
                usernames.add(matcher.group(1));
            }
        }
        return usernames.stream().limit(5).toList();
    }

    private List<SocialAccount> searchSocialAccounts(DigitalFootprintQuery query) {
        try {
            SerpApiResponse response = googleSearchClient.googleSearch(query.getQueryText());
            if (response == null || response.getRoot() == null) {
                return List.of();
            }
            List<WebEvidence> evidences = extractSearchOrganicWebEvidence(response.getRoot());
            List<SocialAccount> accounts = new ArrayList<>();
            for (WebEvidence evidence : evidences) {
                SocialAccount account = toSocialAccount(evidence, query.getPlatform());
                if (account != null && StringUtils.hasText(account.getUrl())) {
                    accounts.add(account);
                }
            }
            return accounts;
        } catch (RuntimeException ex) {
            log.warn("social account query failed query={} error={}", query == null ? null : query.getQueryText(), ex.getMessage(), ex);
            return List.of();
        }
    }

    private SocialAccount toSocialAccount(WebEvidence evidence, String fallbackPlatform) {
        if (evidence == null || !StringUtils.hasText(evidence.getUrl())) {
            return null;
        }
        String url = normalizeDedupUrl(evidence.getUrl());
        String platform = detectSocialPlatform(url, fallbackPlatform);
        if (!StringUtils.hasText(platform)) {
            return null;
        }
        return new SocialAccount()
                .setPlatform(platform)
                .setUrl(url)
                .setUsername(extractSocialUsername(url, evidence.getTitle()))
                .setSource("google")
                .setSuspected(false)
                .setConfidence("search_result");
    }

    private boolean isContactInformationQuery(DigitalFootprintQuery query) {
        if (query == null || !StringUtils.hasText(query.getQueryText())) {
            return false;
        }
        String type = query.getQueryType();
        return "email_contact".equals(type) || "official_site".equals(type) || isSocialProfileQuery(query);
    }

    private boolean isSocialProfileQuery(DigitalFootprintQuery query) {
        if (query == null) {
            return false;
        }
        if ("social_profile".equals(query.getQueryType())) {
            return true;
        }
        return StringUtils.hasText(query.getPlatform())
                && !"email".equalsIgnoreCase(query.getPlatform())
                && !"website".equalsIgnoreCase(query.getPlatform())
                && !"blog".equalsIgnoreCase(query.getPlatform());
    }

    private String detectSocialPlatform(String url, String fallbackPlatform) {
        if (!StringUtils.hasText(url)) {
            return fallbackPlatform;
        }
        String normalized = url.toLowerCase();
        if (normalized.contains("linkedin.com")) {
            return "linkedin";
        }
        if (normalized.contains("twitter.com")) {
            return "twitter";
        }
        if (normalized.contains("x.com")) {
            return "x";
        }
        if (normalized.contains("github.com")) {
            return "github";
        }
        if (normalized.contains("instagram.com")) {
            return "instagram";
        }
        if (normalized.contains("youtube.com") || normalized.contains("youtu.be")) {
            return "youtube";
        }
        return fallbackPlatform;
    }

    private String extractSocialUsername(String url, String title) {
        if (StringUtils.hasText(url)) {
            try {
                URI uri = URI.create(url);
                String path = uri.getPath();
                if (StringUtils.hasText(path)) {
                    String normalized = path.replaceAll("^/+", "").replaceAll("/+$", "");
                    if (StringUtils.hasText(normalized)) {
                        int slashIndex = normalized.indexOf('/');
                        return slashIndex >= 0 ? normalized.substring(0, slashIndex) : normalized;
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // ignore malformed url and fallback to title
            }
        }
        return cleanText(title);
    }

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String resolvedNameForModelCall(String fallbackName, ResolvedPersonProfile profile) {
        if (profile != null && StringUtils.hasText(profile.getResolvedName())) {
            return profile.getResolvedName();
        }
        return fallbackName;
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
                ResolvedPersonProfile localFallback = buildLocalProfileFallback(fallbackName, pageSummaries);
                if (localFallback != null) {
                    log.warn("最终画像使用本地摘要兜底 fallbackName={} pageSummaryCount={}",
                            fallbackName, pageSummaries == null ? 0 : pageSummaries.size());
                    return localFallback;
                }
                if (failHard) {
                    throw new ApiCallException("LLM_PROFILE_FAILED: " + LLM_FAILURE_MESSAGE, kimiEx);
                }
                throw kimiEx;
            }
        }
    }

    private ResolvedPersonProfile buildLocalProfileFallback(String fallbackName, List<PageSummary> pageSummaries) {
        List<PageSummary> usableSummaries = usableProfileFallbackSummaries(pageSummaries);
        if (usableSummaries.isEmpty()) {
            return null;
        }
        List<ArticleCitation> articleSources = buildArticleCitations(usableSummaries);
        List<String> summaryParts = usableSummaries.stream()
                .map(PageSummary::getSummary)
                .map(this::cleanText)
                .filter(StringUtils::hasText)
                .map(summary -> truncateText(summary, LOCAL_PROFILE_FALLBACK_MAX_SUMMARY_CHARS))
                .distinct()
                .toList();
        if (summaryParts.isEmpty()) {
            return null;
        }
        String summary = String.join(" ", summaryParts);
        String description = firstNonBlankText(
                usableSummaries.stream()
                        .map(PageSummary::getTitle)
                        .map(this::cleanText)
                        .filter(StringUtils::hasText)
                        .findFirst()
                        .orElse(null),
                truncateText(summaryParts.get(0), 160)
        );
        List<ParagraphSummaryItem> paragraphs = usableSummaries.stream()
                .limit(LOCAL_PROFILE_FALLBACK_PARAGRAPH_COUNT)
                .map(this::toLocalFallbackParagraph)
                .filter(Objects::nonNull)
                .toList();
        List<String> keyFacts = usableSummaries.stream()
                .flatMap(summaryItem -> summaryItem.getKeyFacts() == null
                        ? Stream.empty()
                        : summaryItem.getKeyFacts().stream())
                .map(this::cleanText)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(LOCAL_PROFILE_FALLBACK_FACT_COUNT)
                .toList();
        List<String> tags = usableSummaries.stream()
                .flatMap(summaryItem -> summaryItem.getTags() == null
                        ? Stream.empty()
                        : summaryItem.getTags().stream())
                .map(this::cleanText)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(LOCAL_PROFILE_FALLBACK_FACT_COUNT)
                .toList();
        List<String> evidenceUrls = usableSummaries.stream()
                .map(PageSummary::getSourceUrl)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return new ResolvedPersonProfile()
                .setResolvedName(fallbackName)
                .setDescription(description)
                .setSummary(summary)
                .setSummaryParagraphs(paragraphs)
                .setKeyFacts(keyFacts)
                .setTags(tags)
                .setEvidenceUrls(evidenceUrls)
                .setArticleSources(articleSources)
                .setTotalArticlesRead(pageSummaries == null ? 0 : pageSummaries.size())
                .setFinalArticlesUsed(paragraphs.stream()
                        .flatMap(paragraph -> paragraph.getSourceIds().stream())
                        .collect(Collectors.toSet())
                        .size());
    }

    private List<PageSummary> usableProfileFallbackSummaries(List<PageSummary> pageSummaries) {
        if (pageSummaries == null || pageSummaries.isEmpty()) {
            return List.of();
        }
        return pageSummaries.stream()
                .filter(Objects::nonNull)
                .filter(summary -> StringUtils.hasText(summary.getSummary()))
                .filter(summary -> !summary.getSummary().startsWith("[不采纳]"))
                .toList();
    }

    private ParagraphSummaryItem toLocalFallbackParagraph(PageSummary summary) {
        if (summary == null || !StringUtils.hasText(summary.getSummary())) {
            return null;
        }
        String text = truncateText(cleanText(summary.getSummary()), LOCAL_PROFILE_FALLBACK_PARAGRAPH_MAX_CHARS);
        List<Integer> sourceIds = summary.getSourceId() == null ? List.of() : List.of(summary.getSourceId());
        if (summary.getSourceId() != null && !text.contains("[" + summary.getSourceId() + "]")) {
            text = text + "[" + summary.getSourceId() + "]";
        }
        return new ParagraphSummaryItem()
                .setText(text)
                .setSourceIds(sourceIds)
                .setSourceUrls(StringUtils.hasText(summary.getSourceUrl()) ? List.of(summary.getSourceUrl()) : List.of());
    }

    private String truncateText(String value, int maxChars) {
        String normalized = cleanText(value);
        if (!StringUtils.hasText(normalized) || maxChars <= 0 || normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "…";
    }

    private ResolvedPersonProfile completeJudgedProfile(ResolvedPersonProfile judged,
                                                        ResolvedPersonProfile profile,
                                                        List<String> warnings) {
        if (judged == null) {
            warnings.add(JUDGEMENT_WARNING);
            return profile;
        }
        if (StringUtils.hasText(profile.getResolvedName())
                && (!StringUtils.hasText(judged.getResolvedName()) || isStableResolvedName(profile.getResolvedName()))) {
            judged.setResolvedName(profile.getResolvedName());
        }
        if ((judged.getEvidenceUrls() == null || judged.getEvidenceUrls().isEmpty()) && profile.getEvidenceUrls() != null) {
            judged.setEvidenceUrls(profile.getEvidenceUrls());
        }
        return judged;
    }

    private boolean isStableResolvedName(String resolvedName) {
        if (!StringUtils.hasText(resolvedName)) {
            return false;
        }
        String normalized = resolvedName.trim().toLowerCase(Locale.ROOT);
        return !normalized.contains("uncertain")
                && !normalized.contains("unknown")
                && !normalized.contains("不确定")
                && !normalized.contains("未知")
                && !normalized.contains("无法确定");
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
            String key = normalizeSocialAccountKey(account);
            deduplicated.putIfAbsent(key, account);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private String normalizeSocialAccountKey(SocialAccount account) {
        String url = normalizeDedupUrl(account.getUrl());
        if (StringUtils.hasText(url)) {
            return url.toLowerCase(Locale.ROOT);
        }
        return (cleanText(account.getPlatform()) + ":" + cleanText(account.getUsername())).toLowerCase(Locale.ROOT);
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
