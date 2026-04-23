package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "全局文章来源条目")
public class ArticleCitation {

    @Schema(description = "全局文章编号")
    private Integer id;

    @Schema(description = "文章标题")
    private String title;

    @Schema(description = "文章链接")
    private String url;

    @Schema(description = "文章来源站点")
    private String source;

    @Schema(description = "文章发布时间")
    private String publishedAt;

    public Integer getId() {
        return id;
    }

    public ArticleCitation setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public ArticleCitation setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public ArticleCitation setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getSource() {
        return source;
    }

    public ArticleCitation setSource(String source) {
        this.source = source;
        return this;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public ArticleCitation setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
        return this;
    }
}
