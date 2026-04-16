package com.example.face2info.entity.internal;

/**
 * 单条主题扩展搜索建议。
 */
public class TopicExpansionQuery {

    private String term;
    private String section;
    private String reason;

    public String getTerm() {
        return term;
    }

    public TopicExpansionQuery setTerm(String term) {
        this.term = term;
        return this;
    }

    public String getSection() {
        return section;
    }

    public TopicExpansionQuery setSection(String section) {
        this.section = section;
        return this;
    }

    public String getReason() {
        return reason;
    }

    public TopicExpansionQuery setReason(String reason) {
        this.reason = reason;
        return this;
    }
}
