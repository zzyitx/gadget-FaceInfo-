package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "同一疑似人物画像流程下的人物画像分组")
public class PersonPortraitGroup {

    @JsonProperty("group_id")
    @Schema(description = "画像分组 ID")
    private String groupId;

    @JsonProperty("group_name")
    @Schema(description = "画像分组展示名")
    private String groupName;

    @JsonProperty("display_portrait")
    @Schema(description = "当前页面主展示的人物画像")
    private CandidatePersonPortrait displayPortrait;

    @Schema(description = "该流程下的全部人物画像，包含当前主展示画像和其他疑似画像")
    private List<CandidatePersonPortrait> portraits = new ArrayList<>();

    public String getGroupId() {
        return groupId;
    }

    public PersonPortraitGroup setGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public String getGroupName() {
        return groupName;
    }

    public PersonPortraitGroup setGroupName(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public CandidatePersonPortrait getDisplayPortrait() {
        return displayPortrait;
    }

    public PersonPortraitGroup setDisplayPortrait(CandidatePersonPortrait displayPortrait) {
        this.displayPortrait = displayPortrait;
        return this;
    }

    public List<CandidatePersonPortrait> getPortraits() {
        return portraits;
    }

    public PersonPortraitGroup setPortraits(List<CandidatePersonPortrait> portraits) {
        this.portraits = portraits == null ? new ArrayList<>() : portraits;
        return this;
    }
}
