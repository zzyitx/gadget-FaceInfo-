package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "视觉大模型直接推断的人物画像")
public class VisionModelPortrait {

    @Schema(description = "数据源标识", example = "gemini_vision")
    private String provider;

    @Schema(description = "模型名称", example = "gemini-2.5-pro")
    private String model;

    @JsonProperty("candidate_name")
    @Schema(description = "模型推断的候选人物姓名")
    private String candidateName;

    @Schema(description = "模型给出的置信度，取值 0 到 1")
    private Double confidence;

    @Schema(description = "中文结构化人物信息摘要")
    private String summary;

    @Schema(description = "工作单位")
    private String company;

    @Schema(description = "工作职位")
    private String position;

    @JsonProperty("social_accounts")
    @Schema(description = "模型基于公开来源推断的社交账号")
    private List<SocialAccount> socialAccounts = new ArrayList<>();

    @JsonProperty("evidence_urls")
    @Schema(description = "模型声明使用的公开来源 URL")
    private List<String> evidenceUrls = new ArrayList<>();

    @JsonProperty("source_notes")
    @Schema(description = "模型对来源和引用的简短说明")
    private List<String> sourceNotes = new ArrayList<>();

    @Schema(description = "模型提取的身份或职业标签")
    private List<String> tags = new ArrayList<>();

    public String getProvider() {
        return provider;
    }

    public VisionModelPortrait setProvider(String provider) {
        this.provider = provider;
        return this;
    }

    public String getModel() {
        return model;
    }

    public VisionModelPortrait setModel(String model) {
        this.model = model;
        return this;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public VisionModelPortrait setCandidateName(String candidateName) {
        this.candidateName = candidateName;
        return this;
    }

    public Double getConfidence() {
        return confidence;
    }

    public VisionModelPortrait setConfidence(Double confidence) {
        this.confidence = confidence;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public VisionModelPortrait setSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public String getCompany() {
        return company;
    }

    public VisionModelPortrait setCompany(String company) {
        this.company = company;
        return this;
    }

    public String getPosition() {
        return position;
    }

    public VisionModelPortrait setPosition(String position) {
        this.position = position;
        return this;
    }

    public List<SocialAccount> getSocialAccounts() {
        return socialAccounts;
    }

    public VisionModelPortrait setSocialAccounts(List<SocialAccount> socialAccounts) {
        this.socialAccounts = socialAccounts == null ? new ArrayList<>() : new ArrayList<>(socialAccounts);
        return this;
    }

    public List<String> getEvidenceUrls() {
        return evidenceUrls;
    }

    public VisionModelPortrait setEvidenceUrls(List<String> evidenceUrls) {
        this.evidenceUrls = evidenceUrls == null ? new ArrayList<>() : new ArrayList<>(evidenceUrls);
        return this;
    }

    public List<String> getSourceNotes() {
        return sourceNotes;
    }

    public VisionModelPortrait setSourceNotes(List<String> sourceNotes) {
        this.sourceNotes = sourceNotes == null ? new ArrayList<>() : new ArrayList<>(sourceNotes);
        return this;
    }

    public List<String> getTags() {
        return tags;
    }

    public VisionModelPortrait setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        return this;
    }
}
