package com.example.face2info.entity.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * 内部使用的人物聚合结果。
 */
public class PersonAggregate {

    private String name;
    private String description;
    private String summary;
    private String wikipedia;
    private String officialWebsite;
    private List<String> tags = new ArrayList<>();
    private List<String> evidenceUrls = new ArrayList<>();

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
}
