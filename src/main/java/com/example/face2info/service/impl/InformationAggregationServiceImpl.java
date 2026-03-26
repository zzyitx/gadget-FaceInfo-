package com.example.face2info.service.impl;

import com.example.face2info.client.NewsApiClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.NewsApiResponse;
import com.example.face2info.entity.internal.PersonAggregate;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.response.NewsItem;
import com.example.face2info.entity.response.SocialAccount;
import com.example.face2info.service.InformationAggregationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Service
/**
 * 信息聚合实现。
 * 并行查询社交账号、人物实体信息和新闻，并在最终阶段统一清洗去重。
 */
public class InformationAggregationServiceImpl implements InformationAggregationService {

    private static final Logger log = LoggerFactory.getLogger(InformationAggregationServiceImpl.class);

    private final SerpApiClient serpApiClient;
    private final NewsApiClient newsApiClient;
    private final ThreadPoolTaskExecutor executor;

    public InformationAggregationServiceImpl(SerpApiClient serpApiClient,
                                             NewsApiClient newsApiClient,
                                             @Qualifier("face2InfoExecutor") ThreadPoolTaskExecutor executor) {
        this.serpApiClient = serpApiClient;
        this.newsApiClient = newsApiClient;
        this.executor = executor;
    }

    @Override
    public AggregationResult aggregate(String name) {
        CompletableFuture<List<SocialAccount>> socialFuture = CompletableFuture
                .supplyAsync(() -> collectSocialAccounts(name), executor)
                .orTimeout(10, TimeUnit.SECONDS);

        CompletableFuture<PersonAggregate> personFuture = CompletableFuture
                .supplyAsync(() -> collectPersonInfo(name), executor)
                .orTimeout(10, TimeUnit.SECONDS);

        CompletableFuture<List<NewsItem>> newsFuture = CompletableFuture
                .supplyAsync(() -> collectNews(name), executor)
                .orTimeout(10, TimeUnit.SECONDS);

        AggregationResult result = new AggregationResult();
        result.setSocialAccounts(joinTask("社交账号", socialFuture, List.of(), result.getErrors()));
        result.setPerson(joinTask("实体信息", personFuture, new PersonAggregate().setName(name), result.getErrors()));
        result.setNews(joinTask("新闻", newsFuture, List.of(), result.getErrors()));

        result.getPerson().setName(name);
        result.getPerson().setDescription(cleanDescription(result.getPerson().getDescription()));
        result.setSocialAccounts(deduplicateSocialAccounts(result.getSocialAccounts()));
        result.setNews(deduplicateNews(name, result.getNews()));
        return result;
    }

    private List<SocialAccount> collectSocialAccounts(String name) {
        log.info("Searching social accounts for {}", name);
        List<SocialAccount> accounts = new ArrayList<>();
        accounts.addAll(parseSocialResults("douyin", serpApiClient.googleSearch(name + " 抖音")));
        accounts.addAll(parseSocialResults("weibo", serpApiClient.googleSearch(name + " 微博")));
        return accounts;
    }

    private PersonAggregate collectPersonInfo(String name) {
        log.info("Searching person details for {}", name);
        SerpApiResponse response = serpApiClient.googleSearch(name);
        JsonNode root = response.getRoot();
        JsonNode knowledgeGraph = root.path("knowledge_graph");
        PersonAggregate aggregate = new PersonAggregate().setName(name);
        if (!knowledgeGraph.isMissingNode() && !knowledgeGraph.isNull()) {
            aggregate.setDescription(knowledgeGraph.path("description").asText(null));
            aggregate.setOfficialWebsite(firstText(knowledgeGraph, "website", "official_website"));
            aggregate.setWikipedia(firstText(knowledgeGraph, "wikipedia", "wikipedia_url"));
        }
        if (!StringUtils.hasText(aggregate.getDescription())) {
            JsonNode organicResults = root.path("organic_results");
            if (organicResults.isArray()) {
                for (JsonNode item : organicResults) {
                    String snippet = item.path("snippet").asText(null);
                    if (StringUtils.hasText(snippet)) {
                        aggregate.setDescription(snippet);
                        break;
                    }
                }
            }
        }
        return aggregate;
    }

    private List<NewsItem> collectNews(String name) {
        log.info("Searching news for {}", name);
        NewsApiResponse response = newsApiClient.searchNews(name);
        List<NewsItem> items = new ArrayList<>();
        JsonNode articles = response.getRoot().path("articles");
        if (articles.isArray()) {
            for (JsonNode article : articles) {
                items.add(new NewsItem()
                        .setTitle(article.path("title").asText(null))
                        .setSummary(article.path("description").asText(null))
                        .setPublishedAt(article.path("publishedAt").asText(null))
                        .setUrl(article.path("url").asText(null))
                        .setSource(article.path("source").path("name").asText(null)));
            }
        }
        return items;
    }

    private List<SocialAccount> parseSocialResults(String platform, SerpApiResponse response) {
        List<SocialAccount> accounts = new ArrayList<>();
        JsonNode organicResults = response.getRoot().path("organic_results");
        if (!organicResults.isArray()) {
            return accounts;
        }
        for (JsonNode item : organicResults) {
            String link = item.path("link").asText("");
            if (!isPlatformLink(platform, link)) {
                continue;
            }
            String title = item.path("title").asText(null);
            accounts.add(new SocialAccount()
                    .setPlatform(platform)
                    .setUrl(link)
                    .setUsername(extractUsername(title)));
        }
        return accounts;
    }

    private boolean isPlatformLink(String platform, String link) {
        return switch (platform) {
            case "douyin" -> link.contains("douyin.com");
            case "weibo" -> link.contains("weibo.com");
            default -> false;
        };
    }

    private String extractUsername(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        return title.replace(" - 抖音", "")
                .replace(" - 微博", "")
                .replace("_微博", "")
                .trim();
    }

    private String cleanDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        return description.replaceAll("\\s+", " ").trim();
    }

    private List<SocialAccount> deduplicateSocialAccounts(List<SocialAccount> accounts) {
        Map<String, SocialAccount> deduplicated = new LinkedHashMap<>();
        for (SocialAccount account : accounts) {
            if (!StringUtils.hasText(account.getUrl())) {
                continue;
            }
            deduplicated.putIfAbsent(account.getPlatform(), account);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private List<NewsItem> deduplicateNews(String name, List<NewsItem> items) {
        Set<String> seen = new LinkedHashSet<>();
        List<NewsItem> filtered = new ArrayList<>();
        for (NewsItem item : items) {
            String title = item.getTitle();
            String source = item.getSource();
            if (!StringUtils.hasText(title) || !title.contains(name)) {
                continue;
            }
            String key = title + "|" + source;
            if (seen.add(key)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private <T> T joinTask(String label, CompletableFuture<T> future, T fallback, List<String> errors) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.error("{} aggregation failed", label, cause);
            errors.add(label + "获取失败：" + cause.getMessage());
            return fallback;
        }
    }
}
