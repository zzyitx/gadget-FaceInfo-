package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.LinkedHashMap;
import java.util.Map;

@Schema(description = "搜图候选人物单独文字检索得到的对比画像")
public class CandidatePersonPortrait {

    @JsonProperty("candidate_name")
    @Schema(description = "候选人物姓名")
    private String candidateName;

    @JsonProperty("portrait_id")
    @Schema(description = "画像在当前流程中的稳定展示 ID")
    private String portraitId;

    @JsonProperty("primary_display")
    @Schema(description = "是否为当前页面主展示的人物画像")
    private boolean primaryDisplay;

    @JsonProperty("similarity_score")
    @Schema(description = "候选图片相似度分数")
    private Double similarityScore;

    @JsonProperty("image_match")
    @Schema(description = "触发该候选人物的搜图匹配结果")
    private ImageMatch imageMatch;

    @JsonProperty("portrait_image_url")
    @Schema(description = "画像一展示的人像图片 URL，优先使用候选资料头像，其次使用搜图缩略图")
    private String portraitImageUrl;

    @JsonProperty("visual_fingerprint")
    @Schema(description = "从画像一展示人像提取的硬性视觉指纹")
    private Map<String, String> visualFingerprint = new LinkedHashMap<>();

    @JsonProperty("portrait_three_cross_comparison")
    @Schema(description = "画像一展示人像视觉指纹与人物画像三视觉基准的字段级交叉对比")
    private Map<String, String> portraitThreeCrossComparison = new LinkedHashMap<>();

    @Schema(description = "候选姓名独立文字搜索得到的人物画像")
    private PersonInfo profile;

    public String getCandidateName() {
        return candidateName;
    }

    public CandidatePersonPortrait setCandidateName(String candidateName) {
        this.candidateName = candidateName;
        return this;
    }

    public String getPortraitId() {
        return portraitId;
    }

    public CandidatePersonPortrait setPortraitId(String portraitId) {
        this.portraitId = portraitId;
        return this;
    }

    public boolean isPrimaryDisplay() {
        return primaryDisplay;
    }

    public CandidatePersonPortrait setPrimaryDisplay(boolean primaryDisplay) {
        this.primaryDisplay = primaryDisplay;
        return this;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public CandidatePersonPortrait setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
        return this;
    }

    public ImageMatch getImageMatch() {
        return imageMatch;
    }

    public CandidatePersonPortrait setImageMatch(ImageMatch imageMatch) {
        this.imageMatch = imageMatch;
        return this;
    }

    public String getPortraitImageUrl() {
        return portraitImageUrl;
    }

    public CandidatePersonPortrait setPortraitImageUrl(String portraitImageUrl) {
        this.portraitImageUrl = portraitImageUrl;
        return this;
    }

    public Map<String, String> getVisualFingerprint() {
        return visualFingerprint;
    }

    public CandidatePersonPortrait setVisualFingerprint(Map<String, String> visualFingerprint) {
        this.visualFingerprint = visualFingerprint == null ? new LinkedHashMap<>() : new LinkedHashMap<>(visualFingerprint);
        return this;
    }

    public Map<String, String> getPortraitThreeCrossComparison() {
        return portraitThreeCrossComparison;
    }

    public CandidatePersonPortrait setPortraitThreeCrossComparison(Map<String, String> portraitThreeCrossComparison) {
        this.portraitThreeCrossComparison = portraitThreeCrossComparison == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(portraitThreeCrossComparison);
        return this;
    }

    public PersonInfo getProfile() {
        return profile;
    }

    public CandidatePersonPortrait setProfile(PersonInfo profile) {
        this.profile = profile;
        return this;
    }
}
