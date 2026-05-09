package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

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

    public PersonInfo getProfile() {
        return profile;
    }

    public CandidatePersonPortrait setProfile(PersonInfo profile) {
        this.profile = profile;
        return this;
    }
}
