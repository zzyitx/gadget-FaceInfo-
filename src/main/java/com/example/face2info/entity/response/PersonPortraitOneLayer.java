package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "人物画像一：真实身份与主体画像")
public class PersonPortraitOneLayer {

    @JsonProperty("portrait_label")
    @Schema(description = "画像层级名称")
    private String portraitLabel = "人物画像一";

    @Schema(description = "真实身份、推断姓名、身份信息、工作单位、职业与职级等主体资料")
    private PersonInfo profile;

    @JsonProperty("social_accounts")
    @Schema(description = "已确认或较高可信度的社交账号")
    private List<SocialAccount> socialAccounts = new ArrayList<>();

    @JsonProperty("candidate_portraits")
    @Schema(description = "人物画像一展示区中的当前人物和疑似人物人像，包含视觉指纹和画像三交叉对比")
    private List<CandidatePersonPortrait> candidatePortraits = new ArrayList<>();

    public String getPortraitLabel() {
        return portraitLabel;
    }

    public PersonPortraitOneLayer setPortraitLabel(String portraitLabel) {
        this.portraitLabel = portraitLabel;
        return this;
    }

    public PersonInfo getProfile() {
        return profile;
    }

    public PersonPortraitOneLayer setProfile(PersonInfo profile) {
        this.profile = profile;
        return this;
    }

    public List<SocialAccount> getSocialAccounts() {
        return socialAccounts;
    }

    public PersonPortraitOneLayer setSocialAccounts(List<SocialAccount> socialAccounts) {
        this.socialAccounts = socialAccounts == null ? new ArrayList<>() : new ArrayList<>(socialAccounts);
        return this;
    }

    public List<CandidatePersonPortrait> getCandidatePortraits() {
        return candidatePortraits;
    }

    public PersonPortraitOneLayer setCandidatePortraits(List<CandidatePersonPortrait> candidatePortraits) {
        this.candidatePortraits = candidatePortraits == null ? new ArrayList<>() : new ArrayList<>(candidatePortraits);
        return this;
    }
}
