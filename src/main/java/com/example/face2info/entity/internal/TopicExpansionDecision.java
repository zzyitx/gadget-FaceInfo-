package com.example.face2info.entity.internal;

import java.util.List;

/**
 * 主题扩展推断结果。
 */
public class TopicExpansionDecision {

    private Boolean shouldExpand;
    private List<TopicExpansionQuery> expansionQueries = List.of();

    public Boolean getShouldExpand() {
        return shouldExpand;
    }

    public TopicExpansionDecision setShouldExpand(Boolean shouldExpand) {
        this.shouldExpand = shouldExpand;
        return this;
    }

    public List<TopicExpansionQuery> getExpansionQueries() {
        return expansionQueries;
    }

    public TopicExpansionDecision setExpansionQueries(List<TopicExpansionQuery> expansionQueries) {
        this.expansionQueries = expansionQueries == null ? List.of() : expansionQueries;
        return this;
    }
}
