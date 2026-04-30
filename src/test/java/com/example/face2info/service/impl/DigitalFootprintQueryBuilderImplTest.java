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
import static org.mockito.Mockito.verifyNoInteractions;

class DigitalFootprintQueryBuilderImplTest {

    @Test
    void shouldReturnDigitalFootprintQueriesFromFallbackTemplatesWithoutCallingModels() {
        DeepSeekSummaryGenerationClient deepSeek = mock(DeepSeekSummaryGenerationClient.class);
        SummaryGenerationClient kimi = mock(SummaryGenerationClient.class);

        List<DigitalFootprintQuery> queries = builder(deepSeek, kimi).build(
                "黄仁勋",
                languageProfile("黄仁勋", "Jensen Huang")
        );

        assertThat(queries)
                .extracting(DigitalFootprintQuery::getQueryText)
                .contains(
                        "Jensen Huang official website",
                        "Jensen Huang LinkedIn profile",
                        "Jensen Huang YouTube channel"
                );
        assertThat(queries)
                .extracting(DigitalFootprintQuery::getSourceReason)
                .containsOnly("fallback_template");
        verifyNoInteractions(deepSeek, kimi);
    }

    @Test
    void shouldFilterTemplatesWithMissingReservedVariables() {
        DeepSeekSummaryGenerationClient deepSeek = mock(DeepSeekSummaryGenerationClient.class);
        SummaryGenerationClient kimi = mock(SummaryGenerationClient.class);

        List<DigitalFootprintQuery> queries = builder(deepSeek, kimi).build(
                "黄仁勋",
                languageProfile("黄仁勋", "Jensen Huang")
        );

        assertThat(queries)
                .extracting(DigitalFootprintQuery::getQueryText)
                .noneMatch(query -> query.contains("{username}") || query.contains("{platform}"));
        verifyNoInteractions(deepSeek, kimi);
    }

    @Test
    void shouldFallbackToBuiltInDigitalTemplatesWhenConfiguredTemplatesAreMissing() {
        DeepSeekSummaryGenerationClient deepSeek = mock(DeepSeekSummaryGenerationClient.class);
        SummaryGenerationClient kimi = mock(SummaryGenerationClient.class);

        List<DigitalFootprintQuery> queries = builder(deepSeek, kimi).build(
                "",
                new SearchLanguageProfile()
        );

        assertThat(queries).isEmpty();
        verifyNoInteractions(deepSeek, kimi);
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

}
