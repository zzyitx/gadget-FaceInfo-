package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "段落来源文章")
public class ParagraphSource {

    @Schema(description = "文章标题")
    private String title;

    @Schema(description = "文章链接")
    private String url;

    @Schema(description = "文章来源")
    private String source;

    @Schema(description = "发布时间")
    private String publishedAt;

    public String getTitle() {
        return title;
    }

    public ParagraphSource setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public ParagraphSource setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getSource() {
        return source;
    }

    public ParagraphSource setSource(String source) {
        this.source = source;
        return this;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public ParagraphSource setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
        return this;
    }
}
