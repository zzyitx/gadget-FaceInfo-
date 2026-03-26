package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "新闻条目")
/**
 * 新闻结果 DTO。
 */
public class NewsItem {

    private String title;
    private String source;

    @JsonProperty("published_at")
    private String publishedAt;

    private String url;
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
