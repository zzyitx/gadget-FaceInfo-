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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * 信息聚合实现。
 * 并行查询社交账号、人物简介和新闻，再统一做清洗、去重和降级处理。
 */
@Slf4j
@Service
public class InformationAggregationServiceImpl implements InformationAggregationService {

    @Autowired
    private SerpApiClient serpApiClient;

    @Autowired
    private NewsApiClient newsApiClient;

    @Autowired
    @Qualifier("face2InfoExecutor")
    private ThreadPoolTaskExecutor executor;

    /**
     * 并行聚合目标人物的公开信息。
     */
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

        // 统一收口后再做清洗和去重，避免每个子任务各自维护不同规则。
        result.getPerson().setName(name);
        result.getPerson().setDescription(cleanDescription(result.getPerson().getDescription()));
        result.setSocialAccounts(deduplicateSocialAccounts(result.getSocialAccounts()));
        result.setNews(deduplicateNews(name, result.getNews()));
        return result;
    }

    /**
     * 搜索候选人物的公开社交账号。
     */
    private List<SocialAccount> collectSocialAccounts(String name) {
        log.info("Searching social accounts for {}", name);
        List<SocialAccount> accounts = new ArrayList<>();
        accounts.addAll(parseSocialResults("douyin", serpApiClient.googleSearch(name + " 抖音")));
        accounts.addAll(parseSocialResults("weibo", serpApiClient.googleSearch(name + " 微博")));
        return accounts;
    }

    /**
     * 搜索候选人物的简介、官网和百科地址。
     */
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

    /**
     * 查询相关新闻并转换为统一新闻结构。
     */
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

    /**
     * 从搜索结果中筛选指定平台的账号链接。
     */
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

    /**
     * 判断链接是否属于目标社交平台。
     */
    private boolean isPlatformLink(String platform, String link) {
        return switch (platform) {
            case "douyin" -> link.contains("douyin.com");
            case "weibo" -> link.contains("weibo.com");
            default -> false;
        };
    }

    /**
     * 从搜索标题中提取较干净的账号展示名。
     */
    private String extractUsername(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        return title.replace(" - 抖音", "")
                .replace(" - 微博", "")
                .replace("_微博", "")
                .trim();
    }

    /**
     * 清洗简介中的多余空白字符。
     */
    private String cleanDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        return description.replaceAll("\\s+", " ").trim();
    }

    /**
     * 按平台去重，只保留每个平台首个有效账号。
     */
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

    /**
     * 过滤掉不包含目标名称或重复的新闻。
     */
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

    /**
     * 依次尝试多个字段，返回第一个非空文本。
     */
    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 等待异步任务结果；失败时记录错误并返回降级值。
     */
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
