package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "面向前端和大模型阅读的最终结论分区")
public class ReadableResultSection {

    @JsonProperty("primary_person")
    @Schema(description = "最终聚合确认的主人物信息")
    private PersonInfo primaryPerson;

    @JsonProperty("primary_portrait")
    @Schema(description = "最终主展示人物画像")
    private CandidatePersonPortrait primaryPortrait;

    @JsonProperty("confirmed_social_accounts")
    @Schema(description = "已确认或高可信的社交账号")
    private List<SocialAccount> confirmedSocialAccounts = new ArrayList<>();

    public PersonInfo getPrimaryPerson() {
        return primaryPerson;
    }

    public ReadableResultSection setPrimaryPerson(PersonInfo primaryPerson) {
        this.primaryPerson = primaryPerson;
        return this;
    }

    public CandidatePersonPortrait getPrimaryPortrait() {
        return primaryPortrait;
    }

    public ReadableResultSection setPrimaryPortrait(CandidatePersonPortrait primaryPortrait) {
        this.primaryPortrait = primaryPortrait;
        return this;
    }

    public List<SocialAccount> getConfirmedSocialAccounts() {
        return confirmedSocialAccounts;
    }

    public ReadableResultSection setConfirmedSocialAccounts(List<SocialAccount> confirmedSocialAccounts) {
        this.confirmedSocialAccounts = confirmedSocialAccounts == null ? new ArrayList<>() : new ArrayList<>(confirmedSocialAccounts);
        return this;
    }
}
