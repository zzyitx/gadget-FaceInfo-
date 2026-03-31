package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "FaceCheck image match")
public class FaceCheckMatch {

    @JsonProperty("image_data_url")
    private String imageDataUrl;

    @JsonProperty("similarity_score")
    private double similarityScore;

    @JsonProperty("source_host")
    private String sourceHost;

    @JsonProperty("source_url")
    private String sourceUrl;

    private int group;
    private int seen;
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
