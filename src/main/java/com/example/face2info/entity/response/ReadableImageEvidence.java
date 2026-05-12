package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "图片匹配证据分组")
public class ReadableImageEvidence {

    @JsonProperty("primary_matches")
    @Schema(description = "主展示或高可信图片匹配")
    private List<ImageMatch> primaryMatches = new ArrayList<>();

    @JsonProperty("supporting_matches")
    @Schema(description = "辅助文章和候选排查使用的图片匹配")
    private List<ImageMatch> supportingMatches = new ArrayList<>();

    public List<ImageMatch> getPrimaryMatches() {
        return primaryMatches;
    }

    public ReadableImageEvidence setPrimaryMatches(List<ImageMatch> primaryMatches) {
        this.primaryMatches = primaryMatches == null ? new ArrayList<>() : new ArrayList<>(primaryMatches);
        return this;
    }

    public List<ImageMatch> getSupportingMatches() {
        return supportingMatches;
    }

    public ReadableImageEvidence setSupportingMatches(List<ImageMatch> supportingMatches) {
        this.supportingMatches = supportingMatches == null ? new ArrayList<>() : new ArrayList<>(supportingMatches);
        return this;
    }
}
