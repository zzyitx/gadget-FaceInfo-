package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 单个网页证据。
 */
@Schema(description = "网页证据")
public class WebEvidence {

    @Schema(description = "网页链接")
    private String url;

    @Schema(description = "网页标题")
    private String title;

    @Schema(description = "网页来源站点")
    private String source;

    @Schema(description = "产生该证据的搜索引擎")
    private String sourceEngine;

    @Schema(description = "网页摘要片段")
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
