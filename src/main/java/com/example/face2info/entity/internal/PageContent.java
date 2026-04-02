package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 页面正文结果。
 */
@Schema(description = "抓取后的页面正文内容")
public class PageContent {

    @Schema(description = "页面链接地址")
    private String url;

    @Schema(description = "页面标题")
    private String title;

    @Schema(description = "页面正文内容")
    private String content;

    @Schema(description = "抓取内容使用的来源引擎")
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
