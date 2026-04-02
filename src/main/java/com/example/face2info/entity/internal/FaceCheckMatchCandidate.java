package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "FaceCheck 原始候选匹配结果")
public class FaceCheckMatchCandidate {

    @Schema(description = "匹配图片的数据地址")
    private String imageDataUrl;

    @Schema(description = "匹配相似度分数")
    private double similarityScore;

    @Schema(description = "来源站点域名")
    private String sourceHost;

    @Schema(description = "来源页面链接")
    private String sourceUrl;

    @Schema(description = "原始分组编号")
    private int group;

    @Schema(description = "结果被观察到的次数")
    private int seen;

    @Schema(description = "结果序号")
    private int index;

    public String getImageDataUrl() {
        return imageDataUrl;
    }

    public FaceCheckMatchCandidate setImageDataUrl(String imageDataUrl) {
        this.imageDataUrl = imageDataUrl;
        return this;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public FaceCheckMatchCandidate setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
        return this;
    }

    public String getSourceHost() {
        return sourceHost;
    }

    public FaceCheckMatchCandidate setSourceHost(String sourceHost) {
        this.sourceHost = sourceHost;
        return this;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public FaceCheckMatchCandidate setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
        return this;
    }

    public int getGroup() {
        return group;
    }

    public FaceCheckMatchCandidate setGroup(int group) {
        this.group = group;
        return this;
    }

    public int getSeen() {
        return seen;
    }

    public FaceCheckMatchCandidate setSeen(int seen) {
        this.seen = seen;
        return this;
    }

    public int getIndex() {
        return index;
    }

    public FaceCheckMatchCandidate setIndex(int index) {
        this.index = index;
        return this;
    }
}
