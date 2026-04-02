package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 新闻结果 DTO。
 */
@Schema(description = "新闻条目")
public class NewsItem {

    @Schema(description = "新闻标题", example = "周杰伦宣布新专辑计划")
    private String title;

    @Schema(description = "新闻来源媒体", example = "People")
    private String source;

    @JsonProperty("published_at")
    @Schema(description = "新闻发布时间，保留原始时间字符串", example = "2026-04-01T09:30:00Z")
    private String publishedAt;

    @Schema(description = "新闻原文链接", example = "https://news.example.com/articles/123")
    private String url;

    @Schema(description = "新闻摘要或抓取到的简要内容", example = "报道介绍了该人物近期公开活动。")
    private String summary;

    public String getTitle() {
        return title;
    }

    public NewsItem setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getSource() {
        return source;
    }

    public NewsItem setSource(String source) {
        this.source = source;
        return this;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public NewsItem setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public NewsItem setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public NewsItem setSummary(String summary) {
        this.summary = summary;
        return this;
    }
}
