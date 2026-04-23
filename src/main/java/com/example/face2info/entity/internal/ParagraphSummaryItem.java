package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "单段摘要及其来源")
public class ParagraphSummaryItem {

    @Schema(description = "段落文本")
    private String text;

    @Schema(description = "段落引用的全局文章编号")
    private List<Integer> sourceIds = new ArrayList<>();

    @Schema(description = "段落引用的来源 URL 列表")
    private List<String> sourceUrls = new ArrayList<>();

    @Schema(description = "段落来源文章列表")
    private List<ParagraphSource> sources = new ArrayList<>();

    public String getText() {
        return text;
    }

    public ParagraphSummaryItem setText(String text) {
        this.text = text;
        return this;
    }

    public List<Integer> getSourceIds() {
        return sourceIds;
    }

    public ParagraphSummaryItem setSourceIds(List<Integer> sourceIds) {
        this.sourceIds = sourceIds == null ? new ArrayList<>() : new ArrayList<>(sourceIds);
        return this;
    }

    public List<String> getSourceUrls() {
        return sourceUrls;
    }

    public ParagraphSummaryItem setSourceUrls(List<String> sourceUrls) {
        this.sourceUrls = sourceUrls == null ? new ArrayList<>() : new ArrayList<>(sourceUrls);
        return this;
    }

    public List<ParagraphSource> getSources() {
        return sources;
    }

    public ParagraphSummaryItem setSources(List<ParagraphSource> sources) {
        this.sources = sources == null ? new ArrayList<>() : new ArrayList<>(sources);
        return this;
    }
}
