package com.example.face2info.client.impl;

import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("测试类：SummaryGenerationClientProviderConditionTest")
class SummaryGenerationClientProviderConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @DisplayName("功能测试：shouldRegisterDeepSeekSummaryClientWhenProviderIsDeepSeek")
    @Test
    void shouldRegisterDeepSeekSummaryClientWhenProviderIsDeepSeek() {
        contextRunner
                .withPropertyValues("face2info.api.summary.provider=deepseek")
                .run(context -> {
                    Map<String, SummaryGenerationClient> clients = context.getBeansOfType(SummaryGenerationClient.class);
                    assertThat(clients).hasSize(2);
                    assertThat(clients.values())
                            .hasAtLeastOneElementOfType(DeepSeekSummaryGenerationClient.class)
                            .hasAtLeastOneElementOfType(KimiSummaryGenerationClient.class);
                    assertThat(context.getBean(SummaryGenerationClient.class))
                            .isInstanceOf(DeepSeekSummaryGenerationClient.class);
                    assertThat(context).hasSingleBean(DeepSeekSummaryGenerationClient.class);
                    assertThat(context).hasSingleBean(KimiSummaryGenerationClient.class);
                });
    }

    @DisplayName("功能测试：shouldRegisterKimiSummaryClientWhenProviderIsKimi")
    @Test
    void shouldRegisterKimiSummaryClientWhenProviderIsKimi() {
        contextRunner
                .withPropertyValues("face2info.api.summary.provider=kimi")
                .run(context -> {
                    assertThat(context).hasSingleBean(SummaryGenerationClient.class);
                    assertThat(context.getBean(SummaryGenerationClient.class))
                            .isInstanceOf(KimiSummaryGenerationClient.class);
                });
    }

    @Configuration
    @EnableConfigurationProperties(ApiProperties.class)
    @Import({
            DeepSeekSummaryGenerationClient.class,
            KimiSummaryGenerationClient.class,
            NoopSummaryGenerationClient.class
    })
    static class TestConfig {

        @Bean
        RestTemplate restTemplate() {
            return new RestTemplate();
        }

        @Bean
        @Qualifier("kimiRestTemplate")
        RestTemplate kimiRestTemplate() {
            return new RestTemplate();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
