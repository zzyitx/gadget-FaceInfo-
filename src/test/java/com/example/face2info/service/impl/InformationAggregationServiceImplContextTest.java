package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.service.DerivedTopicQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(classes = InformationAggregationServiceImplContextTest.TestConfig.class)
class InformationAggregationServiceImplContextTest {

    @Autowired
    private InformationAggregationServiceImpl service;

    @Test
    void shouldCreateInformationAggregationServiceBeanFromSpringContext() {
        assertThat(service).isNotNull();
    }

    @TestConfiguration
    @Import(InformationAggregationServiceImpl.class)
    static class TestConfig {

        @Bean
        GoogleSearchClient googleSearchClient() {
            return mock(GoogleSearchClient.class);
        }

        @Bean
        SerpApiClient serpApiClient() {
            return mock(SerpApiClient.class);
        }

        @Bean
        JinaReaderClient jinaReaderClient() {
            return mock(JinaReaderClient.class);
        }

        @Bean
        SummaryGenerationClient summaryGenerationClient() {
            return mock(SummaryGenerationClient.class);
        }

        @Bean
        DerivedTopicQueryService derivedTopicQueryService() {
            return mock(DerivedTopicQueryService.class);
        }

        @Bean
        @Qualifier("face2InfoExecutor")
        ThreadPoolTaskExecutor face2InfoExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(1);
            executor.setThreadNamePrefix("test-face2info-");
            executor.initialize();
            return executor;
        }

        @Bean
        ApiProperties apiProperties() {
            return new ApiProperties();
        }
    }
}
