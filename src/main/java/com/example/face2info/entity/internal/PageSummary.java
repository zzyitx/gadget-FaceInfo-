package com.example.face2info.entity.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * 单篇正文的结构化摘要结果。
 */
public class PageSummary {

    private String sourceUrl;
    private String title;
    private String resolvedNameCandidate;
    private String summary;
    private List<String> keyFacts = new ArrayList<>();
    private List<String> tags = new ArrayList<>();

    public String getSourceUrl() {
        return sourceUrl;
    }

    public PageSummary setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public PageSummary setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getResolvedNameCandidate() {
        return resolvedNameCandidate;
    }

    public PageSummary setResolvedNameCandidate(String resolvedNameCandidate) {
        this.resolvedNameCandidate = resolvedNameCandidate;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public PageSummary setSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public List<String> getKeyFacts() {
        return keyFacts;
    }

    public PageSummary setKeyFacts(List<String> keyFacts) {
        this.keyFacts = keyFacts;
        return this;
    }

    public List<String> getTags() {
        return tags;
    }

    public PageSummary setTags(List<String> tags) {
        this.tags = tags;
        return this;
    }
}
