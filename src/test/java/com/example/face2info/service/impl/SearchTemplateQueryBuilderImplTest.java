package com.example.face2info.service.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTemplateQueryBuilderImplTest {

    @Test
    void shouldRenderConfiguredTemplatesWithNameVariables() {
        ApiProperties properties = new ApiProperties();
        properties.getSearch().getQueryTemplates().put("education", List.of(
                "{name} education",
                "{native_name} 教育经历",
                "{english_name} alma mater"
        ));

        List<String> queries = new SearchTemplateQueryBuilderImpl(properties).build(
                "education",
                "黄仁勋",
                languageProfile(),
                null,
                Map.of()
        );

        assertThat(queries).containsExactly(
                "Jensen Huang education",
                "黄仁勋 教育经历",
                "Jensen Huang alma mater"
        );
    }

    @Test
    void shouldFilterTemplateWhenReservedVariableIsMissing() {
        ApiProperties properties = new ApiProperties();
        properties.getSearch().getQueryTemplates().put("contact_information", List.of(
                "{name} official website",
                "{username} {platform}"
        ));

        List<String> queries = new SearchTemplateQueryBuilderImpl(properties).build(
                "contact_information",
                "黄仁勋",
                languageProfile(),
                null,
                Map.of()
        );

        assertThat(queries).containsExactly("Jensen Huang official website");
    }

    @Test
    void shouldUseFamilyDefaultTemplatesWhenConfigurationIsEmpty() {
        List<String> queries = new SearchTemplateQueryBuilderImpl(new ApiProperties()).build(
                "family",
                "黄仁勋",
                languageProfile(),
                null,
                Map.of()
        );

        assertThat(queries).contains("Jensen Huang family background", "黄仁勋 家庭背景");
    }

    @Test
    void shouldNotFallbackToRemovedTopicDefaultsWhenConfigurationIsEmpty() {
        List<String> queries = new SearchTemplateQueryBuilderImpl(new ApiProperties()).build(
                "career",
                "黄仁勋",
                languageProfile(),
                null,
                Map.of()
        );

        assertThat(queries).isEmpty();
    }

    private SearchLanguageProfile languageProfile() {
        return new SearchLanguageProfile()
                .setResolvedName("黄仁勋")
                .setPrimaryNationality("US")
                .setLanguageCodes(List.of("zh", "en"))
                .setLocalizedNames(Map.of("zh", "黄仁勋", "en", "Jensen Huang"));
    }
}
