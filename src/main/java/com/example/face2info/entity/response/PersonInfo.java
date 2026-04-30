package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "人物信息")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PersonInfo {

    @Schema(description = "识别或聚合后得到的人物姓名", example = "周杰伦")
    private String name;

    @JsonProperty("image_url")
    @Schema(description = "人物头像图片链接", example = "https://example.com/avatar.jpg")
    private String imageUrl;

    @Schema(description = "聚合后的结构化人物摘要", example = "周杰伦是华语流行音乐代表人物之一。")
    private String summary;

    @JsonProperty("summary_paragraphs")
    @Schema(description = "人物主体摘要段落")
    private List<ParagraphWithSources> summaryParagraphs = new ArrayList<>();

    @JsonProperty("education_summary")
    @Schema(description = "人物教育经历摘要", example = "毕业于淡江中学音乐班。")
    private String educationSummary;

    @JsonProperty("education_summary_paragraphs")
    @Schema(description = "人物教育经历摘要段落")
    private List<ParagraphWithSources> educationSummaryParagraphs = new ArrayList<>();

    @JsonProperty("family_background_summary")
    @Schema(description = "人物家庭背景摘要", example = "出身于台湾普通家庭，母亲对其音乐启蒙影响较大。")
    private String familyBackgroundSummary;

    @JsonProperty("family_background_summary_paragraphs")
    @Schema(description = "人物家庭背景摘要段落")
    private List<ParagraphWithSources> familyBackgroundSummaryParagraphs = new ArrayList<>();

    @JsonProperty("career_summary")
    @Schema(description = "人物职业经历摘要", example = "先以创作人身份进入行业，后发展为歌手、导演与制作人。")
    private String careerSummary;

    @JsonProperty("career_summary_paragraphs")
    @Schema(description = "人物职业经历摘要段落")
    private List<ParagraphWithSources> careerSummaryParagraphs = new ArrayList<>();

    @JsonProperty("china_related_statements_summary")
    @Schema(description = "人物涉华言论摘要", example = "曾就中国政治、经济文化及中美关系发表公开评价。")
    private String chinaRelatedStatementsSummary;

    @JsonProperty("china_related_statements_summary_paragraphs")
    @Schema(description = "人物涉华言论摘要段落")
    private List<ParagraphWithSources> chinaRelatedStatementsSummaryParagraphs = new ArrayList<>();

    @JsonProperty("political_tendency_summary")
    @Schema(description = "人物政治倾向摘要", example = "公开支持自由主义和多边贸易等理念。")
    private String politicalTendencySummary;

    @JsonProperty("political_tendency_summary_paragraphs")
    @Schema(description = "人物政治倾向摘要段落")
    private List<ParagraphWithSources> politicalTendencySummaryParagraphs = new ArrayList<>();

    @JsonProperty("contact_information_summary")
    @Schema(description = "人物地址信息摘要", example = "整理公开办公电话、官方邮箱和认证社交账号。")
    private String contactInformationSummary;

    @JsonProperty("contact_information_summary_paragraphs")
    @Schema(description = "人物地址信息摘要段落")
    private List<ParagraphWithSources> contactInformationSummaryParagraphs = new ArrayList<>();

    @JsonProperty("family_member_situation_summary")
    @Schema(description = "人物家族成员情况摘要", example = "概述直系亲属、经商情况与涉华利益往来。")
    private String familyMemberSituationSummary;

    @JsonProperty("family_member_situation_summary_paragraphs")
    @Schema(description = "人物家族成员情况摘要段落")
    private List<ParagraphWithSources> familyMemberSituationSummaryParagraphs = new ArrayList<>();

    @JsonProperty("misconduct_summary")
    @Schema(description = "人物污点劣迹摘要", example = "汇总公开行政处罚、违法记录和失德争议事件。")
    private String misconductSummary;

    @JsonProperty("misconduct_summary_paragraphs")
    @Schema(description = "人物污点劣迹摘要段落")
    private List<ParagraphWithSources> misconductSummaryParagraphs = new ArrayList<>();

    @Schema(description = "人物百科词条链接", example = "https://zh.wikipedia.org/wiki/%E5%91%A8%E6%9D%B0%E4%BC%A6")
    private String wikipedia;

    @JsonProperty("official_website")
    @Schema(description = "人物官方网站链接", example = "https://www.jvrmusic.com/")
    private String officialWebsite;

    @JsonProperty("social_accounts")
    @Schema(description = "公开可访问的社交账号列表")
    private List<SocialAccount> socialAccounts = new ArrayList<>();

    @Schema(description = "从正文或资料中提炼的人物标签")
    private List<String> tags = new ArrayList<>();

    @JsonProperty("article_sources")
    @Schema(description = "全局文章来源列表")
    private List<ArticleSourceBadge> articleSources = new ArrayList<>();

    @JsonProperty("total_articles_read")
    @Schema(description = "聚合链路实际读取并生成篇级摘要的文章总数")
    private Integer totalArticlesRead;

    @JsonProperty("final_articles_used")
    @Schema(description = "最终人物画像实际引用使用的文章数")
    private Integer finalArticlesUsed;

    @JsonProperty("evidence_urls")
    @Schema(description = "支撑当前人物画像的证据链接")
    private List<String> evidenceUrls = new ArrayList<>();

    @JsonProperty("basic_info")
    @Schema(description = "人物基础信息")
    private PersonBasicInfoResponse basicInfo = new PersonBasicInfoResponse();

    public String getName() {
        return name;
    }

    public PersonInfo setName(String name) {
        this.name = name;
        return this;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public PersonInfo setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public PersonInfo setSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public List<ParagraphWithSources> getSummaryParagraphs() {
        return summaryParagraphs;
    }

    public PersonInfo setSummaryParagraphs(List<ParagraphWithSources> summaryParagraphs) {
        this.summaryParagraphs = summaryParagraphs == null ? new ArrayList<>() : summaryParagraphs;
        return this;
    }

    public String getEducationSummary() {
        return educationSummary;
    }

    public PersonInfo setEducationSummary(String educationSummary) {
        this.educationSummary = educationSummary;
        return this;
    }

    public List<ParagraphWithSources> getEducationSummaryParagraphs() {
        return educationSummaryParagraphs;
    }

    public PersonInfo setEducationSummaryParagraphs(List<ParagraphWithSources> educationSummaryParagraphs) {
        this.educationSummaryParagraphs = educationSummaryParagraphs == null ? new ArrayList<>() : educationSummaryParagraphs;
        return this;
    }

    public String getFamilyBackgroundSummary() {
        return familyBackgroundSummary;
    }

    public PersonInfo setFamilyBackgroundSummary(String familyBackgroundSummary) {
        this.familyBackgroundSummary = familyBackgroundSummary;
        return this;
    }

    public List<ParagraphWithSources> getFamilyBackgroundSummaryParagraphs() {
        return familyBackgroundSummaryParagraphs;
    }

    public PersonInfo setFamilyBackgroundSummaryParagraphs(List<ParagraphWithSources> familyBackgroundSummaryParagraphs) {
        this.familyBackgroundSummaryParagraphs = familyBackgroundSummaryParagraphs == null ? new ArrayList<>() : familyBackgroundSummaryParagraphs;
        return this;
    }

    public String getCareerSummary() {
        return careerSummary;
    }

    public PersonInfo setCareerSummary(String careerSummary) {
        this.careerSummary = careerSummary;
        return this;
    }

    public List<ParagraphWithSources> getCareerSummaryParagraphs() {
        return careerSummaryParagraphs;
    }

    public PersonInfo setCareerSummaryParagraphs(List<ParagraphWithSources> careerSummaryParagraphs) {
        this.careerSummaryParagraphs = careerSummaryParagraphs == null ? new ArrayList<>() : careerSummaryParagraphs;
        return this;
    }

    public String getChinaRelatedStatementsSummary() {
        return chinaRelatedStatementsSummary;
    }

    public PersonInfo setChinaRelatedStatementsSummary(String chinaRelatedStatementsSummary) {
        this.chinaRelatedStatementsSummary = chinaRelatedStatementsSummary;
        return this;
    }

    public List<ParagraphWithSources> getChinaRelatedStatementsSummaryParagraphs() {
        return chinaRelatedStatementsSummaryParagraphs;
    }

    public PersonInfo setChinaRelatedStatementsSummaryParagraphs(List<ParagraphWithSources> chinaRelatedStatementsSummaryParagraphs) {
        this.chinaRelatedStatementsSummaryParagraphs = chinaRelatedStatementsSummaryParagraphs == null ? new ArrayList<>() : chinaRelatedStatementsSummaryParagraphs;
        return this;
    }

    public String getPoliticalTendencySummary() {
        return politicalTendencySummary;
    }

    public PersonInfo setPoliticalTendencySummary(String politicalTendencySummary) {
        this.politicalTendencySummary = politicalTendencySummary;
        return this;
    }

    public List<ParagraphWithSources> getPoliticalTendencySummaryParagraphs() {
        return politicalTendencySummaryParagraphs;
    }

    public PersonInfo setPoliticalTendencySummaryParagraphs(List<ParagraphWithSources> politicalTendencySummaryParagraphs) {
        this.politicalTendencySummaryParagraphs = politicalTendencySummaryParagraphs == null ? new ArrayList<>() : politicalTendencySummaryParagraphs;
        return this;
    }

    public String getContactInformationSummary() {
        return contactInformationSummary;
    }

    public PersonInfo setContactInformationSummary(String contactInformationSummary) {
        this.contactInformationSummary = contactInformationSummary;
        return this;
    }

    public List<ParagraphWithSources> getContactInformationSummaryParagraphs() {
        return contactInformationSummaryParagraphs;
    }

    public PersonInfo setContactInformationSummaryParagraphs(List<ParagraphWithSources> contactInformationSummaryParagraphs) {
        this.contactInformationSummaryParagraphs = contactInformationSummaryParagraphs == null ? new ArrayList<>() : contactInformationSummaryParagraphs;
        return this;
    }

    public String getFamilyMemberSituationSummary() {
        return familyMemberSituationSummary;
    }

    public PersonInfo setFamilyMemberSituationSummary(String familyMemberSituationSummary) {
        this.familyMemberSituationSummary = familyMemberSituationSummary;
        return this;
    }

    public List<ParagraphWithSources> getFamilyMemberSituationSummaryParagraphs() {
        return familyMemberSituationSummaryParagraphs;
    }

    public PersonInfo setFamilyMemberSituationSummaryParagraphs(List<ParagraphWithSources> familyMemberSituationSummaryParagraphs) {
        this.familyMemberSituationSummaryParagraphs = familyMemberSituationSummaryParagraphs == null ? new ArrayList<>() : familyMemberSituationSummaryParagraphs;
        return this;
    }

    public String getMisconductSummary() {
        return misconductSummary;
    }

    public PersonInfo setMisconductSummary(String misconductSummary) {
        this.misconductSummary = misconductSummary;
        return this;
    }

    public List<ParagraphWithSources> getMisconductSummaryParagraphs() {
        return misconductSummaryParagraphs;
    }

    public PersonInfo setMisconductSummaryParagraphs(List<ParagraphWithSources> misconductSummaryParagraphs) {
        this.misconductSummaryParagraphs = misconductSummaryParagraphs == null ? new ArrayList<>() : misconductSummaryParagraphs;
        return this;
    }

    public String getWikipedia() {
        return wikipedia;
    }

    public PersonInfo setWikipedia(String wikipedia) {
        this.wikipedia = wikipedia;
        return this;
    }

    public String getOfficialWebsite() {
        return officialWebsite;
    }

    public PersonInfo setOfficialWebsite(String officialWebsite) {
        this.officialWebsite = officialWebsite;
        return this;
    }

    public List<SocialAccount> getSocialAccounts() {
        return socialAccounts;
    }

    public PersonInfo setSocialAccounts(List<SocialAccount> socialAccounts) {
        this.socialAccounts = socialAccounts;
        return this;
    }

    public List<String> getTags() {
        return tags;
    }

    public PersonInfo setTags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    public List<ArticleSourceBadge> getArticleSources() {
        return articleSources;
    }

    public PersonInfo setArticleSources(List<ArticleSourceBadge> articleSources) {
        this.articleSources = articleSources;
        return this;
    }

    public Integer getTotalArticlesRead() {
        return totalArticlesRead;
    }

    public PersonInfo setTotalArticlesRead(Integer totalArticlesRead) {
        this.totalArticlesRead = totalArticlesRead;
        return this;
    }

    public Integer getFinalArticlesUsed() {
        return finalArticlesUsed;
    }

    public PersonInfo setFinalArticlesUsed(Integer finalArticlesUsed) {
        this.finalArticlesUsed = finalArticlesUsed;
        return this;
    }

    public List<String> getEvidenceUrls() {
        return evidenceUrls;
    }

    public PersonInfo setEvidenceUrls(List<String> evidenceUrls) {
        this.evidenceUrls = evidenceUrls;
        return this;
    }

    public PersonBasicInfoResponse getBasicInfo() {
        return basicInfo;
    }

    public PersonInfo setBasicInfo(PersonBasicInfoResponse basicInfo) {
        this.basicInfo = basicInfo;
        return this;
    }
}
