package com.example.face2info.service.impl;

import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.client.NewsApiClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.NewsApiResponse;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PersonAggregate;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.internal.WebEvidence;
import com.example.face2info.entity.response.NewsItem;
import com.example.face2info.entity.response.SocialAccount;
import com.example.face2info.service.InformationAggregationService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
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
 * 基于识别证据的人物信息聚合实现。
 */
@Slf4j
@Service
public class InformationAggregationServiceImpl implements InformationAggregationService {

    private static final int MAX_PAGE_URLS = 20;
    private static final String SUMMARY_WARNING = "正文智能处理暂时不可用";

    private final SerpApiClient serpApiClient;
    private final NewsApiClient newsApiClient;
    private final JinaReaderClient jinaReaderClient;
    private final SummaryGenerationClient summaryGenerationClient;
    private final ThreadPoolTaskExecutor executor;

    public InformationAggregationServiceImpl(SerpApiClient serpApiClient,
                                             NewsApiClient newsApiClient,
                                             JinaReaderClient jinaReaderClient,
                                             SummaryGenerationClient summaryGenerationClient,
                                             @Qualifier("face2InfoExecutor") ThreadPoolTaskExecutor executor) {
        this.serpApiClient = serpApiClient;
        this.newsApiClient = newsApiClient;
        this.jinaReaderClient = jinaReaderClient;
        this.summaryGenerationClient = summaryGenerationClient;
        this.executor = executor;
    }

    @Override
    public AggregationResult aggregate(RecognitionEvidence evidence) {
        AggregationResult result = new AggregationResult();
        if (evidence == null) {
            return result.setErrors(List.of("recognition evidence is missing"));
        }

        result.getErrors().addAll(evidence.getErrors());

        ResolvedPersonProfile profile = resolveProfileFromEvidence(
                evidence.getWebEvidences(),
                firstSeedQuery(evidence),
                result.getWarnings()
        );
        String resolvedName = resolveNameOrFallback(profile, evidence);
        if (!StringUtils.hasText(resolvedName)) {
            result.getErrors().add("未能从识别证据中解析人物名称");
            result.setPerson(new PersonAggregate().setDescription(cleanDescription(profile.getSummary())));
            return result;
        }

        CompletableFuture<List<SocialAccount>> socialFuture = CompletableFuture
                .supplyAsync(() -> collectSocialAccounts(resolvedName), executor)
                .orTimeout(10, TimeUnit.SECONDS);

        CompletableFuture<PersonAggregate> personFuture = CompletableFuture
                .supplyAsync(() -> collectPersonInfo(resolvedName), executor)
                .orTimeout(10, TimeUnit.SECONDS);

        CompletableFuture<List<NewsItem>> newsFuture = CompletableFuture
                .supplyAsync(() -> collectNews(resolvedName), executor)
                .orTimeout(10, TimeUnit.SECONDS);

        PersonAggregate person = joinTask("实体信息", personFuture, new PersonAggregate().setName(resolvedName), result.getErrors());
        person.setName(resolvedName);
        person.setDescription(cleanDescription(firstNonBlank(profile.getSummary(), person.getDescription())));
        person.setSummary(cleanDescription(profile.getSummary()));
        person.setTags(profile.getTags() == null ? List.of() : profile.getTags());
        person.setEvidenceUrls(profile.getEvidenceUrls());

        result.setPerson(person);
        result.setSocialAccounts(deduplicateSocialAccounts(joinTask("社交账号", socialFuture, List.of(), result.getErrors())));
        result.setNews(deduplicateNews(resolvedName, joinTask("新闻", newsFuture, List.of(), result.getErrors())));
        return result;
    }

    ResolvedPersonProfile resolveProfileFromEvidence(List<WebEvidence> evidences, String fallbackName) {
        return resolveProfileFromEvidence(evidences, fallbackName, new ArrayList<>());
    }

    ResolvedPersonProfile resolveProfileFromEvidence(List<WebEvidence> evidences, String fallbackName, List<String> warnings) {
        List<String> urls = selectTopUrls(evidences);
        if (urls.isEmpty()) {
            return new ResolvedPersonProfile().setResolvedName(fallbackName);
        }

        List<PageContent> pages;
        try {
            pages = jinaReaderClient.readPages(urls);
        } catch (RuntimeException ex) {
            log.warn("Jina page read failed", ex);
            return new ResolvedPersonProfile()
                    .setResolvedName(fallbackName)
                    .setEvidenceUrls(urls);
        }
        if (pages == null || pages.isEmpty()) {
            return new ResolvedPersonProfile()
                    .setResolvedName(fallbackName)
                    .setEvidenceUrls(urls);
        }

        try {
            ResolvedPersonProfile profile = summaryGenerationClient.summarizePerson(fallbackName, pages);
            if (profile == null) {
                return new ResolvedPersonProfile().setResolvedName(fallbackName).setEvidenceUrls(urls);
            }
            if (profile.getEvidenceUrls() == null || profile.getEvidenceUrls().isEmpty()) {
                profile.setEvidenceUrls(urls);
            }
            return profile;
        } catch (RuntimeException ex) {
            log.error("Kimi summary generation failed, fallbackName={}, urlCount={}, category={}",
                    fallbackName, urls.size(), classifySummaryFailure(ex), ex);
            warnings.add(SUMMARY_WARNING);
            return new ResolvedPersonProfile()
                    .setResolvedName(fallbackName)
                    .setEvidenceUrls(urls);
        }
    }

    private String classifySummaryFailure(RuntimeException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("CONFIG_MISSING")) {
            return "CONFIG_MISSING";
        }
        if (message.contains("INVALID_RESPONSE")) {
            return "INVALID_RESPONSE";
        }
        if (message.contains("EMPTY_RESPONSE")) {
            return "EMPTY_RESPONSE";
        }
        if (message.toLowerCase().contains("timeout")) {
            return "TIMEOUT";
        }
        return "HTTP_ERROR";
    }

    private List<String> selectTopUrls(List<WebEvidence> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return List.of();
        }
        Set<String> urls = new LinkedHashSet<>();
        evidences.stream()
                .filter(item -> StringUtils.hasText(item.getUrl()))
                .forEach(item -> {
                    if (urls.size() < MAX_PAGE_URLS) {
                        urls.add(item.getUrl());
                    }
                });
        return new ArrayList<>(urls);
    }

    private String resolveNameOrFallback(ResolvedPersonProfile profile, RecognitionEvidence evidence) {
        if (profile != null && StringUtils.hasText(profile.getResolvedName())) {
            return profile.getResolvedName();
        }
        return firstSeedQuery(evidence);
    }

    private String firstSeedQuery(RecognitionEvidence evidence) {
        if (evidence == null || evidence.getSeedQueries().isEmpty()) {
            return null;
        }
        return evidence.getSeedQueries().get(0);
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

    private String firstNonBlank(String primary, String secondary) {
        return StringUtils.hasText(primary) ? primary : secondary;
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
            errors.add(label + "获取失败: " + cause.getMessage());
            return fallback;
        }
    }
}
