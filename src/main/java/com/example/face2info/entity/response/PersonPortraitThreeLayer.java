package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "人物画像三：视觉基准，用于候选人物要素比对，不作为身份判断来源")
public class PersonPortraitThreeLayer {

    @JsonProperty("portrait_label")
    @Schema(description = "画像层级名称")
    private String portraitLabel = "人物画像三";

    @JsonProperty("vision_model_portraits")
    @Schema(description = "视觉模型从原图提取的硬性视觉指纹，供后续与候选人物资料做要素比对")
    private List<VisionModelPortrait> visionModelPortraits = new ArrayList<>();

    public String getPortraitLabel() {
        return portraitLabel;
    }

    public PersonPortraitThreeLayer setPortraitLabel(String portraitLabel) {
        this.portraitLabel = portraitLabel;
        return this;
    }

    public List<VisionModelPortrait> getVisionModelPortraits() {
        return visionModelPortraits;
    }

    public PersonPortraitThreeLayer setVisionModelPortraits(List<VisionModelPortrait> visionModelPortraits) {
        this.visionModelPortraits = visionModelPortraits == null ? new ArrayList<>() : new ArrayList<>(visionModelPortraits);
        return this;
    }
}
