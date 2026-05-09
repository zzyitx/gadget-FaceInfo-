package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "人物画像二：候选身份、疑似账号与交叉验证线索")
public class PersonPortraitTwoLayer {

    @JsonProperty("portrait_label")
    @Schema(description = "画像层级名称")
    private String portraitLabel = "人物画像二";

    @JsonProperty("candidate_portraits")
    @Schema(description = "搜图候选人物独立文字检索得到的疑似人物画像")
    private List<CandidatePersonPortrait> candidatePortraits = new ArrayList<>();

    @JsonProperty("suspected_social_accounts")
    @Schema(description = "疑似社交媒体账号，通常来自用户名枚举或低置信度命中")
    private List<SocialAccount> suspectedSocialAccounts = new ArrayList<>();

    public String getPortraitLabel() {
        return portraitLabel;
    }

    public PersonPortraitTwoLayer setPortraitLabel(String portraitLabel) {
        this.portraitLabel = portraitLabel;
        return this;
    }

    public List<CandidatePersonPortrait> getCandidatePortraits() {
        return candidatePortraits;
    }

    public PersonPortraitTwoLayer setCandidatePortraits(List<CandidatePersonPortrait> candidatePortraits) {
        this.candidatePortraits = candidatePortraits == null ? new ArrayList<>() : new ArrayList<>(candidatePortraits);
        return this;
    }

    public List<SocialAccount> getSuspectedSocialAccounts() {
        return suspectedSocialAccounts;
    }

    public PersonPortraitTwoLayer setSuspectedSocialAccounts(List<SocialAccount> suspectedSocialAccounts) {
        this.suspectedSocialAccounts = suspectedSocialAccounts == null ? new ArrayList<>() : new ArrayList<>(suspectedSocialAccounts);
        return this;
    }
}
