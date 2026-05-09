package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "文章中抽取出的命名实体")
public class NamedEntity {

    @Schema(description = "实体类型：PERSON、ORG、OCCUPATION")
    private String type;

    @Schema(description = "实体原文")
    private String text;

    @Schema(description = "标准化后的实体文本")
    private String normalizedText;

    @Schema(description = "实体出现次数")
    private int mentions;

    @Schema(description = "实体上下文片段")
    private List<String> contexts = new ArrayList<>();

    @Schema(description = "实体来源文章 URL")
    private String sourceUrl;

    public String getType() {
        return type;
    }

    public NamedEntity setType(String type) {
        this.type = type;
        return this;
    }

    public String getText() {
        return text;
    }

    public NamedEntity setText(String text) {
        this.text = text;
        return this;
    }

    public String getNormalizedText() {
        return normalizedText;
    }

    public NamedEntity setNormalizedText(String normalizedText) {
        this.normalizedText = normalizedText;
        return this;
    }

    public int getMentions() {
        return mentions;
    }

    public NamedEntity setMentions(int mentions) {
        this.mentions = mentions;
        return this;
    }

    public List<String> getContexts() {
        return contexts;
    }

    public NamedEntity setContexts(List<String> contexts) {
        this.contexts = contexts == null ? new ArrayList<>() : new ArrayList<>(contexts);
        return this;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public NamedEntity setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
        return this;
    }
}
