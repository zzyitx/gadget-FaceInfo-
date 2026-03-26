package com.example.face2info.entity.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * 大模型解析后的人物信息。
 */
public class ResolvedPersonProfile {

    private String resolvedName;
    private String summary;
    private List<String> keyFacts = new ArrayList<>();
    private List<String> evidenceUrls = new ArrayList<>();

    public String getResolvedName() {
        return resolvedName;
    }

    public ResolvedPersonProfile setResolvedName(String resolvedName) {
        this.resolvedName = resolvedName;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public ResolvedPersonProfile setSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public List<String> getKeyFacts() {
        return keyFacts;
    }

    public ResolvedPersonProfile setKeyFacts(List<String> keyFacts) {
        this.keyFacts = keyFacts;
        return this;
    }

    public List<String> getEvidenceUrls() {
        return evidenceUrls;
    }

    public ResolvedPersonProfile setEvidenceUrls(List<String> evidenceUrls) {
        this.evidenceUrls = evidenceUrls;
        return this;
    }
}
