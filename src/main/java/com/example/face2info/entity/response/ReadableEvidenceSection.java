package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "统一归档网页、URL 和图片证据的分区")
public class ReadableEvidenceSection {

    @JsonProperty("articles")
    @Schema(description = "文章和网页来源")
    private List<ArticleSourceBadge> articles = new ArrayList<>();

    @JsonProperty("urls")
    @Schema(description = "支撑当前结果的证据 URL")
    private List<String> urls = new ArrayList<>();

    @JsonProperty("images")
    @Schema(description = "图片证据，区分主匹配和辅助匹配")
    private ReadableImageEvidence images = new ReadableImageEvidence();

    public List<ArticleSourceBadge> getArticles() {
        return articles;
    }

    public ReadableEvidenceSection setArticles(List<ArticleSourceBadge> articles) {
        this.articles = articles == null ? new ArrayList<>() : new ArrayList<>(articles);
        return this;
    }

    public List<String> getUrls() {
        return urls;
    }

    public ReadableEvidenceSection setUrls(List<String> urls) {
        this.urls = urls == null ? new ArrayList<>() : new ArrayList<>(urls);
        return this;
    }

    public ReadableImageEvidence getImages() {
        return images;
    }

    public ReadableEvidenceSection setImages(ReadableImageEvidence images) {
        this.images = images == null ? new ReadableImageEvidence() : images;
        return this;
    }
}
