package com.example.face2info.service.impl;

import com.example.face2info.client.NewsApiClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.NewsApiResponse;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 信息聚合服务测试。
 */
class InformationAggregationServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ThreadPoolTaskExecutor executor = executor();

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void shouldAggregateInParallelAndDeduplicate() throws Exception {
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);

        doAnswer(invocation -> {
            Thread.sleep(250);
            String query = invocation.getArgument(0);
            if ("周杰伦".equals(query)) {
                return new SerpApiResponse().setRoot(objectMapper.readTree("""
                        {
                          "knowledge_graph": {
                            "description": "华语歌手",
                            "website": "https://jay.example.com",
                            "wikipedia": "https://zh.wikipedia.org/wiki/周杰伦"
                          }
                        }
                        """));
            }
            return new SerpApiResponse().setRoot(objectMapper.readTree("""
                    {
                      "organic_results": [
                        { "title": "周杰伦 - 微博", "link": "https://weibo.com/jaychou" },
                        { "title": "周杰伦 - 微博", "link": "https://weibo.com/jaychou" },
                        { "title": "周杰伦 - 抖音", "link": "https://www.douyin.com/user/abc" }
                      ]
                    }
                    """));
        }).when(serpApiClient).googleSearch(anyString());

        when(newsApiClient.searchNews("周杰伦")).thenAnswer(invocation -> {
            Thread.sleep(250);
            return new NewsApiResponse().setRoot(objectMapper.readTree("""
                    {
                      "articles": [
                        {
                          "title": "周杰伦新歌发布",
                          "description": "摘要A",
                          "publishedAt": "2025-03-20T10:00:00Z",
                          "url": "https://news.example.com/a",
                          "source": { "name": "新浪娱乐" }
                        },
                        {
                          "title": "周杰伦新歌发布",
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
                new InformationAggregationServiceImpl(serpApiClient, newsApiClient, executor);

        Instant start = Instant.now();
        AggregationResult result = service.aggregate("周杰伦");
        long elapsed = Duration.between(start, Instant.now()).toMillis();

        assertThat(elapsed).isLessThan(650);
        assertThat(result.getSocialAccounts()).hasSize(2);
        assertThat(result.getNews()).hasSize(1);
        assertThat(result.getPerson().getDescription()).isEqualTo("华语歌手");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldReturnPartialDataWhenNewsFails() throws Exception {
        SerpApiClient serpApiClient = mock(SerpApiClient.class);
        NewsApiClient newsApiClient = mock(NewsApiClient.class);

        when(serpApiClient.googleSearch("周杰伦 抖音")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"organic_results\": []}")));
        when(serpApiClient.googleSearch("周杰伦 微博")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"organic_results\": []}")));
        when(serpApiClient.googleSearch("周杰伦")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledge_graph\": {\"description\": \"华语歌手\"}}")));
        when(newsApiClient.searchNews("周杰伦")).thenThrow(new RuntimeException("连接超时"));

        InformationAggregationServiceImpl service =
                new InformationAggregationServiceImpl(serpApiClient, newsApiClient, executor);

        AggregationResult result = service.aggregate("周杰伦");

        assertThat(result.getPerson().getDescription()).isEqualTo("华语歌手");
        assertThat(result.getNews()).isEmpty();
        assertThat(result.getErrors()).singleElement().asString().contains("新闻获取失败");
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
