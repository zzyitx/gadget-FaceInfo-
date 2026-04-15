package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 单篇正文的结构化摘要结果。
 */
@Schema(description = "单篇正文摘要结果")
public class PageSummary {

    @Schema(description = "正文来源链接")
    private String sourceUrl;

    @Schema(description = "正文标题")
    private String title;

    @Schema(description = "正文摘要")
    private String summary;

    @Schema(description = "正文中提取出的关键事实")
    private List<String> keyFacts = new ArrayList<>();

    @Schema(description = "正文中提取出的标签")
    private List<String> tags = new ArrayList<>();

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
}
