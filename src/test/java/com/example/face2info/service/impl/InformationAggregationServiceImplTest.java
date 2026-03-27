package com.example.face2info.service.impl;

import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.client.NewsApiClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
                new InformationAggregationServiceImpl(serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

        ResolvedPersonProfile profile = service.resolveProfileFromEvidence(List.of(
                new WebEvidence().setUrl("https://example.com/a"),
                new WebEvidence().setUrl("https://example.com/b")
        ), "unknown");

        assertThat(profile.getResolvedName()).isEqualTo("Jay Chou");
        assertThat(profile.getSummary()).contains("Mandopop singer");
    }

    @Test
    void shouldMapSummaryAndTagsIntoAggregationResult() throws Exception {
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
        when(serpApiClient.googleSearch("周杰伦")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledge_graph\":{\"description\":\"后备简介\"}}")));
        when(serpApiClient.googleSearch("周杰伦 抖音")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
        when(serpApiClient.googleSearch("周杰伦 微博")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
        when(newsApiClient.searchNews("周杰伦")).thenReturn(new NewsApiResponse()
                .setRoot(objectMapper.readTree("{\"articles\":[]}")));

        AggregationResult result = new InformationAggregationServiceImpl(
                serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("周杰伦"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getSummary()).isEqualTo("周杰伦是华语流行乐代表人物。");
        assertThat(result.getPerson().getTags()).containsExactly("歌手", "音乐制作人");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void shouldAppendWarningWhenSummaryGenerationFails() throws Exception {
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setContent("正文A"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePerson("周杰伦", pages)).thenThrow(new RuntimeException("INVALID_RESPONSE"));
        when(serpApiClient.googleSearch("周杰伦")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledge_graph\":{\"description\":\"后备简介\"}}")));
        when(serpApiClient.googleSearch("周杰伦 抖音")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
        when(serpApiClient.googleSearch("周杰伦 微博")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
        when(newsApiClient.searchNews("周杰伦")).thenReturn(new NewsApiResponse()
                .setRoot(objectMapper.readTree("{\"articles\":[]}")));

        AggregationResult result = new InformationAggregationServiceImpl(
                serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor
        ).aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("周杰伦"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getDescription()).isEqualTo("后备简介");
        assertThat(result.getPerson().getSummary()).isNull();
        assertThat(result.getPerson().getTags()).isEmpty();
        assertThat(result.getWarnings()).containsExactly("正文智能处理暂时不可用");
    }

    @Test
    void shouldUseResolvedNameForGoogleNewsAndSocialAggregation() throws Exception {
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
        when(serpApiClient.googleSearch("Jay Chou")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledge_graph\":{\"description\":\"Fallback\",\"website\":\"https://jay.example.com\"}}")));
        when(serpApiClient.googleSearch("Jay Chou 抖音")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
        when(serpApiClient.googleSearch("Jay Chou 微博")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
        when(newsApiClient.searchNews("Jay Chou")).thenReturn(new NewsApiResponse()
                .setRoot(objectMapper.readTree("{\"articles\":[]}")));

        InformationAggregationServiceImpl service =
                new InformationAggregationServiceImpl(serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("unknown"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getName()).isEqualTo("Jay Chou");
        assertThat(result.getPerson().getDescription()).isEqualTo("Jay Chou is a Mandopop singer.");
    }

    @Test
    void shouldReturnPartialDataWhenNewsFails() throws Exception {
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

        List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer"));
        when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
        when(summaryGenerationClient.summarizePerson("Jay Chou", pages)).thenReturn(new ResolvedPersonProfile()
                .setResolvedName("Jay Chou")
                .setSummary(""));
        when(serpApiClient.googleSearch("Jay Chou 抖音")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"organic_results\": []}")));
        when(serpApiClient.googleSearch("Jay Chou 微博")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"organic_results\": []}")));
        when(serpApiClient.googleSearch("Jay Chou")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledge_graph\": {\"description\": \"华语歌手\"}}")));
        when(newsApiClient.searchNews("Jay Chou")).thenThrow(new RuntimeException("连接超时"));

        InformationAggregationServiceImpl service =
                new InformationAggregationServiceImpl(serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

        assertThat(result.getPerson().getDescription()).isEqualTo("华语歌手");
        assertThat(result.getNews()).isEmpty();
        assertThat(result.getErrors()).singleElement().asString().contains("新闻获取失败");
    }

    @Test
    void shouldAggregateInParallelAndDeduplicate() throws Exception {
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
            String query = invocation.getArgument(0);
            if ("Jay Chou".equals(query)) {
                return new SerpApiResponse().setRoot(objectMapper.readTree("""
                        {
                          "knowledge_graph": {
                            "description": "华语歌手",
                            "website": "https://jay.example.com",
                            "wikipedia": "https://zh.wikipedia.org/wiki/Jay_Chou"
                          }
                        }
                        """));
            }
            return new SerpApiResponse().setRoot(objectMapper.readTree("""
                    {
                      "organic_results": [
                        { "title": "Jay Chou - 微博", "link": "https://weibo.com/jaychou" },
                        { "title": "Jay Chou - 微博", "link": "https://weibo.com/jaychou" },
                        { "title": "Jay Chou - 抖音", "link": "https://www.douyin.com/user/abc" }
                      ]
                    }
                    """));
        }).when(serpApiClient).googleSearch(anyString());

        when(newsApiClient.searchNews("Jay Chou")).thenAnswer(invocation -> {
            Thread.sleep(250);
            return new NewsApiResponse().setRoot(objectMapper.readTree("""
                    {
                      "articles": [
                        {
                          "title": "Jay Chou 新歌发布",
                          "description": "摘要A",
                          "publishedAt": "2025-03-20T10:00:00Z",
                          "url": "https://news.example.com/a",
                          "source": { "name": "新浪娱乐" }
                        },
                        {
                          "title": "Jay Chou 新歌发布",
                          "description": "摘要A",
                          "publishedAt": "2025-03-20T10:00:00Z",
                          "url": "https://news.example.com/b",
                          "source": { "name": "新浪娱乐" }
                        },
                        {
                          "title": "无关新闻",
                          "description": "摘要B",
                          "publishedAt": "2025-03-20T11:00:00Z",
                          "url": "https://news.example.com/c",
                          "source": { "name": "其他来源" }
                        }
                      ]
                    }
                    """));
        });

        InformationAggregationServiceImpl service =
                new InformationAggregationServiceImpl(serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

        Instant start = Instant.now();
        AggregationResult result = service.aggregate(new RecognitionEvidence()
                .setSeedQueries(List.of("Jay Chou"))
                .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));
        long elapsed = Duration.between(start, Instant.now()).toMillis();

        assertThat(elapsed).isLessThan(1000);
        assertThat(result.getSocialAccounts()).hasSize(2);
        assertThat(result.getNews()).hasSize(1);
        assertThat(result.getPerson().getDescription()).isEqualTo("华语歌手");
        assertThat(result.getErrors()).isEmpty();
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
