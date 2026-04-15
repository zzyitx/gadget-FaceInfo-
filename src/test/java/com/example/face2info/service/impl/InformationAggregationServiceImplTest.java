package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.client.NewsApiClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.client.impl.DeepSeekSummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.PersonBasicInfo;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.internal.WebEvidence;
import com.example.face2info.exception.ApiCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
                    mock(NewsApiClient.class),
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
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
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
        when(deepSeekClient.applyComprehensiveJudgement(eq("unknown"), eq(List.of(deepSeekSummary)), any(ResolvedPersonProfile.class)))
                .thenReturn(new ResolvedPersonProfile().setResolvedName("Jay Chou").setSummary("DeepSeek final"));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
                jinaReaderClient, kimiClient, deepSeekClient, executor, properties
        );

        service.resolveProfileFromEvidence(List.of(new WebEvidence().setUrl("https://example.com/a")), "unknown", List.of());

        verify(deepSeekClient).summarizePage("unknown", longPage);
        verify(kimiClient, never()).summarizePage("unknown", longPage);
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
            when(googleSearchClient.googleSearch("Jay Chou的教育经历"))
                    .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                            {"organic":[{"title":"Education","link":"https://example.com/education","snippet":"education snippet"}]}
                            """)));
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
                googleSearchClient, mock(SerpApiClient.class), mock(NewsApiClient.class),
                jinaReaderClient, kimiClient, deepSeekClient, executor, createApiProperties(null)
        );

        String summary = service.summarizeSection("Jay Chou", "education", "Jay Chou的教育经历");

        assertThat(summary).isEqualTo("education summary");
        verify(kimiClient).summarizeSectionFromPageSummaries("Jay Chou", "education", List.of(pageSummary));
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
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
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
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
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
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
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
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("unknown"))
                .setWebEvidences(List.of(
                        new WebEvidence().setUrl("https://example.com/a"),
                        new WebEvidence().setUrl("https://example.com/b")
                )));

        assertThat(result.getPerson().getName()).isEqualTo("unknown");
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
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
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
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
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
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
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
                googleSearchClient, mock(SerpApiClient.class), mock(NewsApiClient.class), jinaReaderClient, summaryGenerationClient, executor
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
    void shouldJoinBaseSummaryWithThreeSectionSummariesInOrder() {
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
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getSummary()).isEqualTo(
                "base summary\n\neducation summary\n\nfamily summary\n\ncareer summary (由大模型总结)"
        );
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
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
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
    void shouldSkipFailedSectionAndKeepRemainingOrder() {
        ResolvedPersonProfile profile = new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setDescription("short intro")
                .setSummary("base summary")
                .setEducationSummary("education summary")
                .setCareerSummary("career summary");

        assertThat(new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
                mock(JinaReaderClient.class), mock(SummaryGenerationClient.class), executor
        ).buildFinalSummary(profile)).isEqualTo("base summary\n\neducation summary\n\ncareer summary");
    }

    @Test
    void shouldFallbackToBaseSummaryWhenAllSectionsMissing() {
        ResolvedPersonProfile profile = new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setDescription("short intro")
                .setSummary("base summary");

        assertThat(new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
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
        when(googleSearchClient.googleSearch("Jay Chou的教育经历")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[{"title":"Education","link":"https://example.com/education","snippet":"education snippet"}]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou的家庭背景")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[{"title":"Family","link":"https://example.com/family","snippet":"family snippet"}]}
                """)));
        when(googleSearchClient.googleSearch("Jay Chou的职业经历")).thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                {"organic":[{"title":"Career","link":"https://example.com/career","snippet":"career snippet"}]}
                """)));

        PageContent educationPage = new PageContent().setUrl("https://example.com/education").setTitle("Education").setContent("education body");
        PageContent familyPage = new PageContent().setUrl("https://example.com/family").setTitle("Family").setContent("family body");
        PageContent careerPage = new PageContent().setUrl("https://example.com/career").setTitle("Career").setContent("career body");
        when(jinaReaderClient.readPages(List.of("https://example.com/education"))).thenReturn(List.of(educationPage));
        when(jinaReaderClient.readPages(List.of("https://example.com/family"))).thenReturn(List.of(familyPage));
        when(jinaReaderClient.readPages(List.of("https://example.com/career"))).thenReturn(List.of(careerPage));

        PageSummary educationSummary = new PageSummary().setSourceUrl("https://example.com/education").setTitle("Education").setSummary("education page");
        PageSummary familySummary = new PageSummary().setSourceUrl("https://example.com/family").setTitle("Family").setSummary("family page");
        PageSummary careerSummary = new PageSummary().setSourceUrl("https://example.com/career").setTitle("Career").setSummary("career page");
        when(summaryGenerationClient.summarizePage("Jay Chou", educationPage)).thenReturn(educationSummary);
        when(summaryGenerationClient.summarizePage("Jay Chou", familyPage)).thenReturn(familySummary);
        when(summaryGenerationClient.summarizePage("Jay Chou", careerPage)).thenReturn(careerSummary);
        when(summaryGenerationClient.summarizeSectionFromPageSummaries("Jay Chou", "education", List.of(educationSummary)))
                .thenReturn("education summary");
        when(summaryGenerationClient.summarizeSectionFromPageSummaries("Jay Chou", "family", List.of(familySummary)))
                .thenReturn("family summary");
        when(summaryGenerationClient.summarizeSectionFromPageSummaries("Jay Chou", "career", List.of(careerSummary)))
                .thenReturn("career summary");

        AggregationResult result = new InformationAggregationServiceImpl(
                googleSearchClient, mock(SerpApiClient.class), mock(NewsApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getDescription()).isEqualTo("short intro (由大模型总结)");
        assertThat(result.getPerson().getSummary()).isEqualTo(
                "base summary\n\neducation summary\n\nfamily summary\n\ncareer summary (由大模型总结)"
        );
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
                googleSearchClient, mock(SerpApiClient.class), mock(NewsApiClient.class),
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
                "Jay Chou",
                List.of(pageSummary),
                draftProfile)).thenReturn(judgedProfile);

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        );

        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou", "周杰伦"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getName()).isEqualTo("Jay Chou");
        assertThat(result.getPerson().getSummary()).isEqualTo("judged summary (由大模型总结)");
        verify(summaryGenerationClient).applyComprehensiveJudgement(
                "Jay Chou",
                List.of(pageSummary),
                draftProfile);
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
                googleSearchClient, mock(SerpApiClient.class), mock(NewsApiClient.class), jinaReaderClient, summaryGenerationClient, executor
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
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
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
    void shouldReturnPlaceholderSocialAccountWithoutCallingNewsApi() {
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        mockSummaryPipeline(summaryGenerationClient, "Jay Chou", pages, new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setSummary("Jay Chou is a singer."));

        AggregationResult result = new InformationAggregationServiceImpl(
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getSocialAccounts()).hasSize(1);
        assertThat(result.getSocialAccounts().get(0).getUsername()).isEqualTo("功能正在开发中");
        assertThat(result.getNews()).isEmpty();
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
                mock(GoogleSearchClient.class), mock(SerpApiClient.class), mock(NewsApiClient.class),
                jinaReaderClient, summaryGenerationClient, executor);

        Instant start = Instant.now();
        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));
        long elapsed = Duration.between(start, Instant.now()).toMillis();

        assertThat(elapsed).isLessThan(1000);
        assertThat(result.getSocialAccounts()).hasSize(1);
        assertThat(result.getNews()).isEmpty();
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
        return properties;
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
