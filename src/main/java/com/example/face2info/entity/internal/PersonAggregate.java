package com.example.face2info.entity.internal;

/**
 * 内部使用的人物聚合结果。
 */
public class PersonAggregate {

    private String name;
    private String description;
    private String wikipedia;
    private String officialWebsite;

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
}
