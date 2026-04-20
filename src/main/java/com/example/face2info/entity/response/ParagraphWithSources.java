package com.example.face2info.entity.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "带来源角标的段落")
public class ParagraphWithSources {

    @Schema(description = "段落文本")
    private String text;

    @Schema(description = "段落来源文章")
    private List<ArticleSourceBadge> sources = new ArrayList<>();

    public String getText() {
        return text;
    }

    public ParagraphWithSources setText(String text) {
        this.text = text;
        return this;
    }

    public List<ArticleSourceBadge> getSources() {
        return sources;
    }

    public ParagraphWithSources setSources(List<ArticleSourceBadge> sources) {
        this.sources = sources == null ? new ArrayList<>() : new ArrayList<>(sources);
        return this;
    }
}
