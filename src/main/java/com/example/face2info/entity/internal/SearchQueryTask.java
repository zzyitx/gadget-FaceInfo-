package com.example.face2info.entity.internal;

/**
 * 单条多语搜索任务。
 */
public class SearchQueryTask {

    private String queryText;
    private String languageCode;
    private String queryKind;
    private String sourceReason;
    private int priority;

    public String getQueryText() {
        return queryText;
    }

    public SearchQueryTask setQueryText(String queryText) {
        this.queryText = queryText;
        return this;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public SearchQueryTask setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
        return this;
    }

    public String getQueryKind() {
        return queryKind;
    }

    public SearchQueryTask setQueryKind(String queryKind) {
        this.queryKind = queryKind;
        return this;
    }

    public String getSourceReason() {
        return sourceReason;
    }

    public SearchQueryTask setSourceReason(String sourceReason) {
        this.sourceReason = sourceReason;
        return this;
    }

    public int getPriority() {
        return priority;
    }

    public SearchQueryTask setPriority(int priority) {
        this.priority = priority;
        return this;
    }
}
