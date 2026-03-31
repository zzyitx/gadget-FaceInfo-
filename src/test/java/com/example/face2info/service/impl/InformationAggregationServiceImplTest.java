package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.client.NewsApiClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.NewsApiResponse;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.internal.WebEvidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InformationAggregationServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
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
    void shouldUseJinaPagesAsSummaryInput() {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(
                new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer"),
                new PageContent().setUrl("https://example.com/b").setTitle("B").setContent("Jay Chou released an album")
        );
        when(jinaReaderClient.readPages(List.of("https://example.com/a", "https://example.com/b"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePerson("unknown", pages))
                .thenReturn(new ResolvedPersonProfile().setResolvedName("Jay Chou").setSummary("Jay Chou is a Mandopop singer."));

        InformationAggregationServiceImpl service =
                new InformationAggregationServiceImpl(googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

        ResolvedPersonProfile profile = service.resolveProfileFromEvidence(List.of(
                new WebEvidence().setUrl("https://example.com/a"),
                new WebEvidence().setUrl("https://example.com/b")
        ), "unknown");

        assertThat(profile.getResolvedName()).isEqualTo("Jay Chou");
        assertThat(profile.getSummary()).contains("Mandopop singer");
    }

    @Test
    void shouldOnlyReadTopFiveUrlsByDefault() {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<String> expectedUrls = List.of(
                "https://example.com/1",
                "https://example.com/2",
                "https://example.com/3",
                "https://example.com/4",
                "https://example.com/5"
        );
        when(jinaReaderClient.readPages(expectedUrls)).thenReturn(List.of());
        when(summaryGenerationClient.summarizePerson("unknown", List.of()))
                .thenReturn(new ResolvedPersonProfile().setResolvedName("unknown"));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient,
                serpApiClient,
                newsApiClient,
                jinaReaderClient,
                summaryGenerationClient,
                executor,
                createApiProperties(null)
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
        verify(jinaReaderClient, never()).readPages(List.of(
                "https://example.com/1",
                "https://example.com/2",
                "https://example.com/3",
                "https://example.com/4",
                "https://example.com/5",
                "https://example.com/6",
                "https://example.com/7"
        ));
    }

    @Test
    void shouldRespectConfiguredMaxPageReads() {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<String> expectedUrls = List.of(
                "https://example.com/1",
                "https://example.com/2",
                "https://example.com/3"
        );
        when(jinaReaderClient.readPages(expectedUrls)).thenReturn(List.of());
        when(summaryGenerationClient.summarizePerson("unknown", List.of()))
                .thenReturn(new ResolvedPersonProfile().setResolvedName("unknown"));

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient,
                serpApiClient,
                newsApiClient,
                jinaReaderClient,
                summaryGenerationClient,
                executor,
                createApiProperties(3)
        );

        service.resolveProfileFromEvidence(List.of(
                new WebEvidence().setUrl("https://example.com/1"),
                new WebEvidence().setUrl("https://example.com/2"),
                new WebEvidence().setUrl("https://example.com/3"),
                new WebEvidence().setUrl("https://example.com/4"),
                new WebEvidence().setUrl("https://example.com/5")
        ), "unknown");

        verify(jinaReaderClient).readPages(expectedUrls);
        verify(jinaReaderClient, never()).readPages(List.of(
                "https://example.com/1",
                "https://example.com/2",
                "https://example.com/3",
                "https://example.com/4",
                "https://example.com/5"
        ));
    }

    @Test
    void shouldMapSummaryAndTagsIntoAggregationResult() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setContent("正文A"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePerson("周杰伦", pages)).thenReturn(new ResolvedPersonProfile()
                .setResolvedName("周杰伦")
                .setSummary("周杰伦是华语流行乐代表人物。")
                .setTags(List.of("歌手", "音乐制作人"))
                .setEvidenceUrls(List.of("https://example.com/a")));
        when(googleSearchClient.googleSearch("周杰伦")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledge_graph\":{\"description\":\"后备简介\"}}")));
        when(newsApiClient.searchNews("周杰伦")).thenReturn(new NewsApiResponse()
                .setRoot(objectMapper.readTree("{\"articles\":[]}")));

        AggregationResult result = new InformationAggregationServiceImpl(
                googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("周杰伦"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getSummary()).isEqualTo("周杰伦是华语流行乐代表人物。 (由 Kimi 总结)");
        assertThat(result.getPerson().getDescription()).isEqualTo("周杰伦是华语流行乐代表人物。 (由 Kimi 总结)");
        assertThat(result.getPerson().getTags()).containsExactly("歌手", "音乐制作人");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void shouldAppendWarningWhenSummaryGenerationFails() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setContent("正文A"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePerson("周杰伦", pages)).thenThrow(new RuntimeException("INVALID_RESPONSE"));
        when(googleSearchClient.googleSearch("周杰伦")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledge_graph\":{\"description\":\"后备简介\"}}")));
        when(newsApiClient.searchNews("周杰伦")).thenReturn(new NewsApiResponse()
                .setRoot(objectMapper.readTree("{\"articles\":[]}")));

        AggregationResult result = new InformationAggregationServiceImpl(
                googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("周杰伦"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getDescription()).isEqualTo("后备简介 (由 SerpAPI 聚合)");
        assertThat(result.getPerson().getSummary()).isNull();
        assertThat(result.getPerson().getTags()).isEmpty();
        assertThat(result.getWarnings()).hasSize(1);
    }

    @Test
    void shouldFallbackToWebEvidenceAndStillCallKimiWhenJinaFails() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenThrow(new RuntimeException("jina failed"));
        when(summaryGenerationClient.summarizePerson(
                org.mockito.ArgumentMatchers.eq("Jay Chou"),
                argThat(pages -> pages != null
                        && pages.size() == 1
                        && "https://example.com/a".equals(pages.get(0).getUrl())
                        && "Profile".equals(pages.get(0).getTitle())
                        && pages.get(0).getContent() != null
                        && pages.get(0).getContent().contains("Mandopop singer")
                        && pages.get(0).getContent().contains("Example News"))))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("Jay Chou")
                        .setSummary("Jay Chou is a singer.")
                        .setEvidenceUrls(List.of("https://example.com/a")));
        when(googleSearchClient.googleSearch("JayChou")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledgeGraph\":{\"description\":\"Fallback description\"}}")));

        AggregationResult result = new InformationAggregationServiceImpl(
                googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor
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
    void shouldFallbackToWebEvidenceAndStillCallKimiWhenJinaReturnsEmptyPages() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(List.of());
        when(summaryGenerationClient.summarizePerson(
                org.mockito.ArgumentMatchers.eq("Jay Chou"),
                argThat(pages -> pages != null
                        && pages.size() == 1
                        && "https://example.com/a".equals(pages.get(0).getUrl())
                        && pages.get(0).getContent() != null
                        && pages.get(0).getContent().contains("Mandopop singer"))))
                .thenReturn(new ResolvedPersonProfile()
                        .setResolvedName("Jay Chou")
                        .setSummary("Jay Chou is a singer.")
                        .setEvidenceUrls(List.of("https://example.com/a")));
        when(googleSearchClient.googleSearch("JayChou")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledgeGraph\":{\"description\":\"Fallback description\"}}")));

        AggregationResult result = new InformationAggregationServiceImpl(
                googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence()
                        .setUrl("https://example.com/a")
                        .setTitle("Profile")
                        .setSnippet("Mandopop singer")
                        .setSourceEngine("google"))));

        assertThat(result.getPerson().getDescription()).isEqualTo("Jay Chou is a singer. (由 Kimi 总结)");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void shouldUseResolvedNameForGoogleNewsAndSocialAggregation() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePerson("unknown", pages)).thenReturn(new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setSummary("Jay Chou is a Mandopop singer.")
                .setEvidenceUrls(List.of("https://example.com/a")));
        when(googleSearchClient.googleSearch("JayChou")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledgeGraph\":{\"description\":\"Fallback\",\"website\":\"https://jay.example.com\"}}")));
        when(newsApiClient.searchNews("Jay Chou")).thenReturn(new NewsApiResponse()
                .setRoot(objectMapper.readTree("{\"articles\":[]}")));

        InformationAggregationServiceImpl service =
                new InformationAggregationServiceImpl(googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("unknown"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getName()).isEqualTo("Jay Chou");
        assertThat(result.getPerson().getDescription()).isEqualTo("Jay Chou is a Mandopop singer. (由 Kimi 总结)");
    }

    @Test
    void shouldRemoveWhitespaceFromResolvedNameBeforeSearching() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Lei Jun profile"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePerson("unknown", pages)).thenReturn(new ResolvedPersonProfile()
                .setResolvedName("Lei Jun")
                .setSummary("Lei Jun is an entrepreneur.")
                .setEvidenceUrls(List.of("https://example.com/a")));
        when(googleSearchClient.googleSearch("LeiJun")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledgeGraph\":{\"description\":\"Fallback\"}}")));

        InformationAggregationServiceImpl service =
                new InformationAggregationServiceImpl(googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("unknown"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getName()).isEqualTo("Lei Jun");
        verify(googleSearchClient).googleSearch("LeiJun");
        verify(googleSearchClient, never()).googleSearch("Lei Jun");
    }

    @Test
    void shouldReturnPlaceholderSocialAccountWithoutCallingSocialSearchApi() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePerson("Jay Chou", pages)).thenReturn(new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setSummary("Jay Chou is a singer."));
        when(googleSearchClient.googleSearch("JayChou")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledge_graph\":{\"description\":\"Fallback description\"}}")));

        AggregationResult result = new InformationAggregationServiceImpl(
                googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getSocialAccounts()).hasSize(1);
        assertThat(result.getSocialAccounts().get(0).getUsername()).isEqualTo("功能正在开发中");
        verify(googleSearchClient).googleSearch("JayChou");
    }

    @Test
    void shouldReturnPartialDataWhenNewsFails() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePerson("Jay Chou", pages)).thenReturn(new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setSummary(""));
        when(googleSearchClient.googleSearch("JayChou")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledge_graph\": {\"description\": \"华语歌手\"}}")));

        InformationAggregationServiceImpl service =
                new InformationAggregationServiceImpl(googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getDescription()).isEqualTo("华语歌手 (由 SerpAPI 聚合)");
        assertThat(result.getNews()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
        verifyNoInteractions(newsApiClient);
    }

    @Test
    void shouldAggregateInParallelAndDeduplicate() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(
                new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer")
        );
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenAnswer(invocation -> {
            Thread.sleep(250);
            return pages;
        });
        when(summaryGenerationClient.summarizePerson("Jay Chou", pages))
                .thenReturn(new ResolvedPersonProfile().setResolvedName("Jay Chou").setSummary("华语歌手"));

        doAnswer(invocation -> {
            Thread.sleep(250);
            return new SerpApiResponse().setRoot(objectMapper.readTree("""
                    {
                      "knowledgeGraph": {
                        "description": "华语歌手",
                        "website": "https://jay.example.com",
                        "wikipedia": "https://zh.wikipedia.org/wiki/Jay_Chou"
                      }
                    }
                    """));
        }).when(googleSearchClient).googleSearch(anyString());

        InformationAggregationServiceImpl service =
                new InformationAggregationServiceImpl(googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

        Instant start = Instant.now();
        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));
        long elapsed = Duration.between(start, Instant.now()).toMillis();

        assertThat(elapsed).isLessThan(1000);
        assertThat(result.getSocialAccounts()).hasSize(1);
        assertThat(result.getSocialAccounts().get(0).getUsername()).isEqualTo("功能正在开发中");
        assertThat(result.getNews()).isEmpty();
        assertThat(result.getPerson().getDescription()).isEqualTo("华语歌手 (由 Kimi 总结)");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldSkipNewsAggregationAndReturnEmptyNews() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePerson("Jay Chou", pages)).thenReturn(new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setSummary("Jay Chou is a singer"));
        when(googleSearchClient.googleSearch(anyString())).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledgeGraph\":{\"description\":\"Mandopop singer\"}}")));

        InformationAggregationServiceImpl service =
                new InformationAggregationServiceImpl(googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getNews()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
        verifyNoInteractions(newsApiClient);
    }

    @Test
    void shouldAppendKimiSuffixToSummaryAndDescription() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setContent("body"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePerson("Jay Chou", pages)).thenReturn(new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setSummary("Jay Chou is a singer."));
        when(googleSearchClient.googleSearch("JayChou")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledgeGraph\":{\"description\":\"Fallback description\"}}")));

        AggregationResult result = new InformationAggregationServiceImpl(
                googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getSummary()).isEqualTo("Jay Chou is a singer. (由 Kimi 总结)");
        assertThat(result.getPerson().getDescription()).isEqualTo("Jay Chou is a singer. (由 Kimi 总结)");
    }

    @Test
    void shouldAppendSerpApiSuffixWhenSummaryUnavailable() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setContent("body"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePerson("Jay Chou", pages)).thenThrow(new RuntimeException("INVALID_RESPONSE"));
        when(googleSearchClient.googleSearch("JayChou")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledgeGraph\":{\"description\":\"Fallback description\"}}")));

        AggregationResult result = new InformationAggregationServiceImpl(
                googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getSummary()).isNull();
        assertThat(result.getPerson().getDescription()).isEqualTo("Fallback description (由 SerpAPI 聚合)");
    }

    @Test
    void shouldUseSerperOrganicSnippetAsFallbackDescription() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setContent("body"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePerson("Lei Jun", pages)).thenThrow(new RuntimeException("INVALID_RESPONSE"));
        when(googleSearchClient.googleSearch("LeiJun")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "organic": [
                            {
                              "title": "Lei Jun Douyin",
                              "link": "https://www.douyin.com/wiki/lei-jun",
                              "source": "Douyin",
                              "snippet": "Lei Jun is Xiaomi founder."
                            }
                          ]
                        }
                        """)));

        AggregationResult result = new InformationAggregationServiceImpl(
                googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Lei Jun"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getSummary()).isNull();
        assertThat(result.getPerson().getDescription()).isEqualTo("Lei Jun is Xiaomi founder. (由 SerpAPI 聚合)");
    }

    private SerpApiResponse emptySerpResponse() throws Exception {
        return new SerpApiResponse().setRoot(objectMapper.readTree("{\"organic\":[]}"));
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
