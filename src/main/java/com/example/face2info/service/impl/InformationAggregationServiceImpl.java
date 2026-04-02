package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.client.NewsApiClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
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

@Slf4j
@Service
public class InformationAggregationServiceImpl implements InformationAggregationService {

    private static final String SUMMARY_WARNING = "正文智能处理暂时不可用";
    private static final String KIMI_SUFFIX = " (由 Kimi 总结)";
    private static final String SERP_API_SUFFIX = " (由 SerpAPI 聚合)";
    private static final String SOCIAL_PLACEHOLDER_PLATFORM = "pending";
    private static final String SOCIAL_PLACEHOLDER_URL = "#";
    private static final String SOCIAL_PLACEHOLDER_USERNAME = "功能正在开发中";

    private final GoogleSearchClient googleSearchClient;
    private final SerpApiClient serpApiClient;
    private final NewsApiClient newsApiClient;
    private final JinaReaderClient jinaReaderClient;
    private final SummaryGenerationClient summaryGenerationClient;
    private final ThreadPoolTaskExecutor executor;
    private final ApiProperties properties;

    @Autowired
    public InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                             SerpApiClient serpApiClient,
                                             NewsApiClient newsApiClient,
                                             JinaReaderClient jinaReaderClient,
                                             SummaryGenerationClient summaryGenerationClient,
                                             @Qualifier("face2InfoExecutor") ThreadPoolTaskExecutor executor,
                                             ApiProperties properties) {
        this.googleSearchClient = googleSearchClient;
        this.serpApiClient = serpApiClient;
        this.newsApiClient = newsApiClient;
        this.jinaReaderClient = jinaReaderClient;
        this.summaryGenerationClient = summaryGenerationClient;
        this.executor = executor;
        this.properties = properties;
    }

    InformationAggregationServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      NewsApiClient newsApiClient,
                                      JinaReaderClient jinaReaderClient,
                                      SummaryGenerationClient summaryGenerationClient,
                                      ThreadPoolTaskExecutor executor) {
        this(
                googleSearchClient,
                serpApiClient,
                newsApiClient,
                jinaReaderClient,
                summaryGenerationClient,
                executor,
                new ApiProperties()
        );
    }

    @Override
    public AggregationResult aggregate(RecognitionEvidence evidence) {
        AggregationResult result = new AggregationResult();
        if (evidence == null) {
            log.warn("信息聚合跳过：缺少识别证据");
            return result.setErrors(List.of("缺少识别证据"));
        }

        result.getErrors().addAll(evidence.getErrors());
        log.info("信息聚合开始 seedQueryCount={} webEvidenceCount={} upstreamErrorCount={}",
                evidence.getSeedQueries().size(), evidence.getWebEvidences().size(), evidence.getErrors().size());

        ResolvedPersonProfile profile = resolveProfileFromEvidence(
                evidence.getWebEvidences(),
                firstSeedQuery(evidence),
                result.getWarnings()
        );
        String resolvedName = resolveNameOrFallback(profile, evidence);
        log.info("正文总结阶段完成 resolvedName={} summaryAvailable={} tagCount={} evidenceUrlCount={} warningCount={}",
                resolvedName,
                StringUtils.hasText(profile.getSummary()),
                profile.getTags() == null ? 0 : profile.getTags().size(),
                profile.getEvidenceUrls() == null ? 0 : profile.getEvidenceUrls().size(),
                result.getWarnings().size());
        if (!StringUtils.hasText(resolvedName)) {
            result.getErrors().add("未能从识别证据中解析人物名称");
            result.setPerson(new PersonAggregate().setDescription(appendSuffix(cleanDescription(profile.getSummary()), KIMI_SUFFIX)));
            log.warn("信息聚合失败：无法解析人物名称 errorCount={}", result.getErrors().size());
            return result;
        }

        CompletableFuture<List<SocialAccount>> socialFuture = CompletableFuture
                .supplyAsync(() -> collectSocialAccounts(resolvedName), executor)
                .orTimeout(10, TimeUnit.SECONDS);

        CompletableFuture<PersonAggregate> personFuture = CompletableFuture
                .supplyAsync(() -> collectPersonInfo(resolvedName), executor)
                .orTimeout(10, TimeUnit.SECONDS);

        PersonAggregate person = joinTask("人物详情", personFuture, new PersonAggregate().setName(resolvedName), result.getErrors());
        person.setName(resolvedName);
        String summarizedDescription = cleanDescription(profile.getSummary());
        String fallbackDescription = cleanDescription(person.getDescription());
        person.setDescription(formatDescription(summarizedDescription, fallbackDescription));
        person.setSummary(appendSuffix(summarizedDescription, KIMI_SUFFIX));
        person.setTags(profile.getTags() == null ? List.of() : profile.getTags());
        person.setEvidenceUrls(profile.getEvidenceUrls());

        result.setPerson(person);
        result.setSocialAccounts(deduplicateSocialAccounts(joinTask("社交账号", socialFuture, List.of(), result.getErrors())));
        result.setNews(List.of());
        log.info("信息聚合完成 personName={} socialCount={} newsCount={} warningCount={} errorCount={}",
                person.getName(), result.getSocialAccounts().size(), result.getNews().size(),
                result.getWarnings().size(), result.getErrors().size());
        return result;
    }

    ResolvedPersonProfile resolveProfileFromEvidence(List<WebEvidence> evidences, String fallbackName) {
        return resolveProfileFromEvidence(evidences, fallbackName, new ArrayList<>());
    }

    ResolvedPersonProfile resolveProfileFromEvidence(List<WebEvidence> evidences, String fallbackName, List<String> warnings) {
        List<String> urls = selectTopUrls(evidences);
        log.info("正文总结准备完成 fallbackName={} selectedUrlCount={}", fallbackName, urls.size());
        if (urls.isEmpty()) {
            log.info("正文总结跳过：没有可用网页证据 fallbackName={}", fallbackName);
            return new ResolvedPersonProfile().setResolvedName(fallbackName);
        }

        List<PageContent> pages = List.of();
        try {
            pages = jinaReaderClient.readPages(urls);
            log.info("Jina 正文提取完成 fallbackName={} pageCount={}", fallbackName, pages == null ? 0 : pages.size());
        } catch (RuntimeException ex) {
            log.warn("Jina 正文提取失败 fallbackName={} urlCount={} error={}", fallbackName, urls.size(), ex.getMessage(), ex);
        }
        if (pages == null || pages.isEmpty()) {
            pages = buildFallbackPages(evidences, urls);
            log.info("正文总结兜底页面构建完成 fallbackName={} pageCount={}", fallbackName, pages.size());
        }
        if (pages.isEmpty()) {
            log.info("正文总结跳过：既无可用 Jina 正文也无可用网页证据 fallbackName={}", fallbackName);
            return new ResolvedPersonProfile()
                    .setResolvedName(fallbackName)
                    .setEvidenceUrls(urls);
        }

        try {
            ResolvedPersonProfile profile = summaryGenerationClient.summarizePerson(fallbackName, pages);
            if (profile == null) {
                log.warn("Kimi 总结返回空结果 fallbackName={} pageCount={}", fallbackName, pages.size());
                return new ResolvedPersonProfile().setResolvedName(fallbackName).setEvidenceUrls(urls);
            }
            if (profile.getEvidenceUrls() == null || profile.getEvidenceUrls().isEmpty()) {
                profile.setEvidenceUrls(urls);
            }
            log.info("Kimi 总结成功 fallbackName={} resolvedName={} summaryLength={} tagCount={}",
                    fallbackName,
                    profile.getResolvedName(),
                    profile.getSummary() == null ? 0 : profile.getSummary().length(),
                    profile.getTags() == null ? 0 : profile.getTags().size());
            return profile;
        } catch (RuntimeException ex) {
            String category = classifySummaryFailure(ex);
            log.error("Kimi 总结失败 fallbackName={} urlCount={} category={} error={}",
                    fallbackName, urls.size(), category, ex.getMessage(), ex);
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
        int maxPageReads = Math.max(1, properties.getApi().getJina().getMaxPageReads());
        Set<String> urls = new LinkedHashSet<>();
        evidences.stream()
                .filter(item -> StringUtils.hasText(item.getUrl()))
                .forEach(item -> {
                    if (urls.size() < maxPageReads) {
                        urls.add(item.getUrl());
                    }
                });
        return new ArrayList<>(urls);
    }

    private List<PageContent> buildFallbackPages(List<WebEvidence> evidences, List<String> selectedUrls) {
        if (evidences == null || evidences.isEmpty() || selectedUrls == null || selectedUrls.isEmpty()) {
            return List.of();
        }
        Set<String> selected = new LinkedHashSet<>(selectedUrls);
        List<PageContent> pages = new ArrayList<>();
        for (WebEvidence evidence : evidences) {
            if (evidence == null || !selected.contains(evidence.getUrl())) {
                continue;
            }
            String content = buildFallbackContent(evidence);
            if (!StringUtils.hasText(content)) {
                continue;
            }
            pages.add(new PageContent()
                    .setUrl(evidence.getUrl())
                    .setTitle(evidence.getTitle())
                    .setContent(content)
                    .setSourceEngine(StringUtils.hasText(evidence.getSourceEngine()) ? evidence.getSourceEngine() : "evidence"));
        }
        return pages;
    }

    private String buildFallbackContent(WebEvidence evidence) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(evidence.getSnippet())) {
            parts.add(evidence.getSnippet().trim());
        }
        if (StringUtils.hasText(evidence.getSource())) {
            parts.add(evidence.getSource().trim());
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("\n", parts);
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
        log.info("社交账号聚合跳过 resolvedName={} reason=feature_in_progress", name);
        return List.of(new SocialAccount()
                .setPlatform(SOCIAL_PLACEHOLDER_PLATFORM)
                .setUrl(SOCIAL_PLACEHOLDER_URL)
                .setUsername(SOCIAL_PLACEHOLDER_USERNAME));
    }

    private PersonAggregate collectPersonInfo(String name) {
        String normalizedName = normalizeName(name);
        log.info("人物详情聚合开始 resolvedName={} normalizedName={}", name, normalizedName);
        SerpApiResponse response = googleSearchClient.googleSearch(normalizedName);
        if (response == null || response.getRoot() == null) {
            log.warn("人物详情聚合返回空结果 resolvedName={}", name);
            return new PersonAggregate().setName(name);
        }
        JsonNode root = response.getRoot();
        JsonNode knowledgeGraph = firstPresent(root, "knowledgeGraph", "knowledge_graph");
        PersonAggregate aggregate = new PersonAggregate().setName(name);
        if (knowledgeGraph != null && !knowledgeGraph.isMissingNode() && !knowledgeGraph.isNull()) {
            aggregate.setDescription(knowledgeGraph.path("description").asText(null));
            aggregate.setOfficialWebsite(firstText(knowledgeGraph, "website", "official_website"));
            aggregate.setWikipedia(firstText(knowledgeGraph, "wikipedia", "wikipedia_url"));
        }
        if (!StringUtils.hasText(aggregate.getDescription())) {
            JsonNode organicResults = root.path("organic");
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
        log.info("人物详情聚合完成 resolvedName={} descriptionAvailable={} websiteAvailable={} wikipediaAvailable={}",
                name,
                StringUtils.hasText(aggregate.getDescription()),
                StringUtils.hasText(aggregate.getOfficialWebsite()),
                StringUtils.hasText(aggregate.getWikipedia()));
        return aggregate;
    }

    private List<NewsItem> collectNews(String name) {
        log.info("新闻聚合开始 resolvedName={}", name);
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
        log.info("新闻聚合完成 resolvedName={} newsCount={}", name, items.size());
        return items;
    }

    private List<SocialAccount> parseSocialResults(String platform, SerpApiResponse response) {
        List<SocialAccount> accounts = new ArrayList<>();
        if (response == null || response.getRoot() == null) {
            return accounts;
        }
        JsonNode organicResults = response.getRoot().path("organic");
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

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return name.replaceAll("\\s+", "");
    }

    private String formatDescription(String summarizedDescription, String fallbackDescription) {
        if (StringUtils.hasText(summarizedDescription)) {
            return appendSuffix(summarizedDescription, KIMI_SUFFIX);
        }
        return appendSuffix(fallbackDescription, SERP_API_SUFFIX);
    }

    private String appendSuffix(String content, String suffix) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        if (content.endsWith(suffix)) {
            return content;
        }
        return content + suffix;
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

    private JsonNode firstPresent(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode value = root.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private <T> T joinTask(String label, CompletableFuture<T> future, T fallback, List<String> errors) {
        try {
            T result = future.join();
            if (result instanceof List<?> list) {
                log.info("{}任务完成 itemCount={}", label, list.size());
            } else {
                log.info("{}任务完成", label);
            }
            return result;
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.error("{}任务失败 error={}", label, cause.getMessage(), cause);
            errors.add(label + "获取失败: " + cause.getMessage());
            return fallback;
        }
    }
}
