package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "人物信息")
public class PersonInfo {

    @Schema(description = "识别或聚合后得到的人物姓名", example = "周杰伦")
    private String name;

    @JsonProperty("image_url")
    @Schema(description = "人物头像图片链接", example = "https://example.com/avatar.jpg")
    private String imageUrl;

    @Schema(description = "聚合后的结构化人物摘要", example = "周杰伦是华语流行音乐代表人物之一。")
    private String summary;

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
