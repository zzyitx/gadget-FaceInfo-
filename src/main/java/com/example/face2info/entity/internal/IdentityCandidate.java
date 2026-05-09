package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "聚合后的身份候选")
public class IdentityCandidate {

    private String name;
    private List<String> organizations = new ArrayList<>();
    private List<String> occupations = new ArrayList<>();
    private List<String> sourceUrls = new ArrayList<>();
    private double maxImageSimilarity;
    private double averageEntityAssociation;
    private int sourceCount;
    private String confidenceLevel;

    public String getName() {
        return name;
    }

    public IdentityCandidate setName(String name) {
        this.name = name;
        return this;
    }

    public List<String> getOrganizations() {
        return organizations;
    }

    public IdentityCandidate setOrganizations(List<String> organizations) {
        this.organizations = organizations == null ? new ArrayList<>() : new ArrayList<>(organizations);
        return this;
    }

    public List<String> getOccupations() {
        return occupations;
    }

    public IdentityCandidate setOccupations(List<String> occupations) {
        this.occupations = occupations == null ? new ArrayList<>() : new ArrayList<>(occupations);
        return this;
    }

    public List<String> getSourceUrls() {
        return sourceUrls;
    }

    public IdentityCandidate setSourceUrls(List<String> sourceUrls) {
        this.sourceUrls = sourceUrls == null ? new ArrayList<>() : new ArrayList<>(sourceUrls);
        return this;
    }

    public double getMaxImageSimilarity() {
        return maxImageSimilarity;
    }

    public IdentityCandidate setMaxImageSimilarity(double maxImageSimilarity) {
        this.maxImageSimilarity = maxImageSimilarity;
        return this;
    }

    public double getAverageEntityAssociation() {
        return averageEntityAssociation;
    }

    public IdentityCandidate setAverageEntityAssociation(double averageEntityAssociation) {
        this.averageEntityAssociation = averageEntityAssociation;
        return this;
    }

    public int getSourceCount() {
        return sourceCount;
    }

    public IdentityCandidate setSourceCount(int sourceCount) {
        this.sourceCount = sourceCount;
        return this;
    }

    public String getConfidenceLevel() {
        return confidenceLevel;
    }

    public IdentityCandidate setConfidenceLevel(String confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
        return this;
    }
}
