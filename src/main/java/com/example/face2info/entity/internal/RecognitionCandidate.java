package com.example.face2info.entity.internal;

import com.example.face2info.entity.response.ImageMatch;

import java.util.ArrayList;
import java.util.List;

public class RecognitionCandidate {

    private String name;
    private double confidence;
    private String source;
    private String error;
    private List<ImageMatch> imageMatches = new ArrayList<>();

    public String getName() {
        return name;
    }

    public RecognitionCandidate setName(String name) {
        this.name = name;
        return this;
    }

    public double getConfidence() {
        return confidence;
    }

    public RecognitionCandidate setConfidence(double confidence) {
        this.confidence = confidence;
        return this;
    }

    public String getSource() {
        return source;
    }

    public RecognitionCandidate setSource(String source) {
        this.source = source;
        return this;
    }

    public String getError() {
        return error;
    }

    public RecognitionCandidate setError(String error) {
        this.error = error;
        return this;
    }

    public List<ImageMatch> getImageMatches() {
        return imageMatches;
    }

    public RecognitionCandidate setImageMatches(List<ImageMatch> imageMatches) {
        this.imageMatches = imageMatches;
        return this;
    }
}
