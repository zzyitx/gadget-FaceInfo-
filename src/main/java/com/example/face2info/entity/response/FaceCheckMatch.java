package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "FaceCheck 图片匹配结果")
public class FaceCheckMatch {

    @JsonProperty("image_data_url")
    @Schema(description = "匹配图片的数据地址，通常为 data URL", example = "data:image/jpeg;base64,...")
    private String imageDataUrl;

    @JsonProperty("similarity_score")
    @Schema(description = "相似度分数，数值越高表示越接近目标人脸", example = "97.2")
    private double similarityScore;

    @JsonProperty("source_host")
    @Schema(description = "匹配结果来源站点域名", example = "instagram.com")
    private String sourceHost;

    @JsonProperty("source_url")
    @Schema(description = "匹配结果的原始页面链接", example = "https://instagram.com/p/demo")
    private String sourceUrl;

    @Schema(description = "FaceCheck 返回的分组编号，用于区分相近候选集", example = "1")
    private int group;

    @Schema(description = "来源页面中该结果出现或被记录的次数", example = "3")
    private int seen;

    @Schema(description = "当前结果在 FaceCheck 返回列表中的序号", example = "0")
    private int index;

    public String getImageDataUrl() {
        return imageDataUrl;
    }

    public FaceCheckMatch setImageDataUrl(String imageDataUrl) {
        this.imageDataUrl = imageDataUrl;
        return this;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public FaceCheckMatch setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
        return this;
    }

    public String getSourceHost() {
        return sourceHost;
    }

    public FaceCheckMatch setSourceHost(String sourceHost) {
        this.sourceHost = sourceHost;
        return this;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public FaceCheckMatch setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
        return this;
    }

    public int getGroup() {
        return group;
    }

    public FaceCheckMatch setGroup(int group) {
        this.group = group;
        return this;
    }

    public int getSeen() {
        return seen;
    }

    public FaceCheckMatch setSeen(int seen) {
        this.seen = seen;
        return this;
    }

    public int getIndex() {
        return index;
    }

    public FaceCheckMatch setIndex(int index) {
        this.index = index;
        return this;
    }
}
