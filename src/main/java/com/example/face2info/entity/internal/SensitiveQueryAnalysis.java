package com.example.face2info.entity.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询敏感性分析结果。
 */
public class SensitiveQueryAnalysis {

    private String originalQuery;
    private String normalizedQuery;
    private List<String> tokens = new ArrayList<>();
    private List<String> sensitiveTerms = new ArrayList<>();
    private List<String> protectedTerms = new ArrayList<>();
    private boolean sensitive;
    private String riskLevel = "low";

    public String getOriginalQuery() {
        return originalQuery;
    }

    public SensitiveQueryAnalysis setOriginalQuery(String originalQuery) {
        this.originalQuery = originalQuery;
        return this;
    }

    public String getNormalizedQuery() {
        return normalizedQuery;
    }

    public SensitiveQueryAnalysis setNormalizedQuery(String normalizedQuery) {
        this.normalizedQuery = normalizedQuery;
        return this;
    }

    public List<String> getTokens() {
        return tokens;
    }

    public SensitiveQueryAnalysis setTokens(List<String> tokens) {
        this.tokens = tokens == null ? new ArrayList<>() : tokens;
        return this;
    }

    public List<String> getSensitiveTerms() {
        return sensitiveTerms;
    }

    public SensitiveQueryAnalysis setSensitiveTerms(List<String> sensitiveTerms) {
        this.sensitiveTerms = sensitiveTerms == null ? new ArrayList<>() : sensitiveTerms;
        return this;
    }

    public List<String> getProtectedTerms() {
        return protectedTerms;
    }

    public SensitiveQueryAnalysis setProtectedTerms(List<String> protectedTerms) {
        this.protectedTerms = protectedTerms == null ? new ArrayList<>() : protectedTerms;
        return this;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public SensitiveQueryAnalysis setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
        return this;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public SensitiveQueryAnalysis setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
        return this;
    }
}
