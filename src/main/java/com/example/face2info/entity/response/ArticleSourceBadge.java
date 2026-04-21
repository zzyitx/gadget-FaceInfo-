package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "段落来源角标")
public class ArticleSourceBadge {

    @Schema(description = "脚注编号")
    private Integer index;

    @Schema(description = "文章标题")
    private String title;

    @Schema(description = "文章链接")
    private String url;

    @Schema(description = "文章来源")
    private String source;

    @JsonProperty("published_at")
    @Schema(description = "文章发布时间")
    private String publishedAt;

    public Integer getIndex() {
        return index;
    }

    public ArticleSourceBadge setIndex(Integer index) {
        this.index = index;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public ArticleSourceBadge setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public ArticleSourceBadge setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getSource() {
        return source;
    }

    public ArticleSourceBadge setSource(String source) {
        this.source = source;
        return this;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public ArticleSourceBadge setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
        return this;
    }
}
