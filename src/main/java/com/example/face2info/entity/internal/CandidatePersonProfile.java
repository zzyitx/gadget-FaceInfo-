package com.example.face2info.entity.internal;

import com.example.face2info.entity.response.ImageMatch;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 搜图候选人物的独立对比画像，不参与主人物聚合。
 */
@Schema(description = "搜图候选人物独立对比画像")
public class CandidatePersonProfile {

    @Schema(description = "候选人物姓名")
    private String candidateName;

    @Schema(description = "候选来源图片匹配")
    private ImageMatch imageMatch;

    @Schema(description = "基于候选姓名单独文字搜索得到的人物画像")
    private PersonAggregate profile;

    public String getCandidateName() {
        return candidateName;
    }

    public CandidatePersonProfile setCandidateName(String candidateName) {
        this.candidateName = candidateName;
        return this;
    }

    public ImageMatch getImageMatch() {
        return imageMatch;
    }

    public CandidatePersonProfile setImageMatch(ImageMatch imageMatch) {
        this.imageMatch = imageMatch;
        return this;
    }

    public PersonAggregate getProfile() {
        return profile;
    }

    public CandidatePersonProfile setProfile(PersonAggregate profile) {
        this.profile = profile;
        return this;
    }
}
