package com.example.face2info.entity.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经过校验与回退后的最终搜索语言画像。
 */
public class SearchLanguageProfile {

    private String resolvedName;
    private String primaryNationality;
    private List<String> languageCodes = new ArrayList<>();
    private Map<String, String> localizedNames = new LinkedHashMap<>();
    private String inferenceReason;
    private double modelConfidence;

    public String getResolvedName() {
        return resolvedName;
    }

    public SearchLanguageProfile setResolvedName(String resolvedName) {
        this.resolvedName = resolvedName;
        return this;
    }

    public String getPrimaryNationality() {
        return primaryNationality;
    }

    public SearchLanguageProfile setPrimaryNationality(String primaryNationality) {
        this.primaryNationality = primaryNationality;
        return this;
    }

    public List<String> getLanguageCodes() {
        return languageCodes;
    }

    public SearchLanguageProfile setLanguageCodes(List<String> languageCodes) {
        this.languageCodes = languageCodes == null ? new ArrayList<>() : languageCodes;
        return this;
    }

    public Map<String, String> getLocalizedNames() {
        return localizedNames;
    }

    public SearchLanguageProfile setLocalizedNames(Map<String, String> localizedNames) {
        this.localizedNames = localizedNames == null ? new LinkedHashMap<>() : localizedNames;
        return this;
    }

    public String getInferenceReason() {
        return inferenceReason;
    }

    public SearchLanguageProfile setInferenceReason(String inferenceReason) {
        this.inferenceReason = inferenceReason;
        return this;
    }

    public double getModelConfidence() {
        return modelConfidence;
    }

    public SearchLanguageProfile setModelConfidence(double modelConfidence) {
        this.modelConfidence = modelConfidence;
        return this;
    }
}
