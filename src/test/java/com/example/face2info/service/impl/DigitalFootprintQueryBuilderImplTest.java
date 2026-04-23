package com.example.face2info.service.impl;

import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.client.impl.DeepSeekSummaryGenerationClient;
import com.example.face2info.entity.internal.DigitalFootprintQuery;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import com.example.face2info.service.DigitalFootprintQueryBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DigitalFootprintQueryBuilderImplTest {

    @Test
    void shouldReturnValidatedQueriesFromDeepSeek() {
        DeepSeekSummaryGenerationClient deepSeek = mock(DeepSeekSummaryGenerationClient.class);
        SummaryGenerationClient kimi = mock(SummaryGenerationClient.class);

        when(deepSeek.generateDigitalFootprintQueries(anyString(), any(), any()))
                .thenReturn(validQueryBlock("Jensen Huang"));

        List<DigitalFootprintQuery> queries = builder(deepSeek, kimi).build(
                "黄仁勋",
                languageProfile("黄仁勋", "Jensen Huang")
        );

        assertThat(queries).hasSize(15);
        assertThat(queries)
                .extracting(DigitalFootprintQuery::getQueryText)
                .contains("site:linkedin.com/in/ Jensen Huang");
    }

    @Test
    void shouldFallbackToKimiWhenDeepSeekOutputIsInvalid() {
        DeepSeekSummaryGenerationClient deepSeek = mock(DeepSeekSummaryGenerationClient.class);
        SummaryGenerationClient kimi = mock(SummaryGenerationClient.class);

        when(deepSeek.generateDigitalFootprintQueries(anyString(), any(), any()))
                .thenReturn("```json\n[]\n```");
        when(kimi.generateDigitalFootprintQueries(anyString(), any(), any()))
                .thenReturn(validQueryBlock("Jensen Huang"));

        List<DigitalFootprintQuery> queries = builder(deepSeek, kimi).build(
                "黄仁勋",
                languageProfile("黄仁勋", "Jensen Huang")
        );

        assertThat(queries).hasSizeBetween(15, 30);
        verify(kimi).generateDigitalFootprintQueries(anyString(), any(), any());
    }

    @Test
    void shouldFallbackToTemplateQueriesWhenBothModelsFail() {
        DeepSeekSummaryGenerationClient deepSeek = mock(DeepSeekSummaryGenerationClient.class);
        SummaryGenerationClient kimi = mock(SummaryGenerationClient.class);

        when(deepSeek.generateDigitalFootprintQueries(anyString(), any(), any()))
                .thenThrow(new RuntimeException("deepseek failed"));
        when(kimi.generateDigitalFootprintQueries(anyString(), any(), any()))
                .thenThrow(new RuntimeException("kimi failed"));

        List<DigitalFootprintQuery> queries = builder(deepSeek, kimi).build(
                "黄仁勋",
                languageProfile("黄仁勋", "Jensen Huang")
        );

        assertThat(queries)
                .extracting(DigitalFootprintQuery::getQueryText)
                .contains("Jensen Huang LinkedIn profile", "site:linkedin.com/in/ Jensen Huang");
    }

    private DigitalFootprintQueryBuilder builder(DeepSeekSummaryGenerationClient deepSeek,
                                                 SummaryGenerationClient kimi) {
        return new DigitalFootprintQueryBuilderImpl(deepSeek, kimi);
    }

    private SearchLanguageProfile languageProfile(String zhName, String enName) {
        return new SearchLanguageProfile()
                .setResolvedName(zhName)
                .setLanguageCodes(List.of("zh", "en"))
                .setLocalizedNames(Map.of("zh", zhName, "en", enName));
    }

    private String validQueryBlock(String enName) {
        return """
                %s LinkedIn profile
                site:linkedin.com/in/ %s
                %s Twitter profile
                site:twitter.com %s
                %s X.com profile
                %s GitHub profile
                %s Medium profile
                %s Substack profile
                %s ResearchGate profile
                %s email contact
                %s "gmail.com"
                %s official website
                %s blog
                %s Instagram profile
                %s YouTube channel
                """.formatted(
                enName,
                enName,
                enName,
                enName,
                enName,
                enName,
                enName,
                enName,
                enName,
                enName,
                enName,
                enName,
                enName,
                enName,
                enName
        );
    }
}
