package com.example.face2info.entity.internal;

import com.example.face2info.entity.response.ImageMatch;

import java.util.ArrayList;
import java.util.List;

/**
 * 多信源识别证据。
 */
public class RecognitionEvidence {

    private List<ImageMatch> imageMatches = new ArrayList<>();
    private List<WebEvidence> webEvidences = new ArrayList<>();
    private List<String> seedQueries = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public List<ImageMatch> getImageMatches() {
        return imageMatches;
    }

    public RecognitionEvidence setImageMatches(List<ImageMatch> imageMatches) {
        this.imageMatches = imageMatches;
        return this;
    }

    public List<WebEvidence> getWebEvidences() {
        return webEvidences;
    }

    public RecognitionEvidence setWebEvidences(List<WebEvidence> webEvidences) {
        this.webEvidences = webEvidences;
        return this;
    }

    public List<String> getSeedQueries() {
        return seedQueries;
    }

    public RecognitionEvidence setSeedQueries(List<String> seedQueries) {
        this.seedQueries = seedQueries;
        return this;
    }

    public List<String> getErrors() {
        return errors;
    }

    public RecognitionEvidence setErrors(List<String> errors) {
        this.errors = errors;
        return this;
    }
}
