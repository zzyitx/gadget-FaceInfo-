package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "内部人物聚合结果")
public class PersonAggregate {

    @Schema(description = "最终识别出的人物姓名")
    private String name;

    @Schema(description = "人物简介")
    private String description;

    @Schema(description = "人物头像链接")
    private String imageUrl;

    @Schema(description = "聚合后的摘要")
    private String summary;

    @Schema(description = "百科链接")
    private String wikipedia;

    @Schema(description = "官方网站链接")
    private String officialWebsite;

    @Schema(description = "人物标签集合")
    private List<String> tags = new ArrayList<>();

    @Schema(description = "支撑当前结论的证据链接")
    private List<String> evidenceUrls = new ArrayList<>();

    @Schema(description = "人物基础信息")
    private PersonBasicInfo basicInfo = new PersonBasicInfo();

    public String getName() {
        return name;
    }

    public PersonAggregate setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public PersonAggregate setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public PersonAggregate setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public PersonAggregate setSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public String getWikipedia() {
        return wikipedia;
    }

    public PersonAggregate setWikipedia(String wikipedia) {
        this.wikipedia = wikipedia;
        return this;
    }

    public String getOfficialWebsite() {
        return officialWebsite;
    }

    public PersonAggregate setOfficialWebsite(String officialWebsite) {
        this.officialWebsite = officialWebsite;
        return this;
    }

    public List<String> getTags() {
        return tags;
    }

    public PersonAggregate setTags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    public List<String> getEvidenceUrls() {
        return evidenceUrls;
    }

    public PersonAggregate setEvidenceUrls(List<String> evidenceUrls) {
        this.evidenceUrls = evidenceUrls;
        return this;
    }

    public PersonBasicInfo getBasicInfo() {
        return basicInfo;
    }

    public PersonAggregate setBasicInfo(PersonBasicInfo basicInfo) {
        this.basicInfo = basicInfo;
        return this;
    }
}
