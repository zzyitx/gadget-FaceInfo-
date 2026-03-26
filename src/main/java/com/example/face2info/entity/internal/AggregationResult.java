package com.example.face2info.entity.internal;

import com.example.face2info.entity.response.NewsItem;
import com.example.face2info.entity.response.SocialAccount;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合阶段内部结果对象。
 * 统一承载人物信息、社交账号、新闻和部分失败信息。
 */
public class AggregationResult {

    private PersonAggregate person = new PersonAggregate();
    private List<SocialAccount> socialAccounts = new ArrayList<>();
    private List<NewsItem> news = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public PersonAggregate getPerson() {
        return person;
    }

    public AggregationResult setPerson(PersonAggregate person) {
        this.person = person;
        return this;
    }

    public List<SocialAccount> getSocialAccounts() {
        return socialAccounts;
    }

    public AggregationResult setSocialAccounts(List<SocialAccount> socialAccounts) {
        this.socialAccounts = socialAccounts;
        return this;
    }

    public List<NewsItem> getNews() {
        return news;
    }

    public AggregationResult setNews(List<NewsItem> news) {
        this.news = news;
        return this;
    }

    public List<String> getErrors() {
        return errors;
    }

    public AggregationResult setErrors(List<String> errors) {
        this.errors = errors;
        return this;
    }
}
