package com.example.face2info.entity.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * 派生主题查询请求。
 */
public class DerivedTopicRequest {

    private String resolvedName;
    private DerivedTopicType topicType = DerivedTopicType.CUSTOM;
    private String rawQuery;
    private List<String> protectedTerms = new ArrayList<>();

    public String getResolvedName() {
        return resolvedName;
    }

    public DerivedTopicRequest setResolvedName(String resolvedName) {
        this.resolvedName = resolvedName;
        return this;
    }

    public DerivedTopicType getTopicType() {
        return topicType;
    }

    public DerivedTopicRequest setTopicType(DerivedTopicType topicType) {
        this.topicType = topicType;
        return this;
    }

    public String getRawQuery() {
        return rawQuery;
    }

    public DerivedTopicRequest setRawQuery(String rawQuery) {
        this.rawQuery = rawQuery;
        return this;
    }

    public List<String> getProtectedTerms() {
        return protectedTerms;
    }

    public DerivedTopicRequest setProtectedTerms(List<String> protectedTerms) {
        this.protectedTerms = protectedTerms == null ? new ArrayList<>() : protectedTerms;
        return this;
    }
}
