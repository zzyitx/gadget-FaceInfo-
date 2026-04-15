package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "大模型解析后的人物画像")
public class ResolvedPersonProfile {

    @Schema(description = "解析后确认的人物名称")
    private String resolvedName;

    @Schema(description = "解析后的人物简介")
    private String description;

    @Schema(description = "解析后的人物摘要")
    private String summary;

    @Schema(description = "解析后的教育经历摘要，用于前端独立区域展示")
    private String educationSummary;

    @Schema(description = "解析后的家庭背景摘要，用于前端独立区域展示")
    private String familyBackgroundSummary;

    @Schema(description = "解析后的职业经历摘要，用于前端独立区域展示")
    private String careerSummary;

    @Schema(description = "解析后的涉华言论摘要，用于前端独立区域展示")
    private String chinaRelatedStatementsSummary;

    @Schema(description = "解析后的政治倾向摘要，用于前端独立区域展示")
    private String politicalTendencySummary;

    @Schema(description = "解析后的地址信息摘要，用于前端独立区域展示")
    private String contactInformationSummary;

    @Schema(description = "解析后的家族成员情况摘要，用于前端独立区域展示")
    private String familyMemberSituationSummary;

    @Schema(description = "解析后的污点劣迹摘要，用于前端独立区域展示")
    private String misconductSummary;

    @Schema(description = "解析出的关键事实")
    private List<String> keyFacts = new ArrayList<>();

    @Schema(description = "解析出的标签集合")
    private List<String> tags = new ArrayList<>();

    @Schema(description = "支撑当前画像的证据链接")
    private List<String> evidenceUrls = new ArrayList<>();

    @Schema(description = "百科链接")
    private String wikipedia;

    @Schema(description = "官方网站链接")
    private String officialWebsite;

    @Schema(description = "人物基础信息")
    private PersonBasicInfo basicInfo = new PersonBasicInfo();

    public String getResolvedName() {
        return resolvedName;
    }

    public ResolvedPersonProfile setResolvedName(String resolvedName) {
        this.resolvedName = resolvedName;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public ResolvedPersonProfile setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public ResolvedPersonProfile setSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public String getEducationSummary() {
        return educationSummary;
    }

    public ResolvedPersonProfile setEducationSummary(String educationSummary) {
        this.educationSummary = educationSummary;
        return this;
    }

    public String getFamilyBackgroundSummary() {
        return familyBackgroundSummary;
    }

    public ResolvedPersonProfile setFamilyBackgroundSummary(String familyBackgroundSummary) {
        this.familyBackgroundSummary = familyBackgroundSummary;
        return this;
    }

    public String getCareerSummary() {
        return careerSummary;
    }

    public ResolvedPersonProfile setCareerSummary(String careerSummary) {
        this.careerSummary = careerSummary;
        return this;
    }

    public String getChinaRelatedStatementsSummary() {
        return chinaRelatedStatementsSummary;
    }

    public ResolvedPersonProfile setChinaRelatedStatementsSummary(String chinaRelatedStatementsSummary) {
        this.chinaRelatedStatementsSummary = chinaRelatedStatementsSummary;
        return this;
    }

    public String getPoliticalTendencySummary() {
        return politicalTendencySummary;
    }

    public ResolvedPersonProfile setPoliticalTendencySummary(String politicalTendencySummary) {
        this.politicalTendencySummary = politicalTendencySummary;
        return this;
    }

    public String getContactInformationSummary() {
        return contactInformationSummary;
    }

    public ResolvedPersonProfile setContactInformationSummary(String contactInformationSummary) {
        this.contactInformationSummary = contactInformationSummary;
        return this;
    }

    public String getFamilyMemberSituationSummary() {
        return familyMemberSituationSummary;
    }

    public ResolvedPersonProfile setFamilyMemberSituationSummary(String familyMemberSituationSummary) {
        this.familyMemberSituationSummary = familyMemberSituationSummary;
        return this;
    }

    public String getMisconductSummary() {
        return misconductSummary;
    }

    public ResolvedPersonProfile setMisconductSummary(String misconductSummary) {
        this.misconductSummary = misconductSummary;
        return this;
    }

    public List<String> getKeyFacts() {
        return keyFacts;
    }

    public ResolvedPersonProfile setKeyFacts(List<String> keyFacts) {
        this.keyFacts = keyFacts;
        return this;
    }

    public List<String> getTags() {
        return tags;
    }

    public ResolvedPersonProfile setTags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    public List<String> getEvidenceUrls() {
        return evidenceUrls;
    }

    public ResolvedPersonProfile setEvidenceUrls(List<String> evidenceUrls) {
        this.evidenceUrls = evidenceUrls;
        return this;
    }

    public String getWikipedia() {
        return wikipedia;
    }

    public ResolvedPersonProfile setWikipedia(String wikipedia) {
        this.wikipedia = wikipedia;
        return this;
    }

    public String getOfficialWebsite() {
        return officialWebsite;
    }

    public ResolvedPersonProfile setOfficialWebsite(String officialWebsite) {
        this.officialWebsite = officialWebsite;
        return this;
    }

    public PersonBasicInfo getBasicInfo() {
        return basicInfo;
    }

    public ResolvedPersonProfile setBasicInfo(PersonBasicInfo basicInfo) {
        this.basicInfo = basicInfo;
        return this;
    }
}
