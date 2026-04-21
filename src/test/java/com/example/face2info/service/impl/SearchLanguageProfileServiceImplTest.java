package com.example.face2info.service.impl;

import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageInferenceResult;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchLanguageProfileServiceImplTest {

    @Test
    void shouldFallbackToChineseAndEnglishWhenInferenceResultIsMissing() {
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        when(summaryGenerationClient.inferSearchLanguageProfile(anyString(), any()))
                .thenReturn(null);

        SearchLanguageProfileServiceImpl service = new SearchLanguageProfileServiceImpl(summaryGenerationClient);

        SearchLanguageProfile profile = service.resolveProfile(
                "黄仁勋（Jensen Huang）",
                new ResolvedPersonProfile().setResolvedName("黄仁勋（Jensen Huang）")
        );

        assertThat(profile.getLanguageCodes()).containsExactly("zh", "en");
        assertThat(profile.getPrimaryNationality()).isEqualTo("unknown");
    }

    @Test
    void shouldAcceptAdditionalLanguageWhenInferenceConfidenceIsHigh() {
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        when(summaryGenerationClient.inferSearchLanguageProfile(anyString(), any()))
                .thenReturn(new SearchLanguageInferenceResult()
                        .setPrimaryNationality("JP")
                        .setRecommendedLanguages(List.of("zh", "en", "ja"))
                        .setLocalizedNames(Map.of("zh", "宫崎骏", "en", "Hayao Miyazaki", "ja", "宮崎 駿"))
                        .setConfidence(0.91));

        SearchLanguageProfileServiceImpl service = new SearchLanguageProfileServiceImpl(summaryGenerationClient);

        SearchLanguageProfile profile = service.resolveProfile(
                "宫崎骏（Hayao Miyazaki）",
                new ResolvedPersonProfile().setResolvedName("宫崎骏（Hayao Miyazaki）")
        );

        assertThat(profile.getLanguageCodes()).containsExactly("zh", "en", "ja");
        assertThat(profile.getLocalizedNames().get("ja")).isEqualTo("宮崎 駿");
    }

    @Test
    void shouldFallbackWhenInferenceConfidenceIsTooLow() {
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        when(summaryGenerationClient.inferSearchLanguageProfile(anyString(), any()))
                .thenReturn(new SearchLanguageInferenceResult()
                        .setPrimaryNationality("JP")
                        .setRecommendedLanguages(List.of("zh", "en", "ja"))
                        .setConfidence(0.2));

        SearchLanguageProfileServiceImpl service = new SearchLanguageProfileServiceImpl(summaryGenerationClient);

        SearchLanguageProfile profile = service.resolveProfile(
                "宫崎骏（Hayao Miyazaki）",
                new ResolvedPersonProfile().setResolvedName("宫崎骏（Hayao Miyazaki）")
        );

        assertThat(profile.getLanguageCodes()).containsExactly("zh", "en");
        assertThat(profile.getPrimaryNationality()).isEqualTo("unknown");
    }
}
