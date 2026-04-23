package com.example.face2info.entity.internal;

/**
 * 单个分段摘要项。
 */
public class SectionSummaryItem {

    private String section;
    private String summary;
    private java.util.List<Integer> sourceIds = new java.util.ArrayList<>();
    private java.util.List<String> sourceUrls = new java.util.ArrayList<>();
    private java.util.List<ParagraphSource> sources = new java.util.ArrayList<>();

    public String getSection() {
        return section;
    }

    public SectionSummaryItem setSection(String section) {
        this.section = section;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public SectionSummaryItem setSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public java.util.List<Integer> getSourceIds() {
        return sourceIds;
    }

    public SectionSummaryItem setSourceIds(java.util.List<Integer> sourceIds) {
        this.sourceIds = sourceIds == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(sourceIds);
        return this;
    }

    public java.util.List<String> getSourceUrls() {
        return sourceUrls;
    }

    public SectionSummaryItem setSourceUrls(java.util.List<String> sourceUrls) {
        this.sourceUrls = sourceUrls == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(sourceUrls);
        return this;
    }

    public java.util.List<ParagraphSource> getSources() {
        return sources;
    }

    public SectionSummaryItem setSources(java.util.List<ParagraphSource> sources) {
        this.sources = sources == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(sources);
        return this;
    }
}
