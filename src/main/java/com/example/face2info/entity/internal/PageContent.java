package com.example.face2info.entity.internal;

/**
 * 页面正文结果。
 */
public class PageContent {

    private String url;
    private String title;
    private String content;
    private String sourceEngine;

    public String getUrl() {
        return url;
    }

    public PageContent setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public PageContent setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getContent() {
        return content;
    }

    public PageContent setContent(String content) {
        this.content = content;
        return this;
    }

    public String getSourceEngine() {
        return sourceEngine;
    }

    public PageContent setSourceEngine(String sourceEngine) {
        this.sourceEngine = sourceEngine;
        return this;
    }
}
