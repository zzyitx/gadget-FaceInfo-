package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.client.NewsApiClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.PersonBasicInfo;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.WebEvidence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

        assertThat(result.getPerson().getSummary()).isEqualTo("周杰伦是华语流行音乐代表人物。 (由 Kimi 总结)");
        assertThat(result.getPerson().getDescription()).isEqualTo("周杰伦是华语流行音乐代表人物。 (由 Kimi 总结)");
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

        assertThat(result.getPerson().getDescription()).isEqualTo("Short description (由 Kimi 总结)");
        assertThat(result.getPerson().getSummary()).isEqualTo("Long summary (由 Kimi 总结)");
        assertThat(result.getPerson().getWikipedia()).isEqualTo("https://example.com/wiki");
        assertThat(result.getPerson().getOfficialWebsite()).isEqualTo("https://example.com");
        assertThat(result.getPerson().getBasicInfo().getBirthDate()).isEqualTo("1979-01-18");
        verifyNoInteractions(googleSearchClient);
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

        assertThat(result.getPerson().getDescription()).isEqualTo("Jay Chou is a singer. (由 Kimi 总结)");
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
