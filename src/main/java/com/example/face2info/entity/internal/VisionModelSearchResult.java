package com.example.face2info.entity.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * 视觉大模型直接识图并自行检索后的结构化结果。
 */
public class VisionModelSearchResult {

    private String provider;
    private String model;
    private String candidateName;
    private Double confidence;
    private String summary;
    private List<String> evidenceUrls = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private List<String> sourceNotes = new ArrayList<>();

    public String getProvider() {
        return provider;
    }

    public VisionModelSearchResult setProvider(String provider) {
        this.provider = provider;
        return this;
    }

    public String getModel() {
        return model;
    }

    public VisionModelSearchResult setModel(String model) {
        this.model = model;
        return this;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public VisionModelSearchResult setCandidateName(String candidateName) {
        this.candidateName = candidateName;
        return this;
    }

    public Double getConfidence() {
        return confidence;
    }

    public VisionModelSearchResult setConfidence(Double confidence) {
        this.confidence = confidence;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public VisionModelSearchResult setSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public List<String> getEvidenceUrls() {
        return evidenceUrls;
    }

    public VisionModelSearchResult setEvidenceUrls(List<String> evidenceUrls) {
        this.evidenceUrls = evidenceUrls == null ? new ArrayList<>() : new ArrayList<>(evidenceUrls);
        return this;
    }

    public List<String> getTags() {
        return tags;
    }

    public VisionModelSearchResult setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        return this;
    }

    public List<String> getSourceNotes() {
        return sourceNotes;
    }

    public VisionModelSearchResult setSourceNotes(List<String> sourceNotes) {
        this.sourceNotes = sourceNotes == null ? new ArrayList<>() : new ArrayList<>(sourceNotes);
        return this;
    }
}
