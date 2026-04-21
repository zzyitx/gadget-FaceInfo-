package com.example.face2info.entity.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型直接返回的搜索语言推断结果。
 */
public class SearchLanguageInferenceResult {

    private String primaryNationality;
    private List<String> recommendedLanguages = new ArrayList<>();
    private Map<String, String> localizedNames = new LinkedHashMap<>();
    private String reason;
    private Double confidence;

    public String getPrimaryNationality() {
        return primaryNationality;
    }

    public SearchLanguageInferenceResult setPrimaryNationality(String primaryNationality) {
        this.primaryNationality = primaryNationality;
        return this;
    }

    public List<String> getRecommendedLanguages() {
        return recommendedLanguages;
    }

    public SearchLanguageInferenceResult setRecommendedLanguages(List<String> recommendedLanguages) {
        this.recommendedLanguages = recommendedLanguages == null ? new ArrayList<>() : recommendedLanguages;
        return this;
    }

    public Map<String, String> getLocalizedNames() {
        return localizedNames;
    }

    public SearchLanguageInferenceResult setLocalizedNames(Map<String, String> localizedNames) {
        this.localizedNames = localizedNames == null ? new LinkedHashMap<>() : localizedNames;
        return this;
    }

    public String getReason() {
        return reason;
    }

    public SearchLanguageInferenceResult setReason(String reason) {
        this.reason = reason;
        return this;
    }

    public Double getConfidence() {
        return confidence;
    }

    public SearchLanguageInferenceResult setConfidence(Double confidence) {
        this.confidence = confidence;
        return this;
    }
}
