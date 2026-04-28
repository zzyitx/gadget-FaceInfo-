package com.example.face2info.service.impl;

import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.client.impl.DeepSeekSummaryGenerationClient;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import com.example.face2info.service.PrimarySearchQueryBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PrimarySearchQueryBuilderImplTest {

    @Test
    void shouldReturnSecondaryQueriesFromConfiguredTemplatesWithoutCallingModels() {
        DeepSeekSummaryGenerationClient deepSeek = mock(DeepSeekSummaryGenerationClient.class);
        SummaryGenerationClient kimi = mock(SummaryGenerationClient.class);

        List<String> queries = builder(deepSeek, kimi).buildSecondaryProfileQueries(
                "伯恩斯",
                languageProfile("尼古拉斯·伯恩斯", "Nicholas Burns"),
                ambassadorProfile()
        );

        assertThat(queries).containsExactly(
                "Nicholas Burns",
                "Nicholas Burns biography",
                "Nicholas Burns official profile",
                "尼古拉斯·伯恩斯 人物简介"
        );
        verifyNoInteractions(deepSeek, kimi);
    }

    @Test
    void shouldReturnSectionQueriesFromConfiguredTemplatesWithoutCallingModels() {
        DeepSeekSummaryGenerationClient deepSeek = mock(DeepSeekSummaryGenerationClient.class);
        SummaryGenerationClient kimi = mock(SummaryGenerationClient.class);

        List<String> queries = builder(deepSeek, kimi).buildSectionQueries(
                "伯恩斯",
                languageProfile("尼古拉斯·伯恩斯", "Nicholas Burns"),
                ambassadorProfile(),
                "china_related_statements"
        );

        assertThat(queries).containsExactly(
                "尼古拉斯·伯恩斯 涉华言论",
                "尼古拉斯·伯恩斯 中国评价",
                "尼古拉斯·伯恩斯 中美关系",
                "尼古拉斯·伯恩斯 中欧关系",
                "Nicholas Burns China policy"
        );
        verifyNoInteractions(deepSeek, kimi);
    }

    @Test
    void shouldUseFallbackQueriesWhenNoTemplateExists() {
        DeepSeekSummaryGenerationClient deepSeek = mock(DeepSeekSummaryGenerationClient.class);
        SummaryGenerationClient kimi = mock(SummaryGenerationClient.class);

        List<String> queries = builder(deepSeek, kimi).buildSectionQueries(
                "伯恩斯",
                languageProfile("尼古拉斯·伯恩斯", "Nicholas Burns"),
                ambassadorProfile(),
                "unknown_section"
        );

        assertThat(queries).hasSize(7);
        assertThat(queries.get(0)).isEqualTo("尼古拉斯·伯恩斯 驻华大使");
        assertThat(queries.get(4)).isEqualTo("Nicholas Burns Ambassador profile");
        assertThat(queries.stream().filter(query -> query.contains("驻华大使"))).hasSizeGreaterThanOrEqualTo(5);
        verifyNoInteractions(deepSeek, kimi);
    }

    private PrimarySearchQueryBuilder builder(DeepSeekSummaryGenerationClient deepSeek,
                                              SummaryGenerationClient kimi) {
        return new PrimarySearchQueryBuilderImpl(deepSeek, kimi);
    }

    private SearchLanguageProfile languageProfile(String zhName, String enName) {
        return new SearchLanguageProfile()
                .setResolvedName(zhName)
                .setLanguageCodes(List.of("zh", "en"))
                .setLocalizedNames(Map.of("zh", zhName, "en", enName));
    }

    private ResolvedPersonProfile ambassadorProfile() {
        return new ResolvedPersonProfile()
                .setResolvedName("尼古拉斯·伯恩斯")
                .setDescription("美国驻华大使，资深外交官。")
                .setSummary("美国驻华大使，资深外交官。");
    }
}
