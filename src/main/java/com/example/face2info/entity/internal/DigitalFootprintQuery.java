package com.example.face2info.entity.internal;

public class DigitalFootprintQuery {

    private String queryText;
    private String queryType;
    private String platform;
    private Integer priority;
    private String sourceReason;

    public String getQueryText() {
        return queryText;
    }

    public DigitalFootprintQuery setQueryText(String queryText) {
        this.queryText = queryText;
        return this;
    }

    public String getQueryType() {
        return queryType;
    }

    public DigitalFootprintQuery setQueryType(String queryType) {
        this.queryType = queryType;
        return this;
    }

    public String getPlatform() {
        return platform;
    }

    public DigitalFootprintQuery setPlatform(String platform) {
        this.platform = platform;
        return this;
    }

    public Integer getPriority() {
        return priority;
    }

    public DigitalFootprintQuery setPriority(Integer priority) {
        this.priority = priority;
        return this;
    }

    public String getSourceReason() {
        return sourceReason;
    }

    public DigitalFootprintQuery setSourceReason(String sourceReason) {
        this.sourceReason = sourceReason;
        return this;
    }
}
