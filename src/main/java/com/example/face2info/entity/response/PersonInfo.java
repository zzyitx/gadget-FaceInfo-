package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 人物信息 DTO。
 */
@Schema(description = "人物信息")
public class PersonInfo {

    private String name;
    private String description;
    private String summary;
    private String wikipedia;

    @JsonProperty("official_website")
    private String officialWebsite;

    @JsonProperty("social_accounts")
    private List<SocialAccount> socialAccounts = new ArrayList<>();

    private List<String> tags = new ArrayList<>();

    public String getName() {
        return name;
    }

    public PersonInfo setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public PersonInfo setDescription(String description) {
        this.description = description;
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
}
