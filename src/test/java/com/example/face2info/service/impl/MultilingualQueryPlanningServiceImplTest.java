package com.example.face2info.service.impl;

import com.example.face2info.client.RealtimeTranslationClient;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import com.example.face2info.entity.internal.SearchQueryTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MultilingualQueryPlanningServiceImplTest {

    @Test
    void shouldGenerateQueriesForChineseEnglishAndNationalityLanguage() {
        SearchLanguageProfile profile = new SearchLanguageProfile()
                .setResolvedName("宫崎骏（Hayao Miyazaki）")
                .setLanguageCodes(List.of("zh", "en", "ja"))
                .setLocalizedNames(Map.of("zh", "宫崎骏", "en", "Hayao Miyazaki", "ja", "宮崎 駿"));

        RealtimeTranslationClient translationClient = (query, targetLanguage) -> "宮崎 駿 対中発言";
        MultilingualQueryPlanningServiceImpl service = new MultilingualQueryPlanningServiceImpl(translationClient);

        List<SearchQueryTask> tasks = service.planSectionQueries(profile, "china_related_statements", List.of("涉华言论"));

        assertThat(tasks).extracting(SearchQueryTask::getQueryText)
                .containsExactly("宫崎骏 涉华言论", "Hayao Miyazaki china-related statements", "宮崎 駿 対中発言");
    }

    @Test
    void shouldSkipNativeRoundWhenNativeLanguageIsEnglish() {
        SearchLanguageProfile profile = new SearchLanguageProfile()
                .setResolvedName("黄仁勋（Jensen Huang）")
                .setLanguageCodes(List.of("zh", "en"))
                .setLocalizedNames(Map.of("en", "Jensen Huang"));

        RealtimeTranslationClient translationClient = (query, targetLanguage) -> {
            throw new AssertionError("native translation should not be called");
        };
        MultilingualQueryPlanningServiceImpl service = new MultilingualQueryPlanningServiceImpl(translationClient);

        List<SearchQueryTask> tasks = service.planSectionQueries(profile, "china_related_statements", List.of("涉华言论"));

        assertThat(tasks).extracting(SearchQueryTask::getQueryText)
                .containsExactly("黄仁勋（Jensen Huang） 涉华言论", "Jensen Huang china-related statements");
        assertThat(tasks).extracting(task -> task.getLanguageCode() + "|" + task.getQueryText())
                .containsExactly(
                        "zh|黄仁勋（Jensen Huang） 涉华言论",
                        "en|Jensen Huang china-related statements"
                );
    }
}
