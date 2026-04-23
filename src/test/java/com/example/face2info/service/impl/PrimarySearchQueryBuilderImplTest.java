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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrimarySearchQueryBuilderImplTest {

    @Test
    void shouldReturnValidatedSecondaryQueriesFromDeepSeek() {
        DeepSeekSummaryGenerationClient deepSeek = mock(DeepSeekSummaryGenerationClient.class);
        SummaryGenerationClient kimi = mock(SummaryGenerationClient.class);

        when(deepSeek.generatePrimarySearchQueries(anyString(), any(), any(), anyString()))
                .thenReturn("""
                        尼古拉斯·伯恩斯 驻华大使
                        尼古拉斯·伯恩斯 驻华大使 涉华言论
                        尼古拉斯·伯恩斯 驻华大使 政策表态
                        尼古拉斯·伯恩斯 驻华大使 对华策略
                        Nicholas Burns Ambassador China policy
                        尼古拉斯·伯恩斯 驻华大使 演讲 PDF
                        尼古拉斯·伯恩斯 驻华大使 争议 采访
                        """);

        List<String> queries = builder(deepSeek, kimi).buildSecondaryProfileQueries(
                "伯恩斯",
                languageProfile("尼古拉斯·伯恩斯", "Nicholas Burns"),
                ambassadorProfile()
        );

        assertThat(queries).containsExactly(
                "尼古拉斯·伯恩斯 驻华大使",
                "尼古拉斯·伯恩斯 驻华大使 涉华言论",
                "尼古拉斯·伯恩斯 驻华大使 政策表态",
                "尼古拉斯·伯恩斯 驻华大使 对华策略",
                "Nicholas Burns Ambassador China policy",
                "尼古拉斯·伯恩斯 驻华大使 演讲 PDF",
                "尼古拉斯·伯恩斯 驻华大使 争议 采访"
        );
    }

    @Test
    void shouldFallbackToKimiWhenDeepSeekOutputViolatesIdentityRule() {
        DeepSeekSummaryGenerationClient deepSeek = mock(DeepSeekSummaryGenerationClient.class);
        SummaryGenerationClient kimi = mock(SummaryGenerationClient.class);

        when(deepSeek.generatePrimarySearchQueries(anyString(), any(), any(), anyString()))
                .thenReturn("""
                        伯恩斯 涉华言论
                        伯恩斯 政策表态
                        伯恩斯 对华策略
                        伯恩斯 威胁 采访
                        Burns China policy
                        Burns speech PDF
                        Burns controversy interview
                        """);
        when(kimi.generatePrimarySearchQueries(anyString(), any(), any(), anyString()))
                .thenReturn("""
                        尼古拉斯·伯恩斯 驻华大使
                        尼古拉斯·伯恩斯 驻华大使 涉华言论
                        尼古拉斯·伯恩斯 驻华大使 政策表态
                        尼古拉斯·伯恩斯 驻华大使 对华策略
                        Nicholas Burns Ambassador China policy
                        尼古拉斯·伯恩斯 驻华大使 演讲 PDF
                        尼古拉斯·伯恩斯 驻华大使 争议 采访
                        """);

        List<String> queries = builder(deepSeek, kimi).buildSectionQueries(
                "伯恩斯",
                languageProfile("尼古拉斯·伯恩斯", "Nicholas Burns"),
                ambassadorProfile(),
                "china_related_statements"
        );

        assertThat(queries).containsExactly(
                "尼古拉斯·伯恩斯 驻华大使",
                "尼古拉斯·伯恩斯 驻华大使 涉华言论",
                "尼古拉斯·伯恩斯 驻华大使 政策表态",
                "尼古拉斯·伯恩斯 驻华大使 对华策略",
                "Nicholas Burns Ambassador China policy",
                "尼古拉斯·伯恩斯 驻华大使 演讲 PDF",
                "尼古拉斯·伯恩斯 驻华大使 争议 采访"
        );
        verify(kimi).generatePrimarySearchQueries(anyString(), any(), any(), anyString());
    }

    @Test
    void shouldFallbackToTemplateQueriesWhenBothModelsFail() {
        DeepSeekSummaryGenerationClient deepSeek = mock(DeepSeekSummaryGenerationClient.class);
        SummaryGenerationClient kimi = mock(SummaryGenerationClient.class);

        when(deepSeek.generatePrimarySearchQueries(anyString(), any(), any(), anyString()))
                .thenThrow(new RuntimeException("deepseek failed"));
        when(kimi.generatePrimarySearchQueries(anyString(), any(), any(), anyString()))
                .thenThrow(new RuntimeException("kimi failed"));

        List<String> queries = builder(deepSeek, kimi).buildSectionQueries(
                "伯恩斯",
                languageProfile("尼古拉斯·伯恩斯", "Nicholas Burns"),
                ambassadorProfile(),
                "china_related_statements"
        );

        assertThat(queries).hasSize(7);
        assertThat(queries.get(0)).isEqualTo("尼古拉斯·伯恩斯 驻华大使");
        assertThat(queries.get(4)).isEqualTo("Nicholas Burns Ambassador China policy");
        assertThat(queries.stream().filter(query -> query.contains("驻华大使"))).hasSizeGreaterThanOrEqualTo(5);
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
