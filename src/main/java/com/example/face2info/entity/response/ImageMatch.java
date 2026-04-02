package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "搜图引擎图片匹配结果")
public class ImageMatch {

    @Schema(description = "结果在原始搜索列表中的位置", example = "1")
    private int position;

    @Schema(description = "匹配结果标题", example = "Lei Jun official profile")
    private String title;

    @Schema(description = "匹配结果文章链接", example = "https://example.com/person/lei-jun")
    private String link;

    @Schema(description = "结果来源站点或来源名称", example = "Wikipedia")
    private String source;

    @JsonProperty("thumbnail_url")
    @Schema(description = "Serper 返回的缩略图地址", example = "https://encrypted-tbn0.gstatic.com/images?q=tbn:...")
    private String thumbnailUrl;

    @JsonProperty("similarity_score")
    @Schema(description = "基于排名、标题和来源计算的匹配置信度分数", example = "96.4")
    private double similarityScore;

    public int getPosition() {
        return position;
    }

    public ImageMatch setPosition(int position) {
        this.position = position;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public ImageMatch setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getLink() {
        return link;
    }

    public ImageMatch setLink(String link) {
        this.link = link;
        return this;
    }

    public String getSource() {
        return source;
    }

    public ImageMatch setSource(String source) {
        this.source = source;
        return this;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public ImageMatch setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
        return this;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public ImageMatch setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
        return this;
    }
}
