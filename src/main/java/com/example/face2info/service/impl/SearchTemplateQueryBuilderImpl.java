package com.example.face2info.service.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.PersonBasicInfo;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import com.example.face2info.service.SearchTemplateQueryBuilder;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SearchTemplateQueryBuilderImpl implements SearchTemplateQueryBuilder {

    private final ApiProperties properties;

    public SearchTemplateQueryBuilderImpl(ApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<String> build(String topicKey,
                              String resolvedName,
                              SearchLanguageProfile languageProfile,
                              @Nullable ResolvedPersonProfile profile,
                              Map<String, String> extraVariables) {
        Map<String, String> variables = buildVariables(resolvedName, languageProfile, profile, extraVariables);
        return properties.getSearch().resolveQueryTemplates(topicKey).stream()
                .map(template -> render(template, variables))
                .filter(StringUtils::hasText)
                .map(query -> query.trim().replaceAll("\\s+", " "))
                .distinct()
                .toList();
    }

    private Map<String, String> buildVariables(String resolvedName,
                                               SearchLanguageProfile languageProfile,
                                               @Nullable ResolvedPersonProfile profile,
                                               Map<String, String> extraVariables) {
        Map<String, String> variables = new LinkedHashMap<>();
        String nativeName = localizedName(languageProfile, "zh");
        String englishName = localizedName(languageProfile, "en");
        variables.put("name", firstNonBlank(englishName, nativeName, resolvedName,
                languageProfile == null ? null : languageProfile.getResolvedName()));
        variables.put("native_name", firstNonBlank(nativeName, resolvedName,
                languageProfile == null ? null : languageProfile.getResolvedName(), englishName));
        variables.put("english_name", firstNonBlank(englishName, resolvedName, nativeName));
        variables.put("organization", firstOrganization(profile));
        variables.put("country", firstCountry(languageProfile));
        variables.put("role", firstRole(profile));
        if (extraVariables != null) {
            extraVariables.forEach((key, value) -> {
                if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                    variables.put(key.trim().toLowerCase(Locale.ROOT), value.trim());
                }
            });
        }
        return variables;
    }

    private String render(String template, Map<String, String> variables) {
        if (!StringUtils.hasText(template)) {
            return null;
        }
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            // 模板包含变量但当前上下文缺值时丢弃该模板，避免把占位符直接带入搜索词。
            if (rendered.contains(placeholder) && !StringUtils.hasText(entry.getValue())) {
                return null;
            }
            rendered = rendered.replace(placeholder, entry.getValue() == null ? "" : entry.getValue());
        }
        if (rendered.matches(".*\\{[a-zA-Z_]+}.*")) {
            return null;
        }
        return rendered;
    }

    private String localizedName(SearchLanguageProfile languageProfile, String code) {
        if (languageProfile == null || languageProfile.getLocalizedNames() == null) {
            return null;
        }
        return trimToNull(languageProfile.getLocalizedNames().get(code));
    }

    private String firstOrganization(@Nullable ResolvedPersonProfile profile) {
        if (profile == null || profile.getBasicInfo() == null) {
            return null;
        }
        PersonBasicInfo basicInfo = profile.getBasicInfo();
        return basicInfo.getBiographies() == null || basicInfo.getBiographies().isEmpty()
                ? null : trimToNull(basicInfo.getBiographies().get(0));
    }

    private String firstRole(@Nullable ResolvedPersonProfile profile) {
        if (profile == null || profile.getBasicInfo() == null
                || profile.getBasicInfo().getOccupations() == null
                || profile.getBasicInfo().getOccupations().isEmpty()) {
            return null;
        }
        return trimToNull(profile.getBasicInfo().getOccupations().get(0));
    }

    private String firstCountry(SearchLanguageProfile languageProfile) {
        return languageProfile == null ? null : trimToNull(languageProfile.getPrimaryNationality());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
