package com.example.face2info.entity.internal;

public class FaceCheckMatchCandidate {

    private String imageDataUrl;
    private double similarityScore;
    private String sourceHost;
    private String sourceUrl;
    private int group;
    private int seen;
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
