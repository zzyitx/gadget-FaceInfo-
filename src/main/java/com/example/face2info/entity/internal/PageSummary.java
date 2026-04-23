package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 单篇正文的结构化摘要结果。
 */
@Schema(description = "单篇正文摘要结果")
public class PageSummary {

    @Schema(description = "全局文章编号")
    private Integer sourceId;

    @Schema(description = "正文来源链接")
    private String sourceUrl;

    @Schema(description = "正文标题")
    private String title;

    @Schema(description = "正文作者")
    private String author;

    @Schema(description = "正文发布时间")
    private String publishedAt;

    @Schema(description = "来源平台")
    private String sourcePlatform;

    @Schema(description = "正文摘要")
    private String summary;

    @Schema(description = "正文中提取出的关键事实")
    private List<String> keyFacts = new ArrayList<>();

    @Schema(description = "正文中提取出的标签")
    private List<String> tags = new ArrayList<>();

    @Schema(description = "单篇正文摘要段落")
    private List<ParagraphSummaryItem> summaryParagraphs = new ArrayList<>();

    @Schema(description = "单篇正文对应的全局文章来源表")
    private List<ArticleCitation> articleSources = new ArrayList<>();

    public Integer getSourceId() {
        return sourceId;
    }

    public PageSummary setSourceId(Integer sourceId) {
        this.sourceId = sourceId;
        return this;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public PageSummary setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public PageSummary setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getAuthor() {
        return author;
    }

    public PageSummary setAuthor(String author) {
        this.author = author;
        return this;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public PageSummary setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
        return this;
    }

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    public PageSummary setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public PageSummary setSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public List<String> getKeyFacts() {
        return keyFacts;
    }

    public PageSummary setKeyFacts(List<String> keyFacts) {
        this.keyFacts = keyFacts;
        return this;
    }

    public List<String> getTags() {
        return tags;
    }

    public PageSummary setTags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    public List<ParagraphSummaryItem> getSummaryParagraphs() {
        return summaryParagraphs;
    }

    public PageSummary setSummaryParagraphs(List<ParagraphSummaryItem> summaryParagraphs) {
        this.summaryParagraphs = summaryParagraphs == null ? new ArrayList<>() : new ArrayList<>(summaryParagraphs);
        return this;
    }

    public List<ArticleCitation> getArticleSources() {
        return articleSources;
    }

    public PageSummary setArticleSources(List<ArticleCitation> articleSources) {
        this.articleSources = articleSources == null ? new ArrayList<>() : new ArrayList<>(articleSources);
        return this;
    }
}
