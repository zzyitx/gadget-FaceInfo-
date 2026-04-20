package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.entity.internal.DerivedTopicRequest;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.internal.TopicQueryDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InformationAggregationDerivedTopicIntegrationTest {

    private final ThreadPoolTaskExecutor executor = executor();

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void shouldUseDerivedTopicGatewayBeforeSectionSearch() throws Exception {
        GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
        JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        DerivedTopicQueryServiceImpl derivedTopicQueryService = mock(DerivedTopicQueryServiceImpl.class);

        when(derivedTopicQueryService.resolveQuery(argThat(request ->
                request != null
                        && request.getTopicType() != null
                        && "Jay Chou".equals(request.getResolvedName())
                        && "Jay Chou的教育经历".equals(request.getRawQuery())
        ))).thenReturn(new TopicQueryDecision().setFinalQuery("Jay Chou 的教育经历"));

        ObjectMapper mapper = new ObjectMapper();
        when(googleSearchClient.googleSearch("Jay Chou 的教育经历"))
                .thenReturn(new SerpApiResponse().setRoot(mapper.readTree("""
                        {"organic":[{"title":"Education","link":"https://example.com/education","snippet":"education snippet"}]}
                        """)));

        PageContent page = new PageContent().setUrl("https://example.com/education").setTitle("Education").setContent("education body");
        PageSummary pageSummary = new PageSummary().setSourceUrl("https://example.com/education").setTitle("Education").setSummary("education summary");
        when(jinaReaderClient.readPages(List.of("https://example.com/education"))).thenReturn(List.of(page));
        when(summaryGenerationClient.summarizePage("Jay Chou", page)).thenReturn(pageSummary);
        when(summaryGenerationClient.summarizeSectionFromPageSummaries("Jay Chou", "education", List.of(pageSummary)))
                .thenReturn("education summary");

        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                googleSearchClient,
                mock(SerpApiClient.class),
                jinaReaderClient,
                summaryGenerationClient,
                null,
                executor,
                new com.example.face2info.config.ApiProperties(),
                derivedTopicQueryService
        );

        String summary = service.summarizeSection("Jay Chou", "education", "Jay Chou的教育经历");

        assertThat(summary).isEqualTo("education summary");
        verify(derivedTopicQueryService).resolveQuery(argThat(request ->
                request != null
                        && request.getTopicType() != null
                        && "Jay Chou".equals(request.getResolvedName())
                        && "Jay Chou的教育经历".equals(request.getRawQuery())
        ));
        verify(googleSearchClient).googleSearch("Jay Chou 的教育经历");
    }

    private ThreadPoolTaskExecutor executor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(2);
        taskExecutor.setMaxPoolSize(2);
        taskExecutor.setQueueCapacity(10);
        taskExecutor.initialize();
        return taskExecutor;
    }
}
