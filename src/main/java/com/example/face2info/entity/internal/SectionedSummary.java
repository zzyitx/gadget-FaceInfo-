package com.example.face2info.entity.internal;

import java.util.List;

/**
 * 分段摘要结果。
 */
public class SectionedSummary {

    private List<SectionSummaryItem> sections = List.of();

    public List<SectionSummaryItem> getSections() {
        return sections;
    }

    public SectionedSummary setSections(List<SectionSummaryItem> sections) {
        this.sections = sections == null ? List.of() : sections;
        return this;
    }
}
