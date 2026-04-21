package com.example.face2info.service.impl;

import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageInferenceResult;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import com.example.face2info.service.SearchLanguageProfileService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SearchLanguageProfileServiceImpl implements SearchLanguageProfileService {

    static final double MIN_CONFIDENCE = 0.65D;
    static final List<String> FALLBACK_LANGUAGES = List.of("zh", "en");

    private final SummaryGenerationClient summaryGenerationClient;

    public SearchLanguageProfileServiceImpl(SummaryGenerationClient summaryGenerationClient) {
        this.summaryGenerationClient = summaryGenerationClient;
    }

    @Override
    public SearchLanguageProfile resolveProfile(String resolvedName, ResolvedPersonProfile profile) {
        SearchLanguageInferenceResult inferred;
        try {
            inferred = summaryGenerationClient.inferSearchLanguageProfile(resolvedName, profile);
        } catch (RuntimeException ex) {
            inferred = null;
        }
        if (!isAccepted(inferred)) {
            return fallbackProfile(resolvedName);
        }
        return new SearchLanguageProfile()
                .setResolvedName(resolvedName)
                .setPrimaryNationality(trimToUnknown(inferred.getPrimaryNationality()))
                .setLanguageCodes(normalizeLanguages(inferred.getRecommendedLanguages()))
                .setLocalizedNames(normalizeLocalizedNames(inferred.getLocalizedNames()))
                .setInferenceReason(inferred.getReason())
                .setModelConfidence(inferred.getConfidence() == null ? 0D : inferred.getConfidence());
    }

    private boolean isAccepted(SearchLanguageInferenceResult inferred) {
        return inferred != null
                && inferred.getConfidence() != null
                && inferred.getConfidence() >= MIN_CONFIDENCE
                && normalizeLanguages(inferred.getRecommendedLanguages()).containsAll(FALLBACK_LANGUAGES);
    }

    private List<String> normalizeLanguages(List<String> languages) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>(FALLBACK_LANGUAGES);
        if (languages != null) {
            for (String language : languages) {
                if (StringUtils.hasText(language)) {
                    normalized.add(language.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private Map<String, String> normalizeLocalizedNames(Map<String, String> localizedNames) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (localizedNames == null) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : localizedNames.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
            String value = entry.getValue().trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                normalized.put(key, value);
            }
        }
        return normalized;
    }

    private String trimToUnknown(String value) {
        return StringUtils.hasText(value) ? value.trim() : "unknown";
    }

    private SearchLanguageProfile fallbackProfile(String resolvedName) {
        return new SearchLanguageProfile()
                .setResolvedName(resolvedName)
                .setPrimaryNationality("unknown")
                .setLanguageCodes(new ArrayList<>(FALLBACK_LANGUAGES))
                .setLocalizedNames(new LinkedHashMap<>())
                .setInferenceReason("fallback")
                .setModelConfidence(0D);
    }
}
