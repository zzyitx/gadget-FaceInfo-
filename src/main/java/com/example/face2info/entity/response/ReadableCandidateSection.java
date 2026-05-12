package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "面向排查和二次聚合的候选信息分区")
public class ReadableCandidateSection {

    @JsonProperty("person_portraits")
    @Schema(description = "由搜图、文本检索和聚合链路产生的人物候选画像")
    private List<CandidatePersonPortrait> personPortraits = new ArrayList<>();

    @JsonProperty("vision_baselines")
    @Schema(description = "视觉模型仅基于上传图提取的视觉基准")
    private List<VisionModelPortrait> visionBaselines = new ArrayList<>();

    @JsonProperty("suspected_social_accounts")
    @Schema(description = "疑似社交账号或低确认度账号线索")
    private List<SocialAccount> suspectedSocialAccounts = new ArrayList<>();

    public List<CandidatePersonPortrait> getPersonPortraits() {
        return personPortraits;
    }

    public ReadableCandidateSection setPersonPortraits(List<CandidatePersonPortrait> personPortraits) {
        this.personPortraits = personPortraits == null ? new ArrayList<>() : new ArrayList<>(personPortraits);
        return this;
    }

    public List<VisionModelPortrait> getVisionBaselines() {
        return visionBaselines;
    }

    public ReadableCandidateSection setVisionBaselines(List<VisionModelPortrait> visionBaselines) {
        this.visionBaselines = visionBaselines == null ? new ArrayList<>() : new ArrayList<>(visionBaselines);
        return this;
    }

    public List<SocialAccount> getSuspectedSocialAccounts() {
        return suspectedSocialAccounts;
    }

    public ReadableCandidateSection setSuspectedSocialAccounts(List<SocialAccount> suspectedSocialAccounts) {
        this.suspectedSocialAccounts = suspectedSocialAccounts == null ? new ArrayList<>() : new ArrayList<>(suspectedSocialAccounts);
        return this;
    }
}
