package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "按调试和聚合用途归档的人物画像分层结果")
public class StructuredPortraits {

    @JsonProperty("person_portrait_one")
    @Schema(description = "人物画像一：真实身份与主体画像")
    private PersonPortraitOneLayer personPortraitOne = new PersonPortraitOneLayer();

    @JsonProperty("person_portrait_two")
    @Schema(description = "人物画像二：候选身份、疑似账号与交叉验证线索")
    private PersonPortraitTwoLayer personPortraitTwo = new PersonPortraitTwoLayer();

    @JsonProperty("person_portrait_three")
    @Schema(description = "人物画像三：视觉大模型推断画像")
    private PersonPortraitThreeLayer personPortraitThree = new PersonPortraitThreeLayer();

    @JsonProperty("source_references")
    @Schema(description = "网页来源、引用、证据 URL 与图片来源")
    private PortraitSourceReferenceGroup sourceReferences = new PortraitSourceReferenceGroup();

    public PersonPortraitOneLayer getPersonPortraitOne() {
        return personPortraitOne;
    }

    public StructuredPortraits setPersonPortraitOne(PersonPortraitOneLayer personPortraitOne) {
        this.personPortraitOne = personPortraitOne == null ? new PersonPortraitOneLayer() : personPortraitOne;
        return this;
    }

    public PersonPortraitTwoLayer getPersonPortraitTwo() {
        return personPortraitTwo;
    }

    public StructuredPortraits setPersonPortraitTwo(PersonPortraitTwoLayer personPortraitTwo) {
        this.personPortraitTwo = personPortraitTwo == null ? new PersonPortraitTwoLayer() : personPortraitTwo;
        return this;
    }

    public PersonPortraitThreeLayer getPersonPortraitThree() {
        return personPortraitThree;
    }

    public StructuredPortraits setPersonPortraitThree(PersonPortraitThreeLayer personPortraitThree) {
        this.personPortraitThree = personPortraitThree == null ? new PersonPortraitThreeLayer() : personPortraitThree;
        return this;
    }

    public PortraitSourceReferenceGroup getSourceReferences() {
        return sourceReferences;
    }

    public StructuredPortraits setSourceReferences(PortraitSourceReferenceGroup sourceReferences) {
        this.sourceReferences = sourceReferences == null ? new PortraitSourceReferenceGroup() : sourceReferences;
        return this;
    }
}
