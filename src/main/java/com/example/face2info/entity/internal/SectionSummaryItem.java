package com.example.face2info.entity.internal;

/**
 * 单个分段摘要项。
 */
public class SectionSummaryItem {

    private String section;
    private String summary;

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
}
