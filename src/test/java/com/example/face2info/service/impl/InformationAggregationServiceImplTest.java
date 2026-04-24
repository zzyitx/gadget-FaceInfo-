package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.client.RealtimeTranslationClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.client.impl.DeepSeekSummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.ArticleCitation;
import com.example.face2info.entity.internal.DigitalFootprintQuery;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ParagraphSummaryItem;
import com.example.face2info.entity.internal.ParagraphSource;
import com.example.face2info.entity.internal.PersonBasicInfo;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import com.example.face2info.entity.internal.SearchLanguageInferenceResult;
import com.example.face2info.entity.internal.SectionSummaryItem;
import com.example.face2info.entity.internal.SectionedSummary;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.internal.TopicExpansionDecision;
import com.example.face2info.entity.internal.TopicExpansionQuery;
import com.example.face2info.entity.internal.WebEvidence;
import com.example.face2info.entity.response.SocialAccount;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.service.DerivedTopicQueryService;
import com.example.face2info.service.DigitalFootprintQueryBuilder;
import com.example.face2info.service.MultilingualQueryPlanningService;
import com.example.face2info.service.PrimarySearchQueryBuilder;
import com.example.face2info.service.SearchLanguageProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InformationAggregationServiceImplTest {

    private final ThreadPoolTaskExecutor executor = executor();

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void shouldCreateServiceWithSummaryAndJinaDependencies() {
        ThreadPoolTaskExecutor localExecutor = executor();
        try {
            InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                    mock(GoogleSearchClient.class),
                    mock(SerpApiClient.class),
                    mock(JinaReaderClient.class),
                    mock(SummaryGenerationClient.class),
                    localExecutor
            );
            assertThat(service).isNotNull();
        } finally {
            localExecutor.shutdown();
        }
    }

    @Test
    void shouldSummarizePagesOneByOneBeforeFinalProfileAggregation() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent pageA = new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Page A");
        PageContent pageB = new PageContent().setUrl("https://example.com/b").setTitle("B").setContent("Page B");
        List<PageContent> pages = List.of(pageA, pageB);
        PageSummary summaryA = new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("Summary A");
        PageSummary summaryB = new PageSummary().setSourceUrl("https://example.com/b").setTitle("B").setSummary("Summary B");
        when(jinaReaderClient.readPages(List.of("https://example.com/a", "https://example.com/b"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePage("unknown", pageA)).thenReturn(summaryA);
        when(summaryGenerationClient.summarizePage("unknown", pageB)).thenReturn(summaryB);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("unknown", List.of(summaryA, summaryB)))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("Jay Chou")
                        .setSummary("Final Summary")
                        .setEvidenceUrls(List.of("https://example.com/a", "https://example.com/b")));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        );

        ResolvedPersonProfile profile = service.resolveProfileFromEvidence(List.of(
                new WebEvidence().setUrl("https://example.com/a"),
                new WebEvidence().setUrl("https://example.com/b")
        ), "unknown");

        assertThat(profile.getResolvedName()).isEqualTo("Jay Chou");
        assertThat(profile.getSummary()).isEqualTo("Final Summary");
        assertThat(profile.getEvidenceUrls()).containsExactly("https://example.com/a", "https://example.com/b");
        verify(summaryGenerationClient).summarizePage("unknown", pageA);
        verify(summaryGenerationClient).summarizePage("unknown", pageB);
        verify(summaryGenerationClient).summarizePersonFromPageSummaries("unknown", List.of(summaryA, summaryB));
    }

    @Test
    void shouldBackfillParagraphSourcesAndGlobalArticleSourcesFromSourceIds() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent pageA = new PageContent().setUrl("https://example.com/a").setTitle("Article A").setContent("Page A");
        PageContent pageB = new PageContent().setUrl("https://example.com/b").setTitle("Article B").setContent("Page B");
        PageSummary summaryA = new PageSummary()
                .setSourceId(1)
                .setSourceUrl("https://example.com/a")
                .setTitle("Article A")
                .setSummary("Summary A");
        PageSummary summaryB = new PageSummary()
                .setSourceId(2)
                .setSourceUrl("https://example.com/b")
                .setTitle("Article B")
                .setSummary("Summary B");
        ResolvedPersonProfile finalProfile = new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setSummary("Final Summary[2]")
                .setSummaryParagraphs(List.of(
                        new com.example.face2info.entity.internal.ParagraphSummaryItem()
                                .setText("Final Summary[2]")
                                .setSourceIds(List.of(2))
                ))
                .setArticleSources(List.of(
                        new ArticleCitation().setId(1).setTitle("Article A").setUrl("https://example.com/a"),
                        new ArticleCitation().setId(2).setTitle("Article B").setUrl("https://example.com/b")
                ));

        when(jinaReaderClient.readPages(List.of("https://example.com/a", "https://example.com/b")))
                .thenReturn(List.of(pageA, pageB));
        when(summaryGenerationClient.summarizePage("unknown", pageA)).thenReturn(summaryA);
        when(summaryGenerationClient.summarizePage("unknown", pageB)).thenReturn(summaryB);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("unknown", List.of(summaryA, summaryB)))
                .thenReturn(finalProfile);
        when(summaryGenerationClient.applyComprehensiveJudgement("Jay Chou", List.of(summaryA, summaryB), finalProfile))
                .thenReturn(finalProfile);

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class),
                mock(SerpApiClient.class),
                jinaReaderClient,
                summaryGenerationClient,
                executor
        );

        ResolvedPersonProfile profile = service.resolveProfileFromEvidence(List.of(
                new WebEvidence().setUrl("https://example.com/a"),
                new WebEvidence().setUrl("https://example.com/b")
        ), "unknown");

        assertThat(profile.getArticleSources()).extracting(ArticleCitation::getId).containsExactly(1, 2);
        assertThat(profile.getTotalArticlesRead()).isEqualTo(2);
        assertThat(profile.getFinalArticlesUsed()).isEqualTo(1);
        assertThat(profile.getSummaryParagraphs()).hasSize(1);
        assertThat(profile.getSummaryParagraphs().get(0).getSourceIds()).containsExactly(2);
        assertThat(profile.getSummaryParagraphs().get(0).getSources()).hasSize(1);
        assertThat(profile.getSummaryParagraphs().get(0).getSources().get(0).getUrl()).isEqualTo("https://example.com/b");
        assertThat(profile.getSummaryParagraphs().get(0).getSources().get(0).getTitle()).isEqualTo("Article B");
    }

    @Test
    void shouldAppendUncitedArticleMatchesIntoBackendArticleSources() {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent pageA = new PageContent().setUrl("https://example.com/a").setTitle("Article A").setContent("Page A");
        PageContent pageB = new PageContent().setUrl("https://example.com/b").setTitle("Article B").setContent("Page B");
        PageSummary summaryA = new PageSummary()
                .setSourceId(1)
                .setSourceUrl("https://example.com/a")
                .setTitle("Article A")
                .setSourcePlatform("Example")
                .setSummary("Summary A");
        PageSummary summaryB = new PageSummary()
                .setSourceId(2)
                .setSourceUrl("https://example.com/b")
                .setTitle("Article B")
                .setSourcePlatform("Example")
                .setSummary("Summary B");
        ResolvedPersonProfile finalProfile = new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setSummary("Final Summary[1][2]")
                .setSummaryParagraphs(List.of(
                        new ParagraphSummaryItem()
                                .setText("Final Summary[1][2]")
                                .setSourceIds(List.of(1, 2))
                ))
                .setArticleSources(List.of(
                        new ArticleCitation().setId(1).setTitle("Article A").setUrl("https://example.com/a").setSource("Example"),
                        new ArticleCitation().setId(2).setTitle("Article B").setUrl("https://example.com/b").setSource("Example")
                ));

        when(jinaReaderClient.readPages(List.of("https://example.com/a", "https://example.com/b")))
                .thenReturn(List.of(pageA, pageB));
        when(summaryGenerationClient.summarizePage("Jay Chou", pageA)).thenReturn(summaryA);
        when(summaryGenerationClient.summarizePage("Jay Chou", pageB)).thenReturn(summaryB);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("Jay Chou", List.of(summaryA, summaryB)))
                .thenReturn(finalProfile);
        when(summaryGenerationClient.applyComprehensiveJudgement("Jay Chou", List.of(summaryA, summaryB), finalProfile))
                .thenReturn(finalProfile);
        when(googleSearchClient.googleSearch(anyString())).thenReturn(null);

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient,
                mock(SerpApiClient.class),
                jinaReaderClient,
                summaryGenerationClient,
                executor
        );

        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(
                        new WebEvidence().setUrl("https://example.com/a"),
                        new WebEvidence().setUrl("https://example.com/b")
                ))
                .setArticleImageMatches(List.of(
                        new com.example.face2info.entity.response.ImageMatch()
                                .setPosition(1)
                                .setTitle("Article B")
                                .setLink("https://example.com/b")
                                .setSource("Example"),
                        new com.example.face2info.entity.response.ImageMatch()
                                .setPosition(2)
                                .setTitle("Article C")
                                .setLink("https://example.com/c")
                                .setSource("Example News")
                )));

        assertThat(result.getPerson().getArticleSources())
                .extracting(ArticleCitation::getId, ArticleCitation::getUrl)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "https://example.com/a"),
                        org.assertj.core.groups.Tuple.tuple(2, "https://example.com/b"),
                        org.assertj.core.groups.Tuple.tuple(3, "https://example.com/c")
                );
        assertThat(result.getPerson().getFinalArticlesUsed()).isEqualTo(2);
    }

    @Test
    void shouldUseDeepSeekForLongContentPageSummary() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient kimiClient = mock(SummaryGenerationClient.class);
        DeepSeekSummaryGenerationClient deepSeekClient = mock(DeepSeekSummaryGenerationClient.class);
        ApiProperties properties = createApiProperties(null);
        properties.getApi().getSummary().setPageRoutingEnabled(true);
        properties.getApi().getSummary().setLongContentThreshold(20);

        PageContent longPage = new PageContent()
                .setUrl("https://example.com/a")
                .setTitle("Long article")
                .setContent("This is a very long page body used for deepseek routing.");
        PageSummary deepSeekSummary = new PageSummary()
                .setSourceUrl("https://example.com/a")
                .setTitle("Long article")
                .setSummary("DeepSeek summary");

        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(List.of(longPage));
        when(deepSeekClient.summarizePage("unknown", longPage)).thenReturn(deepSeekSummary);
        when(deepSeekClient.summarizePersonFromPageSummaries("unknown", List.of(deepSeekSummary)))
                .thenReturn(new ResolvedPersonProfile().setResolvedName("Jay Chou").setSummary("DeepSeek final"));
        when(deepSeekClient.applyComprehensiveJudgement(eq("Jay Chou"), eq(List.of(deepSeekSummary)), any(ResolvedPersonProfile.class)))
                .thenReturn(new ResolvedPersonProfile().setResolvedName("Jay Chou").setSummary("DeepSeek final"));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, kimiClient, deepSeekClient, executor, properties
        );

        service.resolveProfileFromEvidence(List.of(new WebEvidence().setUrl("https://example.com/a")), "unknown", List.of());

        verify(deepSeekClient).summarizePage("unknown", longPage);
        verify(kimiClient, never()).summarizePage("unknown", longPage);
    }

    @Test
    void shouldRunSecondaryProfileSearchInMultipleLanguages() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        ObjectMapper mapper = new ObjectMapper();
        when(jinaReaderClient.readPages(List.of("https://example.com/seed"))).thenReturn(List.of(
                new PageContent().setUrl("https://example.com/seed").setTitle("Seed").setContent("seed body")
        ));
        when(summaryGenerationClient.summarizePage(anyString(), any(PageContent.class)))
                .thenReturn(new PageSummary().setSourceUrl("https://example.com/seed").setTitle("Seed").setSummary("seed summary"));
        when(summaryGenerationClient.summarizePersonFromPageSummaries(anyString(), anyList()))
                .thenReturn(new ResolvedPersonProfile().setResolvedName("黄仁勋（Jensen Huang）").setSummary("base summary"));
        when(summaryGenerationClient.applyComprehensiveJudgement(anyString(), anyList(), any(ResolvedPersonProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(summaryGenerationClient.inferSearchLanguageProfile(anyString(), any()))
                .thenReturn(new SearchLanguageInferenceResult()
                        .setPrimaryNationality("US")
                        .setRecommendedLanguages(List.of("zh", "en"))
                        .setLocalizedNames(Map.of("zh", "黄仁勋", "en", "Jensen Huang"))
                        .setConfidence(0.9));
        when(googleSearchClient.googleSearch("黄仁勋")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jensen Huang")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient,
                mock(SerpApiClient.class),
                jinaReaderClient,
                summaryGenerationClient,
                executor,
                createApiProperties(null)
        );

        service.aggregate(new RecognitionEvidence()
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/seed")))
                .setSeedQueries(List.of("黄仁勋")));

        verify(googleSearchClient, atLeastOnce()).googleSearch("黄仁勋");
        verify(googleSearchClient, atLeastOnce()).googleSearch("Jensen Huang");
    }

    @Test
    void shouldUsePrimarySearchQueryBuilderForSecondaryProfileSearch() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        PrimarySearchQueryBuilder primarySearchQueryBuilder = mock(PrimarySearchQueryBuilder.class);

        ObjectMapper mapper = new ObjectMapper();
        when(jinaReaderClient.readPages(List.of("https://example.com/seed"))).thenReturn(List.of(
                new PageContent().setUrl("https://example.com/seed").setTitle("Seed").setContent("seed body")
        ));
        when(summaryGenerationClient.summarizePage(anyString(), any(PageContent.class)))
                .thenReturn(new PageSummary().setSourceUrl("https://example.com/seed").setTitle("Seed").setSummary("seed summary"));
        when(summaryGenerationClient.summarizePersonFromPageSummaries(anyString(), anyList()))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("尼古拉斯·伯恩斯")
                        .setDescription("美国驻华大使，资深外交官。")
                        .setSummary("美国驻华大使，资深外交官。"));
        when(summaryGenerationClient.applyComprehensiveJudgement(anyString(), anyList(), any(ResolvedPersonProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(summaryGenerationClient.inferSearchLanguageProfile(anyString(), any()))
                .thenReturn(new SearchLanguageInferenceResult()
                        .setPrimaryNationality("US")
                        .setRecommendedLanguages(List.of("zh", "en"))
                        .setLocalizedNames(Map.of("zh", "尼古拉斯·伯恩斯", "en", "Nicholas Burns"))
                        .setConfidence(0.95));
        when(primarySearchQueryBuilder.buildSecondaryProfileQueries(anyString(), any(), any()))
                .thenReturn(List.of(
                        "尼古拉斯·伯恩斯 驻华大使",
                        "尼古拉斯·伯恩斯 驻华大使 涉华言论",
                        "Nicholas Burns Ambassador China policy"
                ));
        when(googleSearchClient.googleSearch("尼古拉斯·伯恩斯 驻华大使"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("尼古拉斯·伯恩斯 驻华大使 涉华言论"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Nicholas Burns Ambassador China policy"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient,
                mock(SerpApiClient.class),
                jinaReaderClient,
                summaryGenerationClient,
                null,
                executor,
                createApiProperties(null),
                mock(DerivedTopicQueryService.class),
                new SearchLanguageProfileServiceImpl(summaryGenerationClient),
                new MultilingualQueryPlanningServiceImpl((query, targetLanguageCode) -> null),
                mock(DigitalFootprintQueryBuilder.class),
                primarySearchQueryBuilder
        );

        service.aggregate(new RecognitionEvidence()
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/seed")))
                .setSeedQueries(List.of("伯恩斯")));

        verify(primarySearchQueryBuilder).buildSecondaryProfileQueries(anyString(), any(), any());
        verify(googleSearchClient).googleSearch("尼古拉斯·伯恩斯 驻华大使");
        verify(googleSearchClient).googleSearch("尼古拉斯·伯恩斯 驻华大使 涉华言论");
        verify(googleSearchClient).googleSearch("Nicholas Burns Ambassador China policy");
    }

    @Test
    void shouldKeepInitialResolvedNameWhenSecondaryEvidenceSuggestsDifferentPerson() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        MultilingualQueryPlanningService multilingualQueryPlanningService = mock(MultilingualQueryPlanningService.class);
        PrimarySearchQueryBuilder primarySearchQueryBuilder = mock(PrimarySearchQueryBuilder.class);
        DigitalFootprintQueryBuilder digitalFootprintQueryBuilder = mock(DigitalFootprintQueryBuilder.class);

        ObjectMapper mapper = new ObjectMapper();
        PageContent seedPage = new PageContent()
                .setUrl("https://example.com/seed")
                .setTitle("Jensen Huang profile")
                .setContent("Jensen Huang is NVIDIA founder.");
        PageSummary seedSummary = new PageSummary()
                .setSourceUrl("https://example.com/seed")
                .setTitle("Jensen Huang profile")
                .setSummary("Jensen Huang profile summary");
        ResolvedPersonProfile baseProfile = new ResolvedPersonProfile()
                .setResolvedName("Jensen Huang")
                .setSummary("NVIDIA founder profile");

        PageContent secondaryPage = new PageContent()
                .setUrl("https://example.com/ces")
                .setTitle("CES coverage")
                .setContent("Article mentions NVIDIA, Intel and other people.");
        PageSummary secondarySummary = new PageSummary()
                .setSourceUrl("https://example.com/ces")
                .setTitle("CES coverage")
                .setSummary("CES article summary");
        ResolvedPersonProfile secondaryProfile = new ResolvedPersonProfile()
                .setResolvedName("王军")
                .setSummary("Supplemental event context");

        when(jinaReaderClient.readPages(List.of("https://example.com/seed"))).thenReturn(List.of(seedPage));
        when(summaryGenerationClient.summarizePage("Jensen Huang", seedPage)).thenReturn(seedSummary);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("Jensen Huang", List.of(seedSummary)))
                .thenReturn(baseProfile);
        when(summaryGenerationClient.applyComprehensiveJudgement(anyString(), anyList(), any(ResolvedPersonProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));

        SearchLanguageProfileService searchLanguageProfileService = (resolvedName, profile) -> new SearchLanguageProfile()
                .setResolvedName(resolvedName)
                .setLanguageCodes(List.of("zh", "en"))
                .setLocalizedNames(Map.of("en", "Jensen Huang"));
        when(multilingualQueryPlanningService.planSecondaryProfileQueries(any()))
                .thenReturn(List.of(new com.example.face2info.entity.internal.SearchQueryTask()
                        .setLanguageCode("en")
                        .setQueryText("Jensen Huang")
                        .setQueryKind("secondary_profile")));
        when(primarySearchQueryBuilder.buildSecondaryProfileQueries(anyString(), any(), any()))
                .thenReturn(List.of());
        when(googleSearchClient.googleSearch("Jensen Huang"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"CES coverage","link":"https://example.com/ces","snippet":"event snippet"}]}
                        """)));
        when(jinaReaderClient.readPages(List.of("https://example.com/ces"))).thenReturn(List.of(secondaryPage));
        when(summaryGenerationClient.summarizePage("Jensen Huang", secondaryPage)).thenReturn(secondarySummary);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("Jensen Huang", List.of(secondarySummary)))
                .thenReturn(secondaryProfile);
        when(multilingualQueryPlanningService.planSectionQueries(any(), anyString(), anyList())).thenReturn(List.of());
        when(multilingualQueryPlanningService.planExpansionQueries(any(), anyString(), anyList())).thenReturn(List.of());
        when(primarySearchQueryBuilder.buildSectionQueries(anyString(), any(), any(), anyString())).thenReturn(List.of());
        when(digitalFootprintQueryBuilder.build(anyString(), any())).thenReturn(List.of());

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient,
                mock(SerpApiClient.class),
                jinaReaderClient,
                summaryGenerationClient,
                null,
                executor,
                createApiProperties(null),
                mock(DerivedTopicQueryService.class),
                searchLanguageProfileService,
                multilingualQueryPlanningService,
                digitalFootprintQueryBuilder,
                primarySearchQueryBuilder
        );

        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/seed")))
                .setSeedQueries(List.of("Jensen Huang")));

        assertThat(result.getPerson().getName()).isEqualTo("Jensen Huang");
    }

    @Test
    void shouldDeduplicateRepeatedUrlsAcrossChineseAndEnglishRounds() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        RealtimeTranslationClient translationClient = mock(RealtimeTranslationClient.class);

        ObjectMapper mapper = new ObjectMapper();
        when(summaryGenerationClient.inferSearchLanguageProfile(anyString(), any()))
                .thenReturn(new SearchLanguageInferenceResult()
                        .setPrimaryNationality("US")
                        .setRecommendedLanguages(List.of("zh", "en"))
                        .setLocalizedNames(Map.of("zh", "黄仁勋", "en", "Jensen Huang"))
                        .setConfidence(0.9));
        when(googleSearchClient.googleSearch("黄仁勋 涉华言论"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"China Related","link":"https://example.com/china","snippet":"zh snippet"}]}
                        """)));
        when(googleSearchClient.googleSearch("Jensen Huang china-related statements"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"China Related EN","link":"https://example.com/china","snippet":"en snippet"}]}
                        """)));
        when(jinaReaderClient.readPages(List.of("https://example.com/china")))
                .thenReturn(List.of(new PageContent().setUrl("https://example.com/china").setTitle("China Related").setContent("body")));
        when(summaryGenerationClient.summarizePage(anyString(), any(PageContent.class)))
                .thenReturn(new PageSummary().setSourceUrl("https://example.com/china").setTitle("China Related").setSummary("summary"));
        when(summaryGenerationClient.summarizeSectionFromPageSummaries(eq("黄仁勋"), eq("china_related_statements"), anyList()))
                .thenReturn("summary");

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient,
                mock(SerpApiClient.class),
                jinaReaderClient,
                summaryGenerationClient,
                null,
                executor,
                createApiProperties(null),
                new MultilingualQueryPlanningServiceImpl(translationClient)
        );

        String summary = service.summarizeSection("黄仁勋", "china_related_statements", "黄仁勋 涉华言论");

        assertThat(summary).isEqualTo("summary");
        verify(jinaReaderClient, times(1)).readPages(List.of("https://example.com/china"));
    }

    @Test
    void shouldFallbackToKimiWhenDeepSeekSectionSummaryFails() {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient kimiClient = mock(SummaryGenerationClient.class);
        DeepSeekSummaryGenerationClient deepSeekClient = mock(DeepSeekSummaryGenerationClient.class);

        PageContent page = new PageContent().setUrl("https://example.com/education").setTitle("Education").setContent("education body");
        PageSummary pageSummary = new PageSummary().setSourceUrl("https://example.com/education").setTitle("Education").setSummary("education page");

        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(List.of(
                new PageContent().setUrl("https://example.com/a").setTitle("Seed").setContent("seed body")
        ));
        when(kimiClient.summarizePage(anyString(), any(PageContent.class))).thenReturn(
                new PageSummary().setSourceUrl("https://example.com/a").setTitle("Seed").setSummary("seed summary")
        );
        when(kimiClient.summarizePersonFromPageSummaries(anyString(), anyList()))
                .thenReturn(new ResolvedPersonProfile().setResolvedName("Jay Chou").setSummary("base summary"));
        when(kimiClient.applyComprehensiveJudgement(anyString(), anyList(), any(ResolvedPersonProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));

        ObjectMapper mapper = new ObjectMapper();
        try {
            when(googleSearchClient.googleSearch("Jay Chou 教育经历"))
                    .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                            {"organic":[{"title":"Education","link":"https://example.com/education","snippet":"education snippet"}]}
                            """)));
            when(googleSearchClient.googleSearch("Jay Chou education"))
                    .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        when(jinaReaderClient.readPages(List.of("https://example.com/education"))).thenReturn(List.of(page));
        when(deepSeekClient.summarizePage("Jay Chou", page)).thenReturn(pageSummary);
        when(kimiClient.summarizePage("Jay Chou", page)).thenReturn(pageSummary);
        when(deepSeekClient.summarizeSectionFromPageSummaries("Jay Chou", "education", List.of(pageSummary)))
                .thenThrow(new ApiCallException("TIMEOUT: deepseek timeout"));
        when(kimiClient.summarizeSectionFromPageSummaries("Jay Chou", "education", List.of(pageSummary)))
                .thenReturn("education summary");

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient, mock(SerpApiClient.class),
                jinaReaderClient, kimiClient, deepSeekClient, executor, createApiProperties(null)
        );

        String summary = service.summarizeSection("Jay Chou", "education", "Jay Chou的教育经历");

        assertThat(summary).isEqualTo("education summary");
        verify(kimiClient).summarizeSectionFromPageSummaries("Jay Chou", "education", List.of(pageSummary));
    }

    @Test
    void shouldFallbackToKimiWhenDeepSeekPageSummaryFails() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient kimiClient = mock(SummaryGenerationClient.class);
        DeepSeekSummaryGenerationClient deepSeekClient = mock(DeepSeekSummaryGenerationClient.class);
        ApiProperties properties = createApiProperties(null);
        properties.getApi().getSummary().setPageRoutingEnabled(true);
        properties.getApi().getSummary().setLongContentThreshold(20);

        PageContent page = new PageContent()
                .setUrl("https://example.com/a")
                .setTitle("A")
                .setContent("Long article body for DeepSeek routing.");
        PageSummary kimiSummary = new PageSummary()
                .setSourceUrl("https://example.com/a")
                .setTitle("A")
                .setSummary("Kimi summary");

        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(List.of(page));
        when(deepSeekClient.summarizePage("unknown", page))
                .thenThrow(new ApiCallException("INVALID_RESPONSE: DeepSeek 页面摘要未返回 JSON"));
        when(kimiClient.summarizePage("unknown", page)).thenReturn(kimiSummary);
        when(deepSeekClient.summarizePersonFromPageSummaries("unknown", List.of(kimiSummary)))
                .thenThrow(new ApiCallException("INVALID_RESPONSE: deepseek final invalid"));
        when(kimiClient.summarizePersonFromPageSummaries("unknown", List.of(kimiSummary)))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("Jay Chou")
                        .setSummary("Final Summary")
                        .setEvidenceUrls(List.of("https://example.com/a")));

        ResolvedPersonProfile profile = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class),
                mock(SerpApiClient.class),
                jinaReaderClient,
                kimiClient,
                deepSeekClient,
                executor,
                properties
        ).resolveProfileFromEvidence(List.of(new WebEvidence().setUrl("https://example.com/a")), "unknown");

        assertThat(profile.getResolvedName()).isEqualTo("Jay Chou");
        assertThat(profile.getSummary()).isEqualTo("Final Summary");
        verify(deepSeekClient).summarizePage("unknown", page);
        verify(kimiClient).summarizePage("unknown", page);
        verify(kimiClient).summarizePersonFromPageSummaries("unknown", List.of(kimiSummary));
    }

    @Test
    void shouldRunSectionBaseQueriesInMultipleLanguages() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        ApiProperties properties = createApiProperties(null);
        ObjectMapper mapper = new ObjectMapper();
        when(summaryGenerationClient.inferSearchLanguageProfile(anyString(), any()))
                .thenReturn(new SearchLanguageInferenceResult()
                        .setPrimaryNationality("JP")
                        .setRecommendedLanguages(List.of("zh", "en", "ja"))
                        .setLocalizedNames(Map.of("zh", "宫崎骏", "en", "Hayao Miyazaki", "ja", "宮崎 駿"))
                        .setConfidence(0.92));
        when(googleSearchClient.googleSearch("宫崎骏 教育经历"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Hayao Miyazaki education"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("宮崎 駿 学歴"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient,
                mock(SerpApiClient.class),
                jinaReaderClient,
                summaryGenerationClient,
                executor,
                properties
        );

        service.summarizeSection("宫崎骏（Hayao Miyazaki）", "education", "宫崎骏 教育经历");

        verify(googleSearchClient, atLeastOnce()).googleSearch("宫崎骏 教育经历");
        verify(googleSearchClient, atLeastOnce()).googleSearch("Hayao Miyazaki education");
        verify(googleSearchClient, atLeastOnce()).googleSearch("宮崎 駿 学歴");
    }

    @Test
    void shouldStillRunSectionSummaryWhenOnlyPartOfPageSummariesSucceed() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent pageA = new PageContent()
                .setUrl("https://example.com/education-a")
                .setTitle("Education A")
                .setContent("education body a");
        PageContent pageB = new PageContent()
                .setUrl("https://example.com/education-b")
                .setTitle("Education B")
                .setContent("education body b");
        PageSummary summaryB = new PageSummary()
                .setSourceUrl("https://example.com/education-b")
                .setTitle("Education B")
                .setSummary("education summary b");

        ObjectMapper mapper = new ObjectMapper();
        when(googleSearchClient.googleSearch("Jay Chou 教育经历"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[
                          {"title":"Education A","link":"https://example.com/education-a","snippet":"snippet a"},
                          {"title":"Education B","link":"https://example.com/education-b","snippet":"snippet b"}
                        ]}
                        """)));
        when(googleSearchClient.googleSearch("Jay Chou education"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(jinaReaderClient.readPages(List.of("https://example.com/education-a", "https://example.com/education-b")))
                .thenReturn(List.of(pageA, pageB));
        when(summaryGenerationClient.summarizePage("Jay Chou", pageA))
                .thenThrow(new ApiCallException("INVALID_RESPONSE: first page failed"));
        when(summaryGenerationClient.summarizePage("Jay Chou", pageB))
                .thenReturn(summaryB);
        when(summaryGenerationClient.summarizeSectionFromPageSummaries("Jay Chou", "education", List.of(summaryB)))
                .thenReturn("education summary");

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient, mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        );

        String summary = service.summarizeSection("Jay Chou", "education", "Jay Chou的教育经历");

        assertThat(summary).isEqualTo("education summary");
        verify(summaryGenerationClient).summarizeSectionFromPageSummaries("Jay Chou", "education", List.of(summaryB));
    }

    @Test
    void shouldSearchChinaRelatedSectionWithMultipleBaseQueries() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        ApiProperties properties = createApiProperties(null);

        PageContent statementPage = new PageContent()
                .setUrl("https://example.com/china-statement")
                .setTitle("Statement")
                .setContent("statement body");
        PageContent relationPage = new PageContent()
                .setUrl("https://example.com/china-relation")
                .setTitle("Relation")
                .setContent("relation body");
        PageSummary statementSummary = new PageSummary()
                .setSourceUrl("https://example.com/china-statement")
                .setTitle("Statement")
                .setSummary("statement summary");
        PageSummary relationSummary = new PageSummary()
                .setSourceUrl("https://example.com/china-relation")
                .setTitle("Relation")
                .setSummary("relation summary");

        ObjectMapper mapper = new ObjectMapper();
        when(googleSearchClient.googleSearch("Jay Chou 涉华言论"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"Statement","link":"https://example.com/china-statement","snippet":"statement snippet"}]}
                        """)));
        when(googleSearchClient.googleSearch("Jay Chou 中国评价"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"Relation","link":"https://example.com/china-relation","snippet":"relation snippet"}]}
                        """)));
        when(googleSearchClient.googleSearch("Jay Chou 中美关系"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jay Chou 中欧关系"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(jinaReaderClient.readPages(List.of("https://example.com/china-statement"))).thenReturn(List.of(statementPage));
        when(jinaReaderClient.readPages(List.of("https://example.com/china-relation"))).thenReturn(List.of(relationPage));
        when(summaryGenerationClient.summarizePage("Jay Chou", statementPage)).thenReturn(statementSummary);
        when(summaryGenerationClient.summarizePage("Jay Chou", relationPage)).thenReturn(relationSummary);
        when(summaryGenerationClient.expandTopicQueriesFromPageSummaries(
                "Jay Chou",
                "china_related_statements",
                List.of(statementSummary, relationSummary)))
                .thenReturn(new TopicExpansionDecision()
                        .setShouldExpand(true)
                        .setExpansionQueries(List.of(
                                new TopicExpansionQuery().setTerm("中国市场").setSection("中国评价").setReason("首轮摘要提到其在中国市场的公开表态")
                        )));
        when(summaryGenerationClient.summarizeSectionedSectionFromPageSummaries(
                "Jay Chou",
                "china_related_statements",
                List.of(statementSummary, relationSummary)))
                .thenReturn(new SectionedSummary().setSections(List.of(
                        new SectionSummaryItem().setSection("涉华言论").setSummary("公开资料提到其曾发表涉华观点。"),
                        new SectionSummaryItem().setSection("中国评价").setSummary("公开资料提到其评价中国市场。")
                )));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient, mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor, properties
        );

        String summary = service.summarizeSection("Jay Chou", "china_related_statements", "unused fallback");

        assertThat(summary).contains("一、涉华言论");
        assertThat(summary).contains("二、中国评价");
        assertThat(summary).contains("中国市场（首轮摘要提到其在中国市场的公开表态）");
        verify(googleSearchClient).googleSearch("Jay Chou 涉华言论");
        verify(googleSearchClient).googleSearch("Jay Chou 中国评价");
        verify(googleSearchClient).googleSearch("Jay Chou 中美关系");
        verify(googleSearchClient).googleSearch("Jay Chou 中欧关系");
    }

    @Test
    void shouldFallbackToDefaultFamilyMemberBaseQueriesWhenConfigMissing() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        ApiProperties properties = createApiProperties(null);
        properties.getApi().getQueryRewrite().getBaseQueryTemplates().remove("family_member_situation");

        PageContent familyPage = new PageContent()
                .setUrl("https://example.com/family")
                .setTitle("Family")
                .setContent("family body");
        PageContent investmentPage = new PageContent()
                .setUrl("https://example.com/investment")
                .setTitle("Investment")
                .setContent("investment body");
        PageSummary familySummary = new PageSummary()
                .setSourceUrl("https://example.com/family")
                .setTitle("Family")
                .setSummary("family summary");
        PageSummary investmentSummary = new PageSummary()
                .setSourceUrl("https://example.com/investment")
                .setTitle("Investment")
                .setSummary("investment summary");

        ObjectMapper mapper = new ObjectMapper();
        when(googleSearchClient.googleSearch("黄仁勋（Jensen Huang） 家庭成员"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"Family","link":"https://example.com/family","snippet":"family snippet"}]}
                        """)));
        when(googleSearchClient.googleSearch("黄仁勋（Jensen Huang） 亲属"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("黄仁勋（Jensen Huang） 经商"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("黄仁勋（Jensen Huang） 在华投资"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"Investment","link":"https://example.com/investment","snippet":"investment snippet"}]}
                        """)));
        when(googleSearchClient.googleSearch("黄仁勋（Jensen Huang） 商业纠纷"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(jinaReaderClient.readPages(List.of("https://example.com/family"))).thenReturn(List.of(familyPage));
        when(jinaReaderClient.readPages(List.of("https://example.com/investment"))).thenReturn(List.of(investmentPage));
        when(summaryGenerationClient.summarizePage("黄仁勋（Jensen Huang）", familyPage)).thenReturn(familySummary);
        when(summaryGenerationClient.summarizePage("黄仁勋（Jensen Huang）", investmentPage)).thenReturn(investmentSummary);
        when(summaryGenerationClient.expandTopicQueriesFromPageSummaries(
                "黄仁勋（Jensen Huang）",
                "family_member_situation",
                List.of(familySummary, investmentSummary)))
                .thenReturn(new TopicExpansionDecision().setShouldExpand(false).setExpansionQueries(List.of()));
        when(summaryGenerationClient.summarizeSectionedSectionFromPageSummaries(
                "黄仁勋（Jensen Huang）",
                "family_member_situation",
                List.of(familySummary, investmentSummary)))
                .thenReturn(new SectionedSummary().setSections(List.of(
                        new SectionSummaryItem().setSection("家庭成员").setSummary("存在公开家庭成员资料。"),
                        new SectionSummaryItem().setSection("经商与投资").setSummary("公开资料提到在华投资相关讨论。")
                )));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient, mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor, properties
        );

        String summary = service.summarizeSection(
                "黄仁勋（Jensen Huang）",
                "family_member_situation",
                "黄仁勋（Jensen Huang） 家族成员 亲属 经商 在华投资 商业纠纷"
        );

        assertThat(summary).contains("一、家庭成员");
        assertThat(summary).contains("三、经商与投资");
        verify(googleSearchClient).googleSearch("黄仁勋（Jensen Huang） 家庭成员");
        verify(googleSearchClient).googleSearch("黄仁勋（Jensen Huang） 亲属");
        verify(googleSearchClient).googleSearch("黄仁勋（Jensen Huang） 经商");
        verify(googleSearchClient).googleSearch("黄仁勋（Jensen Huang） 在华投资");
        verify(googleSearchClient).googleSearch("黄仁勋（Jensen Huang） 商业纠纷");
    }

    @Test
    void shouldRunExpandedQueriesAfterTopicExpansionDecision() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        DeepSeekSummaryGenerationClient deepSeekClient = mock(DeepSeekSummaryGenerationClient.class);

        ApiProperties properties = createApiProperties(null);
        properties.getApi().getSummary().setPageRoutingEnabled(false);

        PageContent basePage = new PageContent()
                .setUrl("https://example.com/political-base")
                .setTitle("Political Base")
                .setContent("base body");
        PageContent expandedPage = new PageContent()
                .setUrl("https://example.com/political-expanded")
                .setTitle("Political Expanded")
                .setContent("expanded body");
        PageSummary baseSummary = new PageSummary()
                .setSourceUrl("https://example.com/political-base")
                .setTitle("Political Base")
                .setSummary("base summary");
        PageSummary expandedSummary = new PageSummary()
                .setSourceUrl("https://example.com/political-expanded")
                .setTitle("Political Expanded")
                .setSummary("expanded summary");

        ObjectMapper mapper = new ObjectMapper();
        when(googleSearchClient.googleSearch("Jay Chou 政治倾向"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"Political Base","link":"https://example.com/political-base","snippet":"base snippet"}]}
                        """)));
        when(googleSearchClient.googleSearch("Jay Chou 政党"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jay Chou 政治理念"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jay Chou 政策立场"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jay Chou 公开演讲"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"Political Expanded","link":"https://example.com/political-expanded","snippet":"expanded snippet"}]}
                        """)));
        when(jinaReaderClient.readPages(List.of("https://example.com/political-base"))).thenReturn(List.of(basePage));
        when(jinaReaderClient.readPages(List.of("https://example.com/political-expanded"))).thenReturn(List.of(expandedPage));
        when(summaryGenerationClient.summarizePage("Jay Chou", basePage)).thenReturn(baseSummary);
        when(summaryGenerationClient.summarizePage("Jay Chou", expandedPage)).thenReturn(expandedSummary);
        when(deepSeekClient.expandTopicQueriesFromPageSummaries(
                "Jay Chou",
                "political_view",
                List.of(baseSummary)))
                .thenReturn(new TopicExpansionDecision()
                        .setShouldExpand(true)
                        .setExpansionQueries(List.of(
                                new TopicExpansionQuery().setTerm("公开演讲").setSection("政治倾向").setReason("首轮资料提到公开演讲")
                        )));
        when(deepSeekClient.summarizeSectionedSectionFromPageSummaries(
                eq("Jay Chou"),
                eq("political_view"),
                anyList()))
                .thenReturn(new SectionedSummary().setSections(List.of(
                        new SectionSummaryItem().setSection("政治倾向").setSummary("公开资料显示其政治表态较谨慎。"),
                        new SectionSummaryItem().setSection("政治理念").setSummary("公开资料未见系统政治理念表述。")
                )));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient, mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, deepSeekClient, executor, properties
        );

        String summary = service.summarizeSection("Jay Chou", "political_view", "unused fallback");

        assertThat(summary).contains("一、政治倾向");
        assertThat(summary).contains("公开演讲（首轮资料提到公开演讲）");
        verify(deepSeekClient).expandTopicQueriesFromPageSummaries("Jay Chou", "political_view", List.of(baseSummary));
        verify(googleSearchClient).googleSearch("Jay Chou 公开演讲");
        verify(summaryGenerationClient).summarizePage("Jay Chou", expandedPage);
    }

    @Test
    void shouldStripSourceSiteTermsFromExpandedQueryBeforeGoogleSearch() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        ApiProperties properties = createApiProperties(null);
        properties.getApi().getSummary().setPageRoutingEnabled(false);
        properties.getApi().getQueryRewrite().getBaseQueryTemplates().put("family", List.of("%s的家庭背景"));
        List<String> expandEnabledTopics = new ArrayList<>(properties.getApi().getQueryRewrite().getExpandEnabledTopics());
        expandEnabledTopics.add("family");
        properties.getApi().getQueryRewrite().setExpandEnabledTopics(expandEnabledTopics);

        PageContent basePage = new PageContent()
                .setUrl("https://example.com/family-base")
                .setTitle("Family Base")
                .setContent("family body");
        PageContent expandedPage = new PageContent()
                .setUrl("https://example.com/family-background")
                .setTitle("Family Background")
                .setContent("background body");
        PageSummary baseSummary = new PageSummary()
                .setSourceUrl("https://example.com/family-base")
                .setTitle("Family Base")
                .setSummary("base summary");
        PageSummary expandedSummary = new PageSummary()
                .setSourceUrl("https://example.com/family-background")
                .setTitle("Family Background")
                .setSummary("background summary");

        ObjectMapper mapper = new ObjectMapper();
        when(googleSearchClient.googleSearch("Jensen Huang 家庭背景"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"Family Base","link":"https://example.com/family-base","snippet":"family snippet"}]}
                        """)));
        when(googleSearchClient.googleSearch("Jensen Huang family background"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jensen Huang 背景信息"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"Family Background","link":"https://example.com/family-background","snippet":"background snippet"}]}
                        """)));
        when(jinaReaderClient.readPages(List.of("https://example.com/family-base"))).thenReturn(List.of(basePage));
        when(jinaReaderClient.readPages(List.of("https://example.com/family-background"))).thenReturn(List.of(expandedPage));
        when(summaryGenerationClient.summarizePage("Jensen Huang", basePage)).thenReturn(baseSummary);
        when(summaryGenerationClient.summarizePage("Jensen Huang", expandedPage)).thenReturn(expandedSummary);
        when(summaryGenerationClient.expandTopicQueriesFromPageSummaries(
                "Jensen Huang",
                "family",
                List.of(baseSummary)))
                .thenReturn(new TopicExpansionDecision()
                        .setShouldExpand(true)
                        .setExpansionQueries(List.of(
                                new TopicExpansionQuery().setTerm("Wikipedia 背景信息").setSection("家庭背景").setReason("首轮结果来自百科页")
                        )));
        when(summaryGenerationClient.summarizeSectionFromPageSummaries(
                "Jensen Huang",
                "family",
                List.of(baseSummary, expandedSummary)))
                .thenReturn("公开资料提到其成长背景。");

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient, mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor, properties
        );

        String summary = service.summarizeSection("Jensen Huang", "family", "Jensen Huang 家庭背景");

        assertThat(summary).isEqualTo("公开资料提到其成长背景。");
        verify(googleSearchClient).googleSearch("Jensen Huang 家庭背景");
        verify(googleSearchClient).googleSearch("Jensen Huang 背景信息");
        verify(googleSearchClient, never()).googleSearch("Jensen Huang Wikipedia 背景信息");
    }

    @Test
    void shouldRenderFamilyMemberSituationWithFixedSectionTitles() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        ApiProperties properties = createApiProperties(null);
        properties.getApi().getQueryRewrite().getBaseQueryTemplates().put("family_member_situation", List.of("%s 家庭成员"));

        PageContent familyPage = new PageContent()
                .setUrl("https://example.com/family-member")
                .setTitle("Family Member")
                .setContent("family member body");
        PageSummary familySummary = new PageSummary()
                .setSourceUrl("https://example.com/family-member")
                .setTitle("Family Member")
                .setSummary("family member summary");

        ObjectMapper mapper = new ObjectMapper();
        when(googleSearchClient.googleSearch("Jay Chou 家庭成员"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"Family Member","link":"https://example.com/family-member","snippet":"family member snippet"}]}
                        """)));
        when(googleSearchClient.googleSearch("Jay Chou 子女"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(jinaReaderClient.readPages(List.of("https://example.com/family-member"))).thenReturn(List.of(familyPage));
        when(summaryGenerationClient.summarizePage("Jay Chou", familyPage)).thenReturn(familySummary);
        when(summaryGenerationClient.expandTopicQueriesFromPageSummaries(
                "Jay Chou",
                "family_member_situation",
                List.of(familySummary)))
                .thenReturn(new TopicExpansionDecision()
                        .setShouldExpand(true)
                        .setExpansionQueries(List.of(
                                new TopicExpansionQuery().setTerm("子女").setSection("家庭成员").setReason("首轮摘要提到其配偶与子女")
                        )));
        when(summaryGenerationClient.summarizeSectionedSectionFromPageSummaries("Jay Chou", "family_member_situation", List.of(familySummary)))
                .thenReturn(new SectionedSummary().setSections(List.of(
                        new SectionSummaryItem().setSection("家庭成员").setSummary("配偶与子女信息。"),
                        new SectionSummaryItem().setSection("亲属信息").setSummary("父母与兄弟姐妹公开资料有限。"),
                        new SectionSummaryItem().setSection("经商与投资").setSummary("暂无明确在华投资证据。"),
                        new SectionSummaryItem().setSection("争议与纠纷").setSummary("未发现公开商业纠纷。")
                )));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient, mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor, properties
        );

        String summary = service.summarizeSection("Jay Chou", "family_member_situation", "unused fallback");

        assertThat(summary).isEqualTo("""
                一、家庭成员
                配偶与子女信息。
                扩展检索依据：子女（首轮摘要提到其配偶与子女）

                二、亲属信息
                父母与兄弟姐妹公开资料有限。

                三、经商与投资
                暂无明确在华投资证据。

                四、争议与纠纷
                未发现公开商业纠纷。""");
    }

    @Test
    void shouldReturnModelExtractionFailureWhenBothDeepSeekAndKimiFail() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient kimiClient = mock(SummaryGenerationClient.class);
        DeepSeekSummaryGenerationClient deepSeekClient = mock(DeepSeekSummaryGenerationClient.class);

        PageContent page = new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("body");
        PageSummary pageSummary = new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("page summary");
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(List.of(page));
        when(kimiClient.summarizePage("Jay Chou", page)).thenReturn(pageSummary);
        when(deepSeekClient.summarizePage("Jay Chou", page)).thenReturn(pageSummary);
        when(deepSeekClient.summarizePersonFromPageSummaries("Jay Chou", List.of(pageSummary)))
                .thenThrow(new ApiCallException("TIMEOUT: deepseek timeout"));
        when(kimiClient.summarizePersonFromPageSummaries("Jay Chou", List.of(pageSummary)))
                .thenThrow(new ApiCallException("INVALID_RESPONSE: kimi invalid"));

        AggregationResult result = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, kimiClient, deepSeekClient, executor, createApiProperties(null)
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getErrors()).contains("大模型提取人物信息失败");
    }

    @Test
    void shouldSkipFailedPageSummariesAndStillBuildFinalProfileWhenAtLeastOnePageSucceeds() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent pageA = new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Page A");
        PageContent pageB = new PageContent().setUrl("https://example.com/b").setTitle("B").setContent("Page B");
        PageSummary summaryB = new PageSummary().setSourceUrl("https://example.com/b").setTitle("B").setSummary("Summary B");
        when(jinaReaderClient.readPages(List.of("https://example.com/a", "https://example.com/b")))
                .thenReturn(List.of(pageA, pageB));
        when(summaryGenerationClient.summarizePage("unknown", pageA)).thenThrow(new RuntimeException("INVALID_RESPONSE"));
        when(summaryGenerationClient.summarizePage("unknown", pageB)).thenReturn(summaryB);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("unknown", List.of(summaryB)))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("Jay Chou")
                        .setSummary("Final Summary")
                        .setEvidenceUrls(List.of("https://example.com/b")));

        ResolvedPersonProfile profile = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).resolveProfileFromEvidence(List.of(
                new WebEvidence().setUrl("https://example.com/a"),
                new WebEvidence().setUrl("https://example.com/b")
        ), "unknown");

        assertThat(profile.getResolvedName()).isEqualTo("Jay Chou");
        assertThat(profile.getSummary()).isEqualTo("Final Summary");
        verify(summaryGenerationClient).summarizePersonFromPageSummaries("unknown", List.of(summaryB));
    }

    @Test
    void shouldSkipVideoUrlsWhenCollectingPageSummaries() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent articlePage = new PageContent()
                .setUrl("https://example.com/article")
                .setTitle("Article")
                .setContent("Article body");
        PageSummary articleSummary = new PageSummary()
                .setSourceUrl("https://example.com/article")
                .setTitle("Article")
                .setSummary("Article summary");

        when(jinaReaderClient.readPages(List.of("https://example.com/article")))
                .thenReturn(List.of(articlePage));
        when(summaryGenerationClient.summarizePage("unknown", articlePage)).thenReturn(articleSummary);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("unknown", List.of(articleSummary)))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("雷军")
                        .setSummary("Final Summary")
                        .setEvidenceUrls(List.of("https://example.com/article")));

        ResolvedPersonProfile profile = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).resolveProfileFromEvidence(List.of(
                new WebEvidence().setUrl("https://www.youtube.com/watch?v=phV5czDmlDc").setTitle("Video"),
                new WebEvidence().setUrl("https://example.com/article").setTitle("Article")
        ), "unknown");

        assertThat(profile.getResolvedName()).isEqualTo("雷军");
        assertThat(profile.getSummary()).isEqualTo("Final Summary");
        verify(jinaReaderClient).readPages(List.of("https://example.com/article"));
        verify(summaryGenerationClient).summarizePage("unknown", articlePage);
    }

    @Test
    void shouldReturnFallbackProfileAndWarningWhenNoPageSummarySucceeds() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent pageA = new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Page A");
        PageContent pageB = new PageContent().setUrl("https://example.com/b").setTitle("B").setContent("Page B");
        when(jinaReaderClient.readPages(List.of("https://example.com/a", "https://example.com/b")))
                .thenReturn(List.of(pageA, pageB));
        when(summaryGenerationClient.summarizePage("unknown", pageA)).thenThrow(new RuntimeException("INVALID_RESPONSE"));
        when(summaryGenerationClient.summarizePage("unknown", pageB)).thenThrow(new RuntimeException("TIMEOUT"));

        AggregationResult result = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("unknown"))
                .setWebEvidences(List.of(
                        new WebEvidence().setUrl("https://example.com/a"),
                        new WebEvidence().setUrl("https://example.com/b")
                )));

        assertThat(result.getPerson().getName()).isNull();
        assertThat(result.getPerson().getSummary()).isNull();
        assertThat(result.getPerson().getDescription()).isNull();
        assertThat(result.getWarnings()).containsExactly("正文智能处理暂时不可用");
        verify(summaryGenerationClient, never()).summarizePersonFromPageSummaries(anyString(), anyList());
    }

    @Test
    void shouldOnlyReadTopFiveUrlsByDefault() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        List<String> expectedUrls = List.of(
                "https://example.com/1",
                "https://example.com/2",
                "https://example.com/3",
                "https://example.com/4",
                "https://example.com/5"
        );
        when(jinaReaderClient.readPages(expectedUrls)).thenReturn(List.of());

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, mock(SummaryGenerationClient.class), executor, createApiProperties(null)
        );

        service.resolveProfileFromEvidence(List.of(
                new WebEvidence().setUrl("https://example.com/1"),
                new WebEvidence().setUrl("https://example.com/2"),
                new WebEvidence().setUrl("https://example.com/3"),
                new WebEvidence().setUrl("https://example.com/4"),
                new WebEvidence().setUrl("https://example.com/5"),
                new WebEvidence().setUrl("https://example.com/6"),
                new WebEvidence().setUrl("https://example.com/7")
        ), "unknown");

        verify(jinaReaderClient).readPages(expectedUrls);
    }

    @Test
    void shouldRespectConfiguredMaxPageReads() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        List<String> expectedUrls = List.of(
                "https://example.com/1",
                "https://example.com/2",
                "https://example.com/3"
        );
        when(jinaReaderClient.readPages(expectedUrls)).thenReturn(List.of());

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, mock(SummaryGenerationClient.class), executor, createApiProperties(3)
        );

        service.resolveProfileFromEvidence(List.of(
                new WebEvidence().setUrl("https://example.com/1"),
                new WebEvidence().setUrl("https://example.com/2"),
                new WebEvidence().setUrl("https://example.com/3"),
                new WebEvidence().setUrl("https://example.com/4"),
                new WebEvidence().setUrl("https://example.com/5")
        ), "unknown");

        verify(jinaReaderClient).readPages(expectedUrls);
    }

    @Test
    void shouldLimitSectionTopicJinaReadsToTwentyAcrossSearchQueries() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        MultilingualQueryPlanningService multilingualQueryPlanningService = mock(MultilingualQueryPlanningService.class);
        PrimarySearchQueryBuilder primarySearchQueryBuilder = mock(PrimarySearchQueryBuilder.class);

        ApiProperties properties = createApiProperties(50);
        ObjectMapper mapper = new ObjectMapper();
        List<String> firstUrls = numberedUrls("https://example.com/china-a-", 15);
        List<String> secondUrls = numberedUrls("https://example.com/china-b-", 15);
        List<String> secondExpectedUrls = secondUrls.subList(0, 5);

        when(multilingualQueryPlanningService.planSectionQueries(any(), eq("china_related_statements"), anyList()))
                .thenReturn(List.of(
                        new com.example.face2info.entity.internal.SearchQueryTask()
                                .setLanguageCode("zh")
                                .setQueryText("黄仁勋 涉华言论")
                                .setQueryKind("section_base")
                                .setSourceReason("round_zh")
                                .setPriority(1),
                        new com.example.face2info.entity.internal.SearchQueryTask()
                                .setLanguageCode("zh")
                                .setQueryText("黄仁勋 中国评价")
                                .setQueryKind("section_base")
                                .setSourceReason("round_zh")
                                .setPriority(2)
                ));
        when(multilingualQueryPlanningService.planExpansionQueries(any(), anyString(), anyList())).thenReturn(List.of());
        when(primarySearchQueryBuilder.buildSectionQueries(anyString(), any(), any(), anyString())).thenReturn(List.of());
        when(googleSearchClient.googleSearch("黄仁勋 涉华言论"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree(searchResponseJson(firstUrls))));
        when(googleSearchClient.googleSearch("黄仁勋 中国评价"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree(searchResponseJson(secondUrls))));
        when(jinaReaderClient.readPages(anyList())).thenAnswer(invocation -> pagesFor(invocation.getArgument(0)));
        when(summaryGenerationClient.summarizePage(eq("黄仁勋"), any(PageContent.class)))
                .thenAnswer(invocation -> {
                    PageContent page = invocation.getArgument(1);
                    return new PageSummary()
                            .setSourceUrl(page.getUrl())
                            .setTitle(page.getTitle())
                            .setSummary(page.getContent());
                });
        when(summaryGenerationClient.expandTopicQueriesFromPageSummaries(anyString(), anyString(), anyList()))
                .thenReturn(new TopicExpansionDecision().setShouldExpand(false).setExpansionQueries(List.of()));
        when(summaryGenerationClient.summarizeSectionedSectionFromPageSummaries(
                eq("黄仁勋"),
                eq("china_related_statements"),
                argThat(summaries -> summaries != null && summaries.size() == 20)))
                .thenReturn(new SectionedSummary().setSections(List.of(
                        new SectionSummaryItem().setSection("涉华言论").setSummary("读取 20 篇后生成摘要。")
                )));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient,
                mock(SerpApiClient.class),
                jinaReaderClient,
                summaryGenerationClient,
                null,
                executor,
                properties,
                mock(DerivedTopicQueryService.class),
                (resolvedName, profile) -> new SearchLanguageProfile()
                        .setResolvedName(resolvedName)
                        .setLanguageCodes(List.of("zh"))
                        .setLocalizedNames(Map.of("zh", resolvedName)),
                multilingualQueryPlanningService,
                mock(DigitalFootprintQueryBuilder.class),
                primarySearchQueryBuilder
        );

        String summary = service.summarizeSection("黄仁勋", "china_related_statements", "黄仁勋 涉华言论");

        assertThat(summary).contains("读取 20 篇后生成摘要");
        verify(jinaReaderClient).readPages(firstUrls);
        verify(jinaReaderClient).readPages(secondExpectedUrls);
        verify(jinaReaderClient, atMostOnce()).readPages(argThat(urls -> urls != null && urls.size() > 5 && urls.containsAll(secondExpectedUrls)));
    }

    @Test
    void shouldGiveEachSectionTopicIndependentTwentyPageJinaBudget() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        MultilingualQueryPlanningService multilingualQueryPlanningService = mock(MultilingualQueryPlanningService.class);
        PrimarySearchQueryBuilder primarySearchQueryBuilder = mock(PrimarySearchQueryBuilder.class);

        ApiProperties properties = createApiProperties(50);
        ObjectMapper mapper = new ObjectMapper();
        List<String> chinaUrls = numberedUrls("https://example.com/china-", 25);
        List<String> familyUrls = numberedUrls("https://example.com/family-", 25);
        List<String> expectedChinaUrls = chinaUrls.subList(0, 20);
        List<String> expectedFamilyUrls = familyUrls.subList(0, 20);

        when(multilingualQueryPlanningService.planSectionQueries(any(), eq("china_related_statements"), anyList()))
                .thenReturn(List.of(new com.example.face2info.entity.internal.SearchQueryTask()
                        .setLanguageCode("zh")
                        .setQueryText("黄仁勋 涉华言论")
                        .setQueryKind("section_base")
                        .setSourceReason("round_zh")
                        .setPriority(1)));
        when(multilingualQueryPlanningService.planSectionQueries(any(), eq("family"), anyList()))
                .thenReturn(List.of(new com.example.face2info.entity.internal.SearchQueryTask()
                        .setLanguageCode("zh")
                        .setQueryText("黄仁勋 家庭背景")
                        .setQueryKind("section_base")
                        .setSourceReason("round_zh")
                        .setPriority(1)));
        when(multilingualQueryPlanningService.planExpansionQueries(any(), anyString(), anyList())).thenReturn(List.of());
        when(primarySearchQueryBuilder.buildSectionQueries(anyString(), any(), any(), anyString())).thenReturn(List.of());
        when(googleSearchClient.googleSearch("黄仁勋 涉华言论"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree(searchResponseJson(chinaUrls))));
        when(googleSearchClient.googleSearch("黄仁勋 家庭背景"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree(searchResponseJson(familyUrls))));
        when(jinaReaderClient.readPages(anyList())).thenAnswer(invocation -> pagesFor(invocation.getArgument(0)));
        when(summaryGenerationClient.summarizePage(eq("黄仁勋"), any(PageContent.class)))
                .thenAnswer(invocation -> {
                    PageContent page = invocation.getArgument(1);
                    return new PageSummary()
                            .setSourceUrl(page.getUrl())
                            .setTitle(page.getTitle())
                            .setSummary(page.getContent());
                });
        when(summaryGenerationClient.expandTopicQueriesFromPageSummaries(anyString(), anyString(), anyList()))
                .thenReturn(new TopicExpansionDecision().setShouldExpand(false).setExpansionQueries(List.of()));
        when(summaryGenerationClient.summarizeSectionedSectionFromPageSummaries(
                eq("黄仁勋"),
                eq("china_related_statements"),
                argThat(summaries -> summaries != null && summaries.size() == 20)))
                .thenReturn(new SectionedSummary().setSections(List.of(
                        new SectionSummaryItem().setSection("涉华言论").setSummary("涉华主题读取 20 篇。")
                )));
        when(summaryGenerationClient.summarizeSectionFromPageSummaries(
                eq("黄仁勋"),
                eq("family"),
                argThat(summaries -> summaries != null && summaries.size() == 20)))
                .thenReturn("家庭主题读取 20 篇。");

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient,
                mock(SerpApiClient.class),
                jinaReaderClient,
                summaryGenerationClient,
                null,
                executor,
                properties,
                mock(DerivedTopicQueryService.class),
                (resolvedName, profile) -> new SearchLanguageProfile()
                        .setResolvedName(resolvedName)
                        .setLanguageCodes(List.of("zh"))
                        .setLocalizedNames(Map.of("zh", resolvedName)),
                multilingualQueryPlanningService,
                mock(DigitalFootprintQueryBuilder.class),
                primarySearchQueryBuilder
        );

        String chinaSummary = service.summarizeSection("黄仁勋", "china_related_statements", "黄仁勋 涉华言论");
        String familySummary = service.summarizeSection("黄仁勋", "family", "黄仁勋 家庭背景");

        assertThat(chinaSummary).contains("涉华主题读取 20 篇");
        assertThat(familySummary).isEqualTo("家庭主题读取 20 篇。");
        verify(jinaReaderClient).readPages(expectedChinaUrls);
        verify(jinaReaderClient).readPages(expectedFamilyUrls);
    }

    @Test
    void shouldMapSummaryAndTagsIntoAggregationResult() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("正文A"));
        mockSummaryPipeline(summaryGenerationClient, "周杰伦", pages, new ResolvedPersonProfile()
                .setResolvedName("周杰伦")
                .setSummary("周杰伦是华语流行音乐代表人物。")
                .setTags(List.of("歌手", "音乐制作人"))
                .setEvidenceUrls(List.of("https://example.com/a")));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);

        AggregationResult result = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("周杰伦"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getSummary()).isEqualTo("周杰伦是华语流行音乐代表人物。 (由大模型总结)");
        assertThat(result.getPerson().getDescription()).isEqualTo("周杰伦是华语流行音乐代表人物。 (由大模型总结)");
        assertThat(result.getPerson().getTags()).containsExactly("歌手", "音乐制作人");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void shouldPopulateStructuredProfileFieldsFromFinalSummaryWithoutGoogleDetailAggregation() {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent page = new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("body");
        PageSummary pageSummary = new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("page summary");
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(List.of(page));
        when(summaryGenerationClient.summarizePage("Jay Chou", page)).thenReturn(pageSummary);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("Jay Chou", List.of(pageSummary)))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("Jay Chou")
                        .setDescription("Short description")
                        .setSummary("Long summary")
                        .setWikipedia("https://example.com/wiki")
                        .setOfficialWebsite("https://example.com")
                        .setBasicInfo(new PersonBasicInfo()
                                .setBirthDate("1979-01-18")
                                .setEducation(List.of("Tamkang Senior High School"))
                                .setOccupations(List.of("Singer", "Producer"))
                                .setBiographies(List.of("Taiwanese Mandopop artist")))
                        .setEvidenceUrls(List.of("https://example.com/a")));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient, mock(SerpApiClient.class), jinaReaderClient, summaryGenerationClient, executor
        );

        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getDescription()).isEqualTo("Short description (由大模型总结)");
        assertThat(result.getPerson().getSummary()).isEqualTo("Long summary (由大模型总结)");
        assertThat(result.getPerson().getWikipedia()).isEqualTo("https://example.com/wiki");
        assertThat(result.getPerson().getOfficialWebsite()).isEqualTo("https://example.com");
        assertThat(result.getPerson().getBasicInfo().getBirthDate()).isEqualTo("1979-01-18");
    }

    @Test
    void shouldKeepSectionSummariesIndependentFromMainSummary() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent page = new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("body");
        PageSummary pageSummary = new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("page summary");

        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(List.of(page));
        when(summaryGenerationClient.summarizePage("Jay Chou", page)).thenReturn(pageSummary);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("Jay Chou", List.of(pageSummary)))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("Jay Chou")
                        .setDescription("short intro")
                        .setSummary("base summary")
                        .setEducationSummary("education summary")
                        .setFamilyBackgroundSummary("family summary")
                        .setCareerSummary("career summary")
                        .setEvidenceUrls(List.of("https://example.com/a")));
        when(summaryGenerationClient.applyComprehensiveJudgement(
                eq("Jay Chou"),
                eq(List.of(pageSummary)),
                any(ResolvedPersonProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));

        AggregationResult result = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getSummary()).isEqualTo("base summary (由大模型总结)");
        assertThat(result.getPerson().getEducationSummary()).isEqualTo("education summary");
        assertThat(result.getPerson().getFamilyBackgroundSummary()).isEqualTo("family summary");
        assertThat(result.getPerson().getCareerSummary()).isEqualTo("career summary");
    }

    @Test
    void shouldExposeSectionSummariesWhenResolvedNameIsMissing() throws Exception {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent page = new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("body");
        PageSummary pageSummary = new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("page summary");

        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(List.of(page));
        when(summaryGenerationClient.summarizePage(null, page)).thenReturn(pageSummary);
        when(summaryGenerationClient.summarizePersonFromPageSummaries(null, List.of(pageSummary)))
                .thenReturn(new ResolvedPersonProfile()
                        .setSummary("base summary")
                        .setEducationSummary("education summary")
                        .setFamilyBackgroundSummary("family summary")
                        .setCareerSummary("career summary"));
        when(summaryGenerationClient.applyComprehensiveJudgement(
                anyString(),
                anyList(),
                any(ResolvedPersonProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));

        AggregationResult result = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of())
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getErrors()).contains("未能从识别证据中解析人物名称");
        assertThat(result.getPerson().getEducationSummary()).isEqualTo("education summary");
        assertThat(result.getPerson().getFamilyBackgroundSummary()).isEqualTo("family summary");
        assertThat(result.getPerson().getCareerSummary()).isEqualTo("career summary");
    }

    @Test
    void shouldKeepMainSummaryWhenExtraSectionsExist() {
        ResolvedPersonProfile profile = new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setDescription("short intro")
                .setSummary("base summary")
                .setEducationSummary("education summary")
                .setCareerSummary("career summary");

        assertThat(new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                mock(JinaReaderClient.class), mock(SummaryGenerationClient.class), executor
        ).buildFinalSummary(profile)).isEqualTo("base summary");
    }

    @Test
    void shouldFallbackToBaseSummaryWhenAllSectionsMissing() {
        ResolvedPersonProfile profile = new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setDescription("short intro")
                .setSummary("base summary");

        assertThat(new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                mock(JinaReaderClient.class), mock(SummaryGenerationClient.class), executor
        ).buildFinalSummary(profile)).isEqualTo("base summary");
    }

    @Test
    void shouldEnrichSummaryWithSectionQueriesByResolvedName() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent seedPage = new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("body");
        PageSummary seedSummary = new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("page summary");
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(List.of(seedPage));
        when(summaryGenerationClient.summarizePage("Jay Chou", seedPage)).thenReturn(seedSummary);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("Jay Chou", List.of(seedSummary)))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("Jay Chou")
                        .setDescription("short intro")
                        .setSummary("base summary")
                        .setEvidenceUrls(List.of("https://example.com/a")));
        when(summaryGenerationClient.applyComprehensiveJudgement(
                eq("Jay Chou"),
                eq(List.of(seedSummary)),
                any(ResolvedPersonProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));

        ObjectMapper mapper = new ObjectMapper();
        when(googleSearchClient.googleSearch("Jay Chou")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jay Chou 教育经历")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[{"title":"Education","link":"https://example.com/education","snippet":"education snippet"}]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou education")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jay Chou 家庭背景")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[{"title":"Family","link":"https://example.com/family","snippet":"family snippet"}]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou family background")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jay Chou 职业经历")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[{"title":"Career","link":"https://example.com/career","snippet":"career snippet"}]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou career")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jay Chou 涉华言论")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[{"title":"China Related","link":"https://example.com/china","snippet":"china snippet"}]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 中国评价")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 中美关系")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 中欧关系")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 政治倾向")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[{"title":"Political","link":"https://example.com/political","snippet":"political snippet"}]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 政党")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 政治理念")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 政策立场")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 公开通讯")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[{"title":"Contact","link":"https://example.com/contact","snippet":"contact snippet"}]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 办公电话")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 官方邮箱")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 认证社交账号")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 联系方式")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 家庭成员")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[{"title":"Family Member","link":"https://example.com/family-member","snippet":"family member snippet"}]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 亲属")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 经商")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 在华投资")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 商业纠纷")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 违法记录")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[{"title":"Misconduct","link":"https://example.com/misconduct","snippet":"misconduct snippet"}]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 行政处罚")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 负面事件")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou 失信行为")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[]}
                """)));

        PageContent educationPage = new PageContent().setUrl("https://example.com/education").setTitle("Education").setContent("education body");
        PageContent familyPage = new PageContent().setUrl("https://example.com/family").setTitle("Family").setContent("family body");
        PageContent careerPage = new PageContent().setUrl("https://example.com/career").setTitle("Career").setContent("career body");
        PageContent chinaPage = new PageContent().setUrl("https://example.com/china").setTitle("China").setContent("china body");
        PageContent politicalPage = new PageContent().setUrl("https://example.com/political").setTitle("Political").setContent("political body");
        PageContent contactPage = new PageContent().setUrl("https://example.com/contact").setTitle("Contact").setContent("contact body");
        PageContent familyMemberPage = new PageContent().setUrl("https://example.com/family-member").setTitle("Family Member").setContent("family member body");
        PageContent misconductPage = new PageContent().setUrl("https://example.com/misconduct").setTitle("Misconduct").setContent("misconduct body");
        when(jinaReaderClient.readPages(List.of("https://example.com/education"))).thenReturn(List.of(educationPage));
        when(jinaReaderClient.readPages(List.of("https://example.com/family"))).thenReturn(List.of(familyPage));
        when(jinaReaderClient.readPages(List.of("https://example.com/career"))).thenReturn(List.of(careerPage));
        when(jinaReaderClient.readPages(List.of("https://example.com/china"))).thenReturn(List.of(chinaPage));
        when(jinaReaderClient.readPages(List.of("https://example.com/political"))).thenReturn(List.of(politicalPage));
        when(jinaReaderClient.readPages(List.of("https://example.com/contact"))).thenReturn(List.of(contactPage));
        when(jinaReaderClient.readPages(List.of("https://example.com/family-member"))).thenReturn(List.of(familyMemberPage));
        when(jinaReaderClient.readPages(List.of("https://example.com/misconduct"))).thenReturn(List.of(misconductPage));

        PageSummary educationSummary = new PageSummary().setSourceUrl("https://example.com/education").setTitle("Education").setSummary("education page");
        PageSummary familySummary = new PageSummary().setSourceUrl("https://example.com/family").setTitle("Family").setSummary("family page");
        PageSummary careerSummary = new PageSummary().setSourceUrl("https://example.com/career").setTitle("Career").setSummary("career page");
        PageSummary chinaSummary = new PageSummary().setSourceUrl("https://example.com/china").setTitle("China").setSummary("china page");
        PageSummary politicalSummary = new PageSummary().setSourceUrl("https://example.com/political").setTitle("Political").setSummary("political page");
        PageSummary contactSummary = new PageSummary().setSourceUrl("https://example.com/contact").setTitle("Contact").setSummary("contact page");
        PageSummary familyMemberSummary = new PageSummary().setSourceUrl("https://example.com/family-member").setTitle("Family Member").setSummary("family member page");
        PageSummary misconductSummary = new PageSummary().setSourceUrl("https://example.com/misconduct").setTitle("Misconduct").setSummary("misconduct page");
        when(summaryGenerationClient.summarizePage("Jay Chou", educationPage)).thenReturn(educationSummary);
        when(summaryGenerationClient.summarizePage("Jay Chou", familyPage)).thenReturn(familySummary);
        when(summaryGenerationClient.summarizePage("Jay Chou", careerPage)).thenReturn(careerSummary);
        when(summaryGenerationClient.summarizePage("Jay Chou", chinaPage)).thenReturn(chinaSummary);
        when(summaryGenerationClient.summarizePage("Jay Chou", politicalPage)).thenReturn(politicalSummary);
        when(summaryGenerationClient.summarizePage("Jay Chou", contactPage)).thenReturn(contactSummary);
        when(summaryGenerationClient.summarizePage("Jay Chou", familyMemberPage)).thenReturn(familyMemberSummary);
        when(summaryGenerationClient.summarizePage("Jay Chou", misconductPage)).thenReturn(misconductSummary);
        when(summaryGenerationClient.summarizeSectionFromPageSummaries("Jay Chou", "education", List.of(educationSummary)))
                .thenReturn("education summary");
        when(summaryGenerationClient.summarizeSectionFromPageSummaries("Jay Chou", "family", List.of(familySummary)))
                .thenReturn("family summary");
        when(summaryGenerationClient.summarizeSectionFromPageSummaries("Jay Chou", "career", List.of(careerSummary)))
                .thenReturn("career summary");
        when(summaryGenerationClient.summarizeSectionFromPageSummaries("Jay Chou", "china_related_statements", List.of(chinaSummary)))
                .thenReturn("china related statements summary");
        when(summaryGenerationClient.summarizeSectionFromPageSummaries("Jay Chou", "political_view", List.of(politicalSummary)))
                .thenReturn("political tendency summary");
        when(summaryGenerationClient.summarizeSectionFromPageSummaries("Jay Chou", "contact_information", List.of(contactSummary)))
                .thenReturn("contact information summary");
        when(summaryGenerationClient.summarizeSectionFromPageSummaries("Jay Chou", "family_member_situation", List.of(familyMemberSummary)))
                .thenReturn("family member situation summary");
        when(summaryGenerationClient.summarizeSectionFromPageSummaries("Jay Chou", "misconduct", List.of(misconductSummary)))
                .thenReturn("misconduct summary");
        when(summaryGenerationClient.summarizeSectionedSectionFromPageSummaries("Jay Chou", "education", List.of(educationSummary)))
                .thenReturn(new SectionedSummary().setSections(List.of(
                        new SectionSummaryItem()
                                .setSection("教育经历")
                                .setSummary("第一段教育经历。")
                                .setSources(List.of(
                                        new ParagraphSource().setTitle("Education").setUrl("https://example.com/education")
                                )),
                        new SectionSummaryItem()
                                .setSection("教育经历")
                                .setSummary("第二段教育经历。")
                                .setSources(List.of(
                                        new ParagraphSource().setTitle("Education").setUrl("https://example.com/education")
                                ))
                )));
        when(summaryGenerationClient.summarizeSectionedSectionFromPageSummaries("Jay Chou", "family", List.of(familySummary)))
                .thenReturn(new SectionedSummary().setSections(List.of(
                        new SectionSummaryItem()
                                .setSection("家庭背景")
                                .setSummary("第一段家庭背景。")
                                .setSources(List.of(
                                        new ParagraphSource().setTitle("Family").setUrl("https://example.com/family")
                                ))
                )));

        AggregationResult result = new InformationAggregationServiceImpl(
                googleSearchClient, mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getDescription()).isEqualTo("short intro (由大模型总结)");
        assertThat(result.getPerson().getSummary()).isEqualTo("base summary (由大模型总结)");
        assertThat(result.getPerson().getEducationSummary()).isEqualTo("education summary");
        assertThat(result.getPerson().getFamilyBackgroundSummary()).isEqualTo("family summary");
        assertThat(result.getPerson().getCareerSummary()).isEqualTo("career summary");
        assertThat(result.getPerson().getChinaRelatedStatementsSummary()).isEqualTo("china related statements summary");
        assertThat(result.getPerson().getPoliticalTendencySummary()).isEqualTo("political tendency summary");
        assertThat(result.getPerson().getContactInformationSummary()).isEqualTo("contact information summary");
        assertThat(result.getPerson().getFamilyMemberSituationSummary()).isEqualTo("family member situation summary");
        assertThat(result.getPerson().getMisconductSummary()).isEqualTo("misconduct summary");
        assertThat(result.getPerson().getEducationSummaryParagraphs()).hasSize(2);
        assertThat(result.getPerson().getEducationSummaryParagraphs().get(0).getText()).isEqualTo("第一段教育经历。");
        assertThat(result.getPerson().getEducationSummaryParagraphs().get(0).getSources())
                .extracting(ParagraphSource::getUrl)
                .containsExactly("https://example.com/education");
        assertThat(result.getPerson().getFamilyBackgroundSummaryParagraphs()).hasSize(1);
        assertThat(result.getPerson().getFamilyBackgroundSummaryParagraphs().get(0).getSources().get(0).getTitle())
                .isEqualTo("Family");
    }

    @Test
    void shouldKeepBaseSummaryWhenSectionSearchThrows() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent page = new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("body");
        PageSummary pageSummary = new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("page summary");

        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(List.of(page));
        when(summaryGenerationClient.summarizePage("Jay Chou", page)).thenReturn(pageSummary);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("Jay Chou", List.of(pageSummary)))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("Jay Chou")
                        .setDescription("short")
                        .setSummary("base summary"));
        when(summaryGenerationClient.applyComprehensiveJudgement(anyString(), anyList(), any(ResolvedPersonProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));

        ObjectMapper mapper = new ObjectMapper();
        when(googleSearchClient.googleSearch("Jay Chou")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jay Chou的教育经历")).thenThrow(new RuntimeException("search failed"));
        when(googleSearchClient.googleSearch("Jay Chou的家庭背景")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jay Chou的职业经历")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));

        AggregationResult result = new InformationAggregationServiceImpl(
                googleSearchClient, mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getSummary()).isEqualTo("base summary (由大模型总结)");
    }

    @Test
    void shouldApplyComprehensiveJudgementAfterFinalSummary() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent page = new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("body");
        PageSummary pageSummary = new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("page summary");
        ResolvedPersonProfile draftProfile = new ResolvedPersonProfile()
                .setResolvedName("Uncertain Name")
                .setSummary("draft summary")
                .setEvidenceUrls(List.of("https://example.com/a"));
        ResolvedPersonProfile judgedProfile = new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setSummary("judged summary")
                .setEvidenceUrls(List.of("https://example.com/a"));

        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(List.of(page));
        when(summaryGenerationClient.summarizePage("Jay Chou", page)).thenReturn(pageSummary);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("Jay Chou", List.of(pageSummary))).thenReturn(draftProfile);
        when(summaryGenerationClient.applyComprehensiveJudgement(
                "Uncertain Name",
                List.of(pageSummary),
                draftProfile)).thenReturn(judgedProfile);

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        );

        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou", "周杰伦"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getName()).isEqualTo("Jay Chou");
        assertThat(result.getPerson().getSummary()).isEqualTo("judged summary (由大模型总结)");
        verify(summaryGenerationClient).applyComprehensiveJudgement(
                "Uncertain Name",
                List.of(pageSummary),
                draftProfile);
    }

    @Test
    void shouldShareKimiResolvedNameWithDeepSeekJudgement() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient kimiClient = mock(SummaryGenerationClient.class);
        DeepSeekSummaryGenerationClient deepSeekClient = mock(DeepSeekSummaryGenerationClient.class);
        ApiProperties properties = createApiProperties(null);
        properties.getApi().getSummary().setPageRoutingEnabled(false);

        PageContent seedPage = new PageContent().setUrl("https://example.com/seed").setTitle("Nvidia").setContent("seed body");
        PageSummary seedSummary = new PageSummary().setSourceUrl("https://example.com/seed").setTitle("Nvidia").setSummary("seed summary");
        ResolvedPersonProfile kimiProfile = new ResolvedPersonProfile()
                .setResolvedName("黄仁勋")
                .setSummary("base summary")
                .setEvidenceUrls(List.of("https://example.com/seed"));

        when(jinaReaderClient.readPages(List.of("https://example.com/seed"))).thenReturn(List.of(seedPage));
        when(kimiClient.summarizePage("Nvidia", seedPage)).thenReturn(seedSummary);
        when(deepSeekClient.summarizePersonFromPageSummaries("Nvidia", List.of(seedSummary)))
                .thenThrow(new ApiCallException("EMPTY_RESPONSE: DeepSeek 返回内容为空"));
        when(kimiClient.summarizePersonFromPageSummaries("Nvidia", List.of(seedSummary))).thenReturn(kimiProfile);
        when(deepSeekClient.applyComprehensiveJudgement("黄仁勋", List.of(seedSummary), kimiProfile)).thenReturn(kimiProfile);

        ResolvedPersonProfile profile = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class),
                mock(SerpApiClient.class),
                jinaReaderClient,
                kimiClient,
                deepSeekClient,
                executor,
                properties
        ).resolveProfileFromEvidence(
                List.of(new WebEvidence().setUrl("https://example.com/seed")),
                "Nvidia",
                new ArrayList<>()
        );

        assertThat(profile.getResolvedName()).isEqualTo("黄仁勋");
        verify(deepSeekClient).applyComprehensiveJudgement("黄仁勋", List.of(seedSummary), kimiProfile);
    }

    @Test
    void shouldNotFallbackToSeedQueryWhenResolvedNameMissing() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent page = new PageContent().setUrl("https://example.com/a").setTitle("Nvidia").setContent("body");
        PageSummary pageSummary = new PageSummary().setSourceUrl("https://example.com/a").setTitle("Nvidia").setSummary("page summary");
        ResolvedPersonProfile unnamedProfile = new ResolvedPersonProfile().setSummary("base summary");

        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(List.of(page));
        when(summaryGenerationClient.summarizePage("Nvidia", page)).thenReturn(pageSummary);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("Nvidia", List.of(pageSummary))).thenReturn(unnamedProfile);
        when(summaryGenerationClient.applyComprehensiveJudgement("Nvidia", List.of(pageSummary), unnamedProfile))
                .thenReturn(unnamedProfile);

        AggregationResult result = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Nvidia"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getErrors()).contains("未能从识别证据中解析人物名称");
        assertThat(result.getPerson().getName()).isNull();
    }

    @Test
    void shouldRunSecondarySerperSearchWithResolvedNameAndUseKnowledgeGraphAvatar() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        PageContent initialPage = new PageContent().setUrl("https://example.com/seed").setTitle("Seed").setContent("seed body");
        PageSummary initialSummary = new PageSummary().setSourceUrl("https://example.com/seed").setTitle("Seed").setSummary("seed summary");
        when(jinaReaderClient.readPages(List.of("https://example.com/seed"))).thenReturn(List.of(initialPage));
        when(summaryGenerationClient.summarizePage("Lei Jun", initialPage)).thenReturn(initialSummary);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("Lei Jun", List.of(initialSummary)))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("Lei Jun")
                        .setSummary("initial summary")
                        .setEvidenceUrls(List.of("https://example.com/seed")));

        ObjectMapper mapper = new ObjectMapper();
        SerpApiResponse textSearch = new SerpApiResponse().setRoot(mapper.readTree("""
                {
                  "knowledgeGraph": {
                    "imageUrl": "https://img.example.com/lei-jun.jpg",
                    "descriptionLink": "https://zh.wikipedia.org/wiki/%E9%9B%B7%E5%86%9B"
                  },
                  "organic": [
                    {
                      "title": "雷军_百度百科",
                      "link": "https://baike.baidu.com/item/%E9%9B%B7%E5%86%9B/1968",
                      "snippet": "百度百科简介"
                    },
                    {
                      "title": "雷军 - 维基百科",
                      "link": "https://zh.wikipedia.org/wiki/%E9%9B%B7%E5%86%9B",
                      "snippet": "维基百科简介"
                    },
                    {
                      "title": "Xiaomi 官方介绍",
                      "link": "https://ir.mi.com/zh-hans/board-member-management/leijun/",
                      "snippet": "官方简介"
                    }
                  ]
                }
                """));
        when(googleSearchClient.googleSearch("Lei Jun")).thenReturn(textSearch);

        List<String> secondaryUrls = List.of(
                "https://zh.wikipedia.org/wiki/%E9%9B%B7%E5%86%9B",
                "https://baike.baidu.com/item/%E9%9B%B7%E5%86%9B/1968",
                "https://ir.mi.com/zh-hans/board-member-management/leijun/"
        );
        PageContent wikiPage = new PageContent().setUrl(secondaryUrls.get(0)).setTitle("Wiki").setContent("wiki body");
        PageContent baikePage = new PageContent().setUrl(secondaryUrls.get(1)).setTitle("Baike").setContent("baike body");
        PageContent officialPage = new PageContent().setUrl(secondaryUrls.get(2)).setTitle("Official").setContent("official body");
        when(jinaReaderClient.readPages(secondaryUrls)).thenReturn(List.of(wikiPage, baikePage, officialPage));

        PageSummary wikiSummary = new PageSummary().setSourceUrl(secondaryUrls.get(0)).setTitle("Wiki").setSummary("wiki summary");
        PageSummary baikeSummary = new PageSummary().setSourceUrl(secondaryUrls.get(1)).setTitle("Baike").setSummary("baike summary");
        PageSummary officialSummary = new PageSummary().setSourceUrl(secondaryUrls.get(2)).setTitle("Official").setSummary("official summary");
        when(summaryGenerationClient.summarizePage("Lei Jun", wikiPage)).thenReturn(wikiSummary);
        when(summaryGenerationClient.summarizePage("Lei Jun", baikePage)).thenReturn(baikeSummary);
        when(summaryGenerationClient.summarizePage("Lei Jun", officialPage)).thenReturn(officialSummary);
        when(summaryGenerationClient.summarizePersonFromPageSummaries("Lei Jun", List.of(wikiSummary, baikeSummary, officialSummary)))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("Lei Jun")
                        .setSummary("secondary detailed summary")
                        .setDescription("secondary short description")
                        .setEvidenceUrls(secondaryUrls));
        when(summaryGenerationClient.applyComprehensiveJudgement(anyString(), anyList(), any(ResolvedPersonProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient, mock(SerpApiClient.class), jinaReaderClient, summaryGenerationClient, executor
        );

        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Lei Jun"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/seed"))));

        assertThat(result.getPerson().getImageUrl()).isEqualTo("https://img.example.com/lei-jun.jpg");
        assertThat(result.getPerson().getDescription()).isEqualTo("secondary short description (由大模型总结)");
        assertThat(result.getPerson().getSummary()).isEqualTo("secondary detailed summary (由大模型总结)");
        assertThat(result.getPerson().getEvidenceUrls()).contains("https://zh.wikipedia.org/wiki/%E9%9B%B7%E5%86%9B");
        assertThat(result.getPerson().getEvidenceUrls()).contains("https://baike.baidu.com/item/%E9%9B%B7%E5%86%9B/1968");
        assertThat(result.getWarnings()).doesNotContain("secondary_profile_search_unavailable");
    }

    @Test
    void shouldFallbackToWebEvidenceAndStillCallKimiWhenJinaFails() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenThrow(new RuntimeException("jina failed"));
        when(summaryGenerationClient.summarizePage(org.mockito.ArgumentMatchers.eq("Jay Chou"), org.mockito.ArgumentMatchers.argThat(page ->
                page != null
                        && "https://example.com/a".equals(page.getUrl())
                        && "Profile".equals(page.getTitle())
                        && page.getContent() != null
                        && page.getContent().contains("Mandopop singer")
                        && page.getContent().contains("Example News")
        ))).thenReturn(new PageSummary()
                .setSourceUrl("https://example.com/a")
                .setTitle("Profile")
                .setSummary("Page Summary"));
        when(summaryGenerationClient.summarizePersonFromPageSummaries(anyString(), anyList()))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("Jay Chou")
                        .setSummary("Jay Chou is a singer.")
                        .setEvidenceUrls(List.of("https://example.com/a")));
        when(summaryGenerationClient.applyComprehensiveJudgement(
                anyString(),
                anyList(),
                org.mockito.ArgumentMatchers.any(ResolvedPersonProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));

        AggregationResult result = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence()
                        .setUrl("https://example.com/a")
                        .setTitle("Profile")
                        .setSnippet("Mandopop singer")
                        .setSource("Example News")
                        .setSourceEngine("google"))));

        assertThat(result.getPerson().getDescription()).isEqualTo("Jay Chou is a singer. (由大模型总结)");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void shouldReturnEmptySocialAccountsWhenNoDigitalFootprintResultsAvailable() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        mockSummaryPipeline(summaryGenerationClient, "Jay Chou", pages, new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setSummary("Jay Chou is a singer."));

        AggregationResult result = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getSocialAccounts()).isEmpty();
    }

    @Test
    void shouldUseDigitalFootprintQueriesForContactInformationSection() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        DigitalFootprintQueryBuilder queryBuilder = mock(DigitalFootprintQueryBuilder.class);

        when(summaryGenerationClient.inferSearchLanguageProfile(anyString(), any()))
                .thenReturn(new SearchLanguageInferenceResult()
                        .setPrimaryNationality("US")
                        .setRecommendedLanguages(List.of("zh", "en"))
                        .setLocalizedNames(Map.of("zh", "周杰伦", "en", "Jay Chou"))
                        .setConfidence(0.8));
        when(queryBuilder.build(eq("Jay Chou"), any())).thenReturn(List.of(
                new DigitalFootprintQuery().setQueryText("Jay Chou email contact").setQueryType("email_contact").setPriority(1),
                new DigitalFootprintQuery().setQueryText("site:linkedin.com/in/ Jay Chou").setQueryType("social_profile").setPriority(2)
        ));

        ObjectMapper mapper = new ObjectMapper();
        when(googleSearchClient.googleSearch("周杰伦 公开通讯"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jay Chou contact information"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(googleSearchClient.googleSearch("Jay Chou email contact"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"Contact","link":"https://example.com/contact","snippet":"contact snippet"}]}
                        """)));
        when(googleSearchClient.googleSearch("site:linkedin.com/in/ Jay Chou"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("{\"organic\":[]}")));
        when(jinaReaderClient.readPages(List.of("https://example.com/contact")))
                .thenReturn(List.of(new PageContent().setUrl("https://example.com/contact").setTitle("Contact").setContent("contact body")));
        when(summaryGenerationClient.summarizePage(anyString(), any(PageContent.class)))
                .thenReturn(new PageSummary().setSourceUrl("https://example.com/contact").setTitle("Contact").setSummary("contact summary"));
        when(summaryGenerationClient.summarizeSectionFromPageSummaries(eq("Jay Chou"), eq("contact_information"), anyList()))
                .thenReturn("contact summary");

        InformationAggregationServiceImpl service = serviceWithBuilder(
                googleSearchClient,
                jinaReaderClient,
                summaryGenerationClient,
                queryBuilder,
                createApiProperties(null)
        );

        String summary = service.summarizeSection("Jay Chou", "contact_information", "unused fallback");

        assertThat(summary).isEqualTo("contact summary");
        verify(googleSearchClient).googleSearch("Jay Chou email contact");
    }

    @Test
    void shouldCollectSocialAccountsFromDigitalFootprintSearchResults() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        DigitalFootprintQueryBuilder queryBuilder = mock(DigitalFootprintQueryBuilder.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jensen Huang is a tech executive"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        mockSummaryPipeline(summaryGenerationClient, "Jensen Huang", pages, new ResolvedPersonProfile()
                .setResolvedName("Jensen Huang")
                .setSummary("Jensen Huang is a tech executive."));
        when(summaryGenerationClient.inferSearchLanguageProfile(anyString(), any()))
                .thenReturn(new SearchLanguageInferenceResult()
                        .setPrimaryNationality("US")
                        .setRecommendedLanguages(List.of("zh", "en"))
                        .setLocalizedNames(Map.of("zh", "黄仁勋", "en", "Jensen Huang"))
                        .setConfidence(0.9));
        when(queryBuilder.build(eq("Jensen Huang"), any())).thenReturn(List.of(
                new DigitalFootprintQuery().setQueryText("site:linkedin.com/in/ Jensen Huang").setPlatform("linkedin").setPriority(1),
                new DigitalFootprintQuery().setQueryText("site:twitter.com Jensen Huang").setPlatform("twitter").setPriority(2)
        ));

        ObjectMapper mapper = new ObjectMapper();
        when(googleSearchClient.googleSearch("site:linkedin.com/in/ Jensen Huang"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"Jensen Huang | LinkedIn","link":"https://www.linkedin.com/in/jensen-huang","snippet":"LinkedIn profile"}]}
                        """)));
        when(googleSearchClient.googleSearch("site:twitter.com Jensen Huang"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"Jensen Huang (@nvidia)","link":"https://twitter.com/nvidia","snippet":"X profile"}]}
                        """)));

        AggregationResult result = serviceWithBuilder(
                googleSearchClient,
                jinaReaderClient,
                summaryGenerationClient,
                queryBuilder,
                createApiProperties(null)
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jensen Huang"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getSocialAccounts())
                .extracting(SocialAccount::getPlatform)
                .contains("linkedin", "twitter");
        assertThat(result.getSocialAccounts())
                .noneMatch(account -> "pending".equals(account.getPlatform()));
    }

    @Test
    void shouldKeepAggregationFastWithoutStandaloneDetailAggregation() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenAnswer(invocation -> {
            Thread.sleep(250);
            return pages;
        });
        List<PageSummary> pageSummaries = List.of(new PageSummary()
                .setSourceUrl("https://example.com/a")
                .setTitle("A")
                .setSummary("Jay Chou is a singer"));
        when(summaryGenerationClient.summarizePage("Jay Chou", pages.get(0))).thenReturn(pageSummaries.get(0));
        doAnswer(invocation -> {
            Thread.sleep(250);
            return new ResolvedPersonProfile().setResolvedName("Jay Chou").setSummary("华语歌手");
        }).when(summaryGenerationClient).summarizePersonFromPageSummaries("Jay Chou", pageSummaries);

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor);

        Instant start = Instant.now();
        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));
        long elapsed = Duration.between(start, Instant.now()).toMillis();

        assertThat(elapsed).isLessThan(1000);
        assertThat(result.getSocialAccounts()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    private void mockSummaryPipeline(SummaryGenerationClient summaryGenerationClient,
                                     String fallbackName,
                                     List<PageContent> pages,
                                     ResolvedPersonProfile profile) {
        List<PageSummary> pageSummaries = new ArrayList<>();
        for (PageContent page : pages) {
            PageSummary pageSummary = new PageSummary()
                    .setSourceUrl(page.getUrl())
                    .setTitle(page.getTitle())
                    .setSummary(page.getContent());
            pageSummaries.add(pageSummary);
            when(summaryGenerationClient.summarizePage(fallbackName, page)).thenReturn(pageSummary);
        }
        when(summaryGenerationClient.summarizePersonFromPageSummaries(fallbackName, pageSummaries)).thenReturn(profile);
        when(summaryGenerationClient.applyComprehensiveJudgement(
                fallbackName,
                pageSummaries,
                profile)).thenReturn(profile);
    }

    private ApiProperties createApiProperties(Integer maxPageReads) {
        ApiProperties properties = new ApiProperties();
        if (maxPageReads != null) {
            properties.getApi().getJina().setMaxPageReads(maxPageReads);
        }
        properties.getApi().getQueryRewrite().getBaseQueryTemplates().put("china_related_statements", List.of(
                "%s 涉华言论",
                "%s 中国评价",
                "%s 中美关系",
                "%s 中欧关系"
        ));
        properties.getApi().getQueryRewrite().getBaseQueryTemplates().put("political_view", List.of(
                "%s 政治倾向",
                "%s 政党",
                "%s 政治理念",
                "%s 政策立场"
        ));
        properties.getApi().getQueryRewrite().getBaseQueryTemplates().put("contact_information", List.of(
                "%s 公开通讯",
                "%s 办公电话",
                "%s 官方邮箱",
                "%s 认证社交账号",
                "%s 联系方式"
        ));
        properties.getApi().getQueryRewrite().getBaseQueryTemplates().put("family_member_situation", List.of(
                "%s 家庭成员",
                "%s 亲属",
                "%s 经商",
                "%s 在华投资",
                "%s 商业纠纷"
        ));
        properties.getApi().getQueryRewrite().getBaseQueryTemplates().put("misconduct", List.of(
                "%s 违法记录",
                "%s 行政处罚",
                "%s 负面事件",
                "%s 失信行为"
        ));
        properties.getApi().getQueryRewrite().setExpandEnabledTopics(List.of(
                "china_related_statements",
                "political_view",
                "contact_information",
                "family_member_situation",
                "misconduct"
        ));
        properties.getApi().getQueryRewrite().setExpandMaxQueryCount(4);
        properties.getApi().getQueryRewrite().setExpandMaxTermLength(16);
        return properties;
    }

    private List<String> numberedUrls(String prefix, int count) {
        List<String> urls = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            urls.add(prefix + index);
        }
        return urls;
    }

    private String searchResponseJson(List<String> urls) {
        StringBuilder builder = new StringBuilder("{\"organic\":[");
        for (int index = 0; index < urls.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append("{\"title\":\"Article ")
                    .append(index + 1)
                    .append("\",\"link\":\"")
                    .append(urls.get(index))
                    .append("\",\"snippet\":\"snippet ")
                    .append(index + 1)
                    .append("\"}");
        }
        return builder.append("]}").toString();
    }

    private List<PageContent> pagesFor(List<String> urls) {
        return urls.stream()
                .map(url -> new PageContent()
                        .setUrl(url)
                        .setTitle("Article " + url)
                        .setContent("content " + url))
                .toList();
    }

    private InformationAggregationServiceImpl serviceWithBuilder(GoogleSearchClient googleSearchClient,
                                                                 JinaReaderClient jinaReaderClient,
                                                                 SummaryGenerationClient summaryGenerationClient,
                                                                 DigitalFootprintQueryBuilder queryBuilder,
                                                                 ApiProperties properties) {
        return new InformationAggregationServiceImpl(
                googleSearchClient,
                mock(SerpApiClient.class),
                jinaReaderClient,
                summaryGenerationClient,
                null,
                executor,
                properties,
                new DerivedTopicQueryService() {
                    @Override
                    public com.example.face2info.entity.internal.TopicQueryDecision resolveQuery(
                            com.example.face2info.entity.internal.DerivedTopicRequest request) {
                        return new com.example.face2info.entity.internal.TopicQueryDecision()
                                .setFinalQuery(request == null ? null : request.getRawQuery());
                    }
                },
                new SearchLanguageProfileServiceImpl(summaryGenerationClient),
                new MultilingualQueryPlanningServiceImpl((query, targetLanguageCode) -> null),
                queryBuilder,
                mock(PrimarySearchQueryBuilder.class)
        );
    }

    private ThreadPoolTaskExecutor executor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(4);
        taskExecutor.setMaxPoolSize(4);
        taskExecutor.setQueueCapacity(10);
        taskExecutor.initialize();
        return taskExecutor;
    }
}
