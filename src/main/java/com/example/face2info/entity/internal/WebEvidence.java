package com.example.face2info.entity.internal;

/**
 * 单个网页证据。
 */
public class WebEvidence {

    private String url;
    private String title;
    private String source;
    private String sourceEngine;
    private String snippet;

    public String getUrl() {
        return url;
    }

    public WebEvidence setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public WebEvidence setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getSource() {
        return source;
    }

    public WebEvidence setSource(String source) {
        this.source = source;
        return this;
    }

    public String getSourceEngine() {
        return sourceEngine;
    }

    public WebEvidence setSourceEngine(String sourceEngine) {
        this.sourceEngine = sourceEngine;
        return this;
    }

    public String getSnippet() {
        return snippet;
    }

    public WebEvidence setSnippet(String snippet) {
        this.snippet = snippet;
        return this;
    }
}
