package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 搜索查询模板配置。
 */
@Getter
@Setter
public class SearchTemplateProperties {

    private static final Map<String, List<String>> DEFAULT_QUERY_TEMPLATES = Map.ofEntries(
            Map.entry("secondary_profile", List.of(
                    "{name}",
                    "{name} biography",
                    "{name} official profile",
                    "{english_name} biography",
                    "{native_name} 人物简介"
            )),
            Map.entry("family", List.of(
                    "{name} family background",
                    "{name} upbringing",
                    "{native_name} 家庭背景",
                    "{native_name} 成长经历"
            ))
    );

    private Map<String, List<String>> queryTemplates = new LinkedHashMap<>();
    private Map<String, List<String>> derivedSectionTitles = new LinkedHashMap<>();
    private List<String> expandEnabledTopics = new ArrayList<>();
    private int expandMaxQueryCount = 4;
    private int expandMaxTermLength = 16;

    public List<String> resolveQueryTemplates(String topicKey) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        List<String> configured = queryTemplates.get(topicKey);
        if (configured != null) {
            configured.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(merged::add);
        }
        if (merged.isEmpty()) {
            List<String> defaults = DEFAULT_QUERY_TEMPLATES.get(topicKey);
            if (defaults != null) {
                defaults.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .forEach(merged::add);
            }
        }
        return new ArrayList<>(merged);
    }

    public List<String> resolveDerivedSectionTitles(String sectionType) {
        List<String> configured = derivedSectionTitles.get(sectionType);
        if (configured == null) {
            return List.of();
        }
        return configured.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }
}
