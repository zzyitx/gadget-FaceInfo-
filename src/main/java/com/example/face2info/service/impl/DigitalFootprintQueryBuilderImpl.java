package com.example.face2info.service.impl;

import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.client.impl.DeepSeekSummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.DigitalFootprintQuery;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import com.example.face2info.service.DigitalFootprintQueryBuilder;
import com.example.face2info.service.SearchTemplateQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class DigitalFootprintQueryBuilderImpl implements DigitalFootprintQueryBuilder {

    private static final int MIN_VALID_QUERY_COUNT = 12;
    private static final int MAX_QUERY_COUNT = 30;

    private final DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient;
    private final SummaryGenerationClient summaryGenerationClient;
    private final SearchTemplateQueryBuilder searchTemplateQueryBuilder;

    @Autowired
    public DigitalFootprintQueryBuilderImpl(@Nullable DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient,
                                            SummaryGenerationClient summaryGenerationClient,
                                            SearchTemplateQueryBuilder searchTemplateQueryBuilder) {
        this.deepSeekSummaryGenerationClient = deepSeekSummaryGenerationClient;
        this.summaryGenerationClient = summaryGenerationClient;
        this.searchTemplateQueryBuilder = searchTemplateQueryBuilder;
    }

    public DigitalFootprintQueryBuilderImpl(@Nullable DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient,
                                            SummaryGenerationClient summaryGenerationClient) {
        this(deepSeekSummaryGenerationClient, summaryGenerationClient,
                new SearchTemplateQueryBuilderImpl(new ApiProperties()));
    }

    @Override
    public List<DigitalFootprintQuery> build(String resolvedName, SearchLanguageProfile languageProfile) {
        if (!StringUtils.hasText(preferredQueryName(resolvedName, languageProfile))) {
            return List.of();
        }
        List<DigitalFootprintQuery> templateQueries = searchTemplateQueryBuilder.build(
                        "contact_information",
                        resolvedName,
                        languageProfile,
                        null,
                        Map.of()
                ).stream()
                .map(query -> toQuery(query, "configured_template", 1))
                .toList();
        if (!templateQueries.isEmpty()) {
            return prioritize(templateQueries);
        }

        return fallbackQueries(resolvedName, languageProfile);
    }

    private List<DigitalFootprintQuery> runModelSafely(QuerySupplier supplier,
                                                       String resolvedName,
                                                       SearchLanguageProfile languageProfile,
                                                       String sourceReason) {
        try {
            return parseAndValidate(supplier.get(), resolvedName, languageProfile, sourceReason);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    List<DigitalFootprintQuery> parseAndValidate(String rawText,
                                                 String resolvedName,
                                                 SearchLanguageProfile languageProfile,
                                                 String sourceReason) {
        if (!StringUtils.hasText(rawText)) {
            return List.of();
        }
        List<String> nameVariants = resolveNameVariants(resolvedName, languageProfile);
        if (nameVariants.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, DigitalFootprintQuery> deduplicated = new LinkedHashMap<>();
        for (String line : rawText.split("\\R")) {
            String cleaned = normalizeLine(line);
            if (!isValidQueryLine(cleaned, nameVariants)) {
                continue;
            }
            deduplicated.putIfAbsent(cleaned, toQuery(cleaned, sourceReason, deduplicated.size() + 1));
            if (deduplicated.size() >= MAX_QUERY_COUNT) {
                break;
            }
        }

        List<DigitalFootprintQuery> queries = new ArrayList<>(deduplicated.values());
        if (queries.size() < MIN_VALID_QUERY_COUNT) {
            return List.of();
        }
        return prioritize(queries);
    }

    private List<DigitalFootprintQuery> prioritize(List<DigitalFootprintQuery> queries) {
        return queries.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(query -> query.getPriority() == null ? Integer.MAX_VALUE : query.getPriority()))
                .limit(MAX_QUERY_COUNT)
                .toList();
    }

    private DigitalFootprintQuery toQuery(String queryText, String sourceReason, int defaultPriority) {
        String normalized = queryText.toLowerCase(Locale.ROOT);
        return new DigitalFootprintQuery()
                .setQueryText(queryText)
                .setQueryType(resolveQueryType(normalized))
                .setPlatform(resolvePlatform(normalized))
                .setPriority(resolvePriority(normalized, defaultPriority))
                .setSourceReason(sourceReason);
    }

    private boolean isValidQueryLine(String line, List<String> nameVariants) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        String normalized = line.toLowerCase(Locale.ROOT);
        if (normalized.contains("```") || normalized.startsWith("{") || normalized.startsWith("[")) {
            return false;
        }
        return nameVariants.stream()
                .filter(StringUtils::hasText)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private String normalizeLine(String line) {
        if (!StringUtils.hasText(line)) {
            return null;
        }
        return line.trim().replaceAll("\\s+", " ");
    }

    private List<String> resolveNameVariants(String resolvedName, SearchLanguageProfile languageProfile) {
        LinkedHashMap<String, String> deduplicated = new LinkedHashMap<>();
        if (languageProfile != null && languageProfile.getLocalizedNames() != null) {
            languageProfile.getLocalizedNames().values().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(name -> deduplicated.putIfAbsent(name.toLowerCase(Locale.ROOT), name));
        }
        if (StringUtils.hasText(resolvedName)) {
            deduplicated.putIfAbsent(resolvedName.trim().toLowerCase(Locale.ROOT), resolvedName.trim());
        }
        if (languageProfile != null && StringUtils.hasText(languageProfile.getResolvedName())) {
            String profileName = languageProfile.getResolvedName().trim();
            deduplicated.putIfAbsent(profileName.toLowerCase(Locale.ROOT), profileName);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private List<DigitalFootprintQuery> fallbackQueries(String resolvedName, SearchLanguageProfile languageProfile) {
        String preferredName = preferredQueryName(resolvedName, languageProfile);
        return prioritize(List.of(
                fallbackQuery(preferredName + " LinkedIn profile", "social_profile", "linkedin", 1),
                fallbackQuery("site:linkedin.com/in/ " + preferredName, "social_profile", "linkedin", 2),
                fallbackQuery(preferredName + " Twitter profile", "social_profile", "twitter", 3),
                fallbackQuery("site:twitter.com " + preferredName, "social_profile", "twitter", 4),
                fallbackQuery(preferredName + " X.com profile", "social_profile", "x", 5),
                fallbackQuery(preferredName + " GitHub profile", "social_profile", "github", 6),
                fallbackQuery(preferredName + " Medium profile", "social_profile", "medium", 7),
                fallbackQuery(preferredName + " Substack profile", "social_profile", "substack", 8),
                fallbackQuery(preferredName + " ResearchGate profile", "social_profile", "researchgate", 9),
                fallbackQuery(preferredName + " email contact", "email_contact", "email", 10),
                fallbackQuery(preferredName + " \"gmail.com\"", "email_contact", "email", 11),
                fallbackQuery(preferredName + " official website", "official_site", "website", 12),
                fallbackQuery(preferredName + " blog", "official_site", "blog", 13),
                fallbackQuery(preferredName + " Instagram profile", "social_profile", "instagram", 14),
                fallbackQuery(preferredName + " YouTube channel", "social_profile", "youtube", 15)
        ));
    }

    private String preferredQueryName(String resolvedName, SearchLanguageProfile languageProfile) {
        if (languageProfile != null && languageProfile.getLocalizedNames() != null) {
            String english = languageProfile.getLocalizedNames().get("en");
            if (StringUtils.hasText(english)) {
                return english.trim();
            }
        }
        if (StringUtils.hasText(resolvedName)) {
            return resolvedName.trim();
        }
        return languageProfile == null ? "" : languageProfile.getResolvedName();
    }

    private DigitalFootprintQuery fallbackQuery(String queryText, String queryType, String platform, int priority) {
        return new DigitalFootprintQuery()
                .setQueryText(queryText)
                .setQueryType(queryType)
                .setPlatform(platform)
                .setPriority(priority)
                .setSourceReason("fallback_template");
    }

    private String resolveQueryType(String normalizedQuery) {
        if (normalizedQuery.contains("email") || normalizedQuery.contains("gmail.com") || normalizedQuery.contains("contact")) {
            return "email_contact";
        }
        if (normalizedQuery.contains("website") || normalizedQuery.contains("blog")) {
            return "official_site";
        }
        return "social_profile";
    }

    private String resolvePlatform(String normalizedQuery) {
        Map<String, String> platforms = Map.ofEntries(
                Map.entry("linkedin", "linkedin"),
                Map.entry("twitter", "twitter"),
                Map.entry("x.com", "x"),
                Map.entry("github", "github"),
                Map.entry("medium", "medium"),
                Map.entry("substack", "substack"),
                Map.entry("researchgate", "researchgate"),
                Map.entry("instagram", "instagram"),
                Map.entry("youtube", "youtube"),
                Map.entry("gmail.com", "email"),
                Map.entry("email", "email"),
                Map.entry("website", "website"),
                Map.entry("blog", "blog")
        );
        return platforms.entrySet().stream()
                .filter(entry -> normalizedQuery.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("web");
    }

    private int resolvePriority(String normalizedQuery, int defaultPriority) {
        if (normalizedQuery.contains("site:linkedin.com/in/")) {
            return 1;
        }
        if (normalizedQuery.contains("linkedin")) {
            return 2;
        }
        if (normalizedQuery.contains("site:twitter.com")) {
            return 3;
        }
        if (normalizedQuery.contains("twitter") || normalizedQuery.contains("x.com")) {
            return 4;
        }
        if (normalizedQuery.contains("email") || normalizedQuery.contains("gmail.com") || normalizedQuery.contains("contact")) {
            return 5;
        }
        if (normalizedQuery.contains("website") || normalizedQuery.contains("blog")) {
            return 6;
        }
        return defaultPriority + 10;
    }

    @FunctionalInterface
    private interface QuerySupplier {
        String get();
    }
}
