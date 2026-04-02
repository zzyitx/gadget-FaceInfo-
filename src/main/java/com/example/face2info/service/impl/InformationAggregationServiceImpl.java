package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.client.NewsApiClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.PersonAggregate;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.WebEvidence;
import com.example.face2info.entity.response.SocialAccount;
import com.example.face2info.service.InformationAggregationService;
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
    private static final String SOCIAL_PLACEHOLDER_PLATFORM = "pending";
    private static final String SOCIAL_PLACEHOLDER_URL = "#";
    private static final String SOCIAL_PLACEHOLDER_USERNAME = "功能正在开发中";

    @SuppressWarnings("unused")
    private final GoogleSearchClient googleSearchClient;
    @SuppressWarnings("unused")
    private final SerpApiClient serpApiClient;
    @SuppressWarnings("unused")
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
        this(googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor, new ApiProperties());
    }

    @Override
    public AggregationResult aggregate(RecognitionEvidence evidence) {
        AggregationResult result = new AggregationResult();
        if (evidence == null) {
            log.warn("信息聚合跳过：缺少识别证据");
            return result.setErrors(List.of("缺少识别证据"));
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
            result.setPerson(new PersonAggregate()
                    .setDescription(appendSuffix(cleanText(profile.getDescription()), KIMI_SUFFIX))
                    .setSummary(appendSuffix(cleanText(profile.getSummary()), KIMI_SUFFIX))
                    .setBasicInfo(profile.getBasicInfo())
                    .setOfficialWebsite(profile.getOfficialWebsite())
                    .setWikipedia(profile.getWikipedia()));
            return result;
        }

        CompletableFuture<List<SocialAccount>> socialFuture = CompletableFuture
                .supplyAsync(() -> collectSocialAccounts(resolvedName), executor)
                .orTimeout(10, TimeUnit.SECONDS);

        result.setPerson(buildPersonFromProfile(profile, resolvedName));
        result.setSocialAccounts(deduplicateSocialAccounts(joinTask("社交账号", socialFuture, List.of(), result.getErrors())));
        result.setNews(List.of());
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

        List<PageContent> pages = List.of();
        try {
            pages = jinaReaderClient.readPages(urls);
        } catch (RuntimeException ex) {
            log.warn("Jina 正文提取失败 fallbackName={} urlCount={} error={}", fallbackName, urls.size(), ex.getMessage(), ex);
        }
        if (pages == null || pages.isEmpty()) {
            pages = buildFallbackPages(evidences, urls);
        }
        if (pages.isEmpty()) {
            return new ResolvedPersonProfile()
                    .setResolvedName(fallbackName)
                    .setEvidenceUrls(urls);
        }

        List<PageSummary> pageSummaries = collectPageSummaries(fallbackName, pages);
        if (pageSummaries.isEmpty()) {
            warnings.add(SUMMARY_WARNING);
            return new ResolvedPersonProfile()
                    .setResolvedName(fallbackName)
                    .setEvidenceUrls(urls);
        }

        try {
            ResolvedPersonProfile profile = summaryGenerationClient.summarizePersonFromPageSummaries(fallbackName, pageSummaries);
            if (profile == null) {
                warnings.add(SUMMARY_WARNING);
                return new ResolvedPersonProfile().setResolvedName(fallbackName).setEvidenceUrls(urls);
            }
            if (profile.getEvidenceUrls() == null || profile.getEvidenceUrls().isEmpty()) {
                profile.setEvidenceUrls(pageSummaries.stream()
                        .map(PageSummary::getSourceUrl)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList());
            }
            return profile;
        } catch (RuntimeException ex) {
            log.error("Kimi 最终总结失败 fallbackName={} pageSummaryCount={} category={} error={}",
                    fallbackName, pageSummaries.size(), classifySummaryFailure(ex), ex.getMessage(), ex);
            warnings.add(SUMMARY_WARNING);
            return new ResolvedPersonProfile()
                    .setResolvedName(fallbackName)
                    .setEvidenceUrls(urls);
        }
    }

    private PersonAggregate buildPersonFromProfile(ResolvedPersonProfile profile, String resolvedName) {
        String shortDescription = cleanText(profile.getDescription());
        String longSummary = cleanText(profile.getSummary());
        return new PersonAggregate()
                .setName(resolvedName)
                .setDescription(appendSuffix(StringUtils.hasText(shortDescription) ? shortDescription : longSummary, KIMI_SUFFIX))
                .setSummary(appendSuffix(longSummary, KIMI_SUFFIX))
                .setWikipedia(cleanText(profile.getWikipedia()))
                .setOfficialWebsite(cleanText(profile.getOfficialWebsite()))
                .setTags(profile.getTags() == null ? List.of() : profile.getTags())
                .setBasicInfo(profile.getBasicInfo())
                .setEvidenceUrls(profile.getEvidenceUrls());
    }

    private List<PageSummary> collectPageSummaries(String fallbackName, List<PageContent> pages) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }

        List<PageSummary> pageSummaries = new ArrayList<>();
        for (PageContent page : pages) {
            if (page == null || !StringUtils.hasText(page.getContent())) {
                continue;
            }
            try {
                PageSummary pageSummary = summaryGenerationClient.summarizePage(fallbackName, page);
                if (pageSummary == null || !StringUtils.hasText(pageSummary.getSummary())) {
                    continue;
                }
                if (!StringUtils.hasText(pageSummary.getSourceUrl())) {
                    pageSummary.setSourceUrl(page.getUrl());
                }
                if (!StringUtils.hasText(pageSummary.getTitle())) {
                    pageSummary.setTitle(page.getTitle());
                }
                pageSummaries.add(pageSummary);
            } catch (RuntimeException ex) {
                log.warn("篇级总结失败 fallbackName={} url={} category={} error={}",
                        fallbackName, page.getUrl(), classifySummaryFailure(ex), ex.getMessage(), ex);
            }
        }
        return pageSummaries;
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

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceAll("\\s+", " ").trim();
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

    private <T> T joinTask(String label, CompletableFuture<T> future, T fallback, List<String> errors) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.error("{}任务失败 error={}", label, cause.getMessage(), cause);
            errors.add(label + "获取失败: " + cause.getMessage());
            return fallback;
        }
    }
}
