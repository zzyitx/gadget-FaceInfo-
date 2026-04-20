package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.internal.WebEvidence;
import com.example.face2info.entity.response.ImageMatch;
import com.example.face2info.service.FaceRecognitionService;
import com.example.face2info.service.ImageSimilarityService;
import com.example.face2info.service.ImageResultCacheService;
import com.example.face2info.util.NameExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.task.TaskRejectedException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Service
public class FaceRecognitionServiceImpl implements FaceRecognitionService {

    private static final int MAX_IMAGE_MATCHES = 10;
    private static final int MAX_SEED_QUERIES = 3;
    private static final double AGGREGATED_PRIMARY_THRESHOLD = 60.0;
    private static final double MIN_SIMILARITY_SCORE = 65.0;
    private static final double MAX_SIMILARITY_SCORE = 99.0;
    private static final List<String> TEMP_IMAGE_HOST_KEYWORDS = List.of("tmpfiles", "tempfile");
    private static final String YANDEX_CBIR_PATH_KEYWORD = "/get-images-cbir/";

    private final GoogleSearchClient googleSearchClient;
    private final SerpApiClient serpApiClient;
    private final NameExtractor nameExtractor;
    private final TmpfilesClient tmpfilesClient;
    private final ImageSimilarityService imageSimilarityService;
    private final ImageResultCacheService imageResultCacheService;
    private final ThreadPoolTaskExecutor executor;

    @Autowired
    public FaceRecognitionServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      NameExtractor nameExtractor,
                                      TmpfilesClient tmpfilesClient,
                                      ImageSimilarityService imageSimilarityService,
                                      ImageResultCacheService imageResultCacheService,
                                      @Qualifier("face2InfoExecutor") ThreadPoolTaskExecutor executor) {
        this.googleSearchClient = googleSearchClient;
        this.serpApiClient = serpApiClient;
        this.nameExtractor = nameExtractor;
        this.tmpfilesClient = tmpfilesClient;
        this.imageSimilarityService = imageSimilarityService;
        this.imageResultCacheService = imageResultCacheService;
        this.executor = executor;
    }

    @Override
    public RecognitionEvidence recognize(MultipartFile image) {
        RecognitionEvidence cachedEvidence = imageResultCacheService.getRecognitionEvidence(image);
        if (cachedEvidence != null) {
            log.info("人脸识别命中缓存 fileName={} seedQueryCount={} webEvidenceCount={}",
                    image.getOriginalFilename(),
                    cachedEvidence.getSeedQueries() == null ? 0 : cachedEvidence.getSeedQueries().size(),
                    cachedEvidence.getWebEvidences() == null ? 0 : cachedEvidence.getWebEvidences().size());
            return cachedEvidence;
        }
        String imageUrl = tmpfilesClient.uploadImage(image);
        log.info("人脸识别模块已获得临时图片地址 imageUrl={}", imageUrl);
        return recognizeInternal(image, imageUrl, null);
    }

    @Override
    public RecognitionEvidence recognize(MultipartFile image, String uploadedImageUrl) {
        RecognitionEvidence cachedEvidence = imageResultCacheService.getRecognitionEvidence(image);
        if (cachedEvidence != null) {
            log.info("人脸识别命中缓存 fileName={} seedQueryCount={} webEvidenceCount={}",
                    image.getOriginalFilename(),
                    cachedEvidence.getSeedQueries() == null ? 0 : cachedEvidence.getSeedQueries().size(),
                    cachedEvidence.getWebEvidences() == null ? 0 : cachedEvidence.getWebEvidences().size());
            return cachedEvidence;
        }
        // 复用预处理阶段已经上传好的图床地址，避免高清图再次上传并破坏链路一致性。
        return recognizeInternal(image, uploadedImageUrl, uploadedImageUrl);
    }

    private RecognitionEvidence recognizeInternal(MultipartFile image, String imageUrl, String preparedImageUrl) {
        if (preparedImageUrl != null) {
            log.info("人脸识别模块复用已准备图片地址 imageUrl={}", preparedImageUrl);
        }
        log.info("人脸识别模块开始 fileName={} size={} contentType={}",
                image.getOriginalFilename(), image.getSize(), image.getContentType());

        RecognitionEvidence searchEvidence = searchByImageUrl(imageUrl, image);

        log.info("人脸识别模块完成 imageMatchCount={} seedQueryCount={} webEvidenceCount={} errorCount={}",
                searchEvidence.getImageMatches().size(),
                searchEvidence.getSeedQueries().size(),
                searchEvidence.getWebEvidences().size(),
                searchEvidence.getErrors().size());
        imageResultCacheService.cacheRecognitionEvidence(image, searchEvidence);
        return searchEvidence;
    }

    private RecognitionEvidence searchByImageUrl(String imageUrl, MultipartFile originalImage) {
        RecognitionEvidence evidence = new RecognitionEvidence();
        List<WebEvidence> webEvidences = new ArrayList<>();
        // 多个反向搜图源并行请求，降低整链路等待时间。
        CompletableFuture<SearchOutcome> lensFuture = submitSearch("serper_google_lens", imageUrl,
                () -> googleSearchClient.reverseImageSearchByUrl(imageUrl));
        CompletableFuture<SearchOutcome> yandexAboutFuture = submitSearch("yandex_images_about", imageUrl,
                () -> serpApiClient.reverseImageSearchByUrlYandex(imageUrl, "about"));
        CompletableFuture<SearchOutcome> yandexSimilarFuture = submitSearch("yandex_images_similar", imageUrl,
                () -> serpApiClient.reverseImageSearchByUrlYandex(imageUrl, "similar"));
        CompletableFuture<SearchOutcome> bingFuture = submitSearch("bing_images", imageUrl,
                () -> serpApiClient.reverseImageSearchByUrlBing(imageUrl));

        SearchOutcome lensOutcome = joinSearch("serper_google_lens", lensFuture);
        SearchOutcome yandexAboutOutcome = joinSearch("yandex_images_about", yandexAboutFuture);
        SearchOutcome yandexSimilarOutcome = joinSearch("yandex_images_similar", yandexSimilarFuture);
        SearchOutcome bingOutcome = joinSearch("bing_images", bingFuture);

        for (SearchOutcome outcome : List.of(lensOutcome, yandexAboutOutcome, yandexSimilarOutcome, bingOutcome)) {
            if (outcome.error() != null) {
                evidence.getErrors().add(outcome.error());
            }
            webEvidences.addAll(outcome.webEvidences());
        }

        evidence.setImageMatches(extractImageMatches(
                originalImage,
                imageUrl,
                lensOutcome.root(),
                yandexAboutOutcome.root(),
                yandexSimilarOutcome.root(),
                bingOutcome.root()
        ));
        evidence.setWebEvidences(deduplicateWebEvidence(webEvidences));
        evidence.setSeedQueries(extractSeedQueries(lensOutcome.root(), evidence.getWebEvidences(), evidence.getImageMatches()));
        log.info("识别证据去重完成 before={} after={}", webEvidences.size(), evidence.getWebEvidences().size());
        return evidence;
    }

    /**
     * 提交单个识别源查询任务，统一由 face2InfoExecutor 执行。
     */
    private CompletableFuture<SearchOutcome> submitSearch(String source, String imageUrl, SearchSupplier supplier) {
        return CompletableFuture.supplyAsync(() -> performSearch(source, imageUrl, supplier), executor);
    }

    private SearchOutcome joinSearch(String source, CompletableFuture<SearchOutcome> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.warn("识别来源失败 source={} error={}", source, cause.getMessage(), cause);
            return SearchOutcome.failure(source + ": " + cause.getMessage());
        }
    }

    private SearchOutcome performSearch(String source, String imageUrl, SearchSupplier supplier) {
        try {
            log.info("识别来源开始 source={} imageUrl={}", source, imageUrl);
            SerpApiResponse response = supplier.get();
            JsonNode root = response == null ? null : response.getRoot();
            if (root == null) {
                return SearchOutcome.empty();
            }
            List<WebEvidence> evidences = extractWebEvidence(root, source);
            if ("serper_google_lens".equals(source)) {
                log.info("识别来源完成 source={} imageMatchCount={} webEvidenceCount={}",
                        source, countArray(root.path("organic")), evidences.size());
            } else {
                log.info("识别来源完成 source={} webEvidenceCount={}", source, evidences.size());
            }
            return SearchOutcome.success(root, evidences);
        } catch (RuntimeException ex) {
            log.warn("识别来源失败 source={} imageUrl={} error={}", source, imageUrl, ex.getMessage(), ex);
            return SearchOutcome.failure(source + ": " + ex.getMessage());
        }
    }

    /**
     * 从不同来源的 JSON 结构中抽取网页证据，转换为统一内部模型。
     */
    private List<WebEvidence> extractWebEvidence(JsonNode root, String sourceEngine) {
        List<WebEvidence> evidences = new ArrayList<>();
        if ("serper_google_lens".equals(sourceEngine)) {
            collectWebEvidence(evidences, root.path("organic"), sourceEngine);
            return evidences;
        }
        collectWebEvidence(evidences, root.path("image_results"), sourceEngine);
        collectWebEvidence(evidences, root.path("images_results"), sourceEngine);
        collectWebEvidence(evidences, root.path("related_content"), sourceEngine);
        return evidences;
    }

    private void collectWebEvidence(List<WebEvidence> evidences, JsonNode nodes, String sourceEngine) {
        if (!nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            String url = firstNonBlank(node, "link", "url", "page_url");
            String title = node.path("title").asText(null);
            String source = firstNonBlank(node, "source", "displayed_link");
            String snippet = firstNonBlank(node, "snippet", "description");
            if (!StringUtils.hasText(url) && !StringUtils.hasText(title)) {
                continue;
            }
            evidences.add(new WebEvidence()
                    .setUrl(url)
                    .setTitle(title)
                    .setSource(source)
                    .setSourceEngine(sourceEngine)
                    .setSnippet(snippet));
        }
    }

    private String firstNonBlank(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = readPath(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String readPath(JsonNode node, String fieldPath) {
        JsonNode current = node;
        for (String segment : fieldPath.split("\\.")) {
            current = current.path(segment);
        }
        return current.asText(null);
    }

    private List<WebEvidence> deduplicateWebEvidence(List<WebEvidence> evidences) {
        Set<String> seen = new LinkedHashSet<>();
        List<WebEvidence> deduplicated = new ArrayList<>();
        for (WebEvidence evidence : evidences) {
            String key = StringUtils.hasText(evidence.getUrl())
                    ? evidence.getUrl()
                    : evidence.getSourceEngine() + "|" + evidence.getTitle();
            if (seen.add(key)) {
                deduplicated.add(evidence);
            }
        }
        return deduplicated;
    }

    /**
     * 从搜图证据里提取稳定的回退搜索词，避免再次调用大模型去猜人名。
     */
    private List<String> extractSeedQueries(JsonNode lensRoot, List<WebEvidence> webEvidences, List<ImageMatch> imageMatches) {
        Set<String> normalized = new LinkedHashSet<>();
        collectSeedQuery(normalized, firstNonBlank(lensRoot, "knowledgeGraph.title", "knowledge_graph.title"));
        if (imageMatches != null) {
            for (ImageMatch match : imageMatches) {
                if (normalized.size() >= MAX_SEED_QUERIES) {
                    break;
                }
                collectSeedQuery(normalized, match == null ? null : match.getTitle());
            }
        }
        if (webEvidences != null) {
            for (WebEvidence evidence : webEvidences) {
                if (normalized.size() >= MAX_SEED_QUERIES) {
                    break;
                }
                collectSeedQuery(normalized, evidence == null ? null : evidence.getTitle());
            }
        }
        return new ArrayList<>(normalized);
    }

    private void collectSeedQuery(Set<String> normalized, String rawValue) {
        String cleaned = nameExtractor.cleanCandidateName(rawValue);
        if (StringUtils.hasText(cleaned)) {
            normalized.add(cleaned);
        }
    }

    /**
     * 计算并筛选图像匹配结果：
     * 1) 汇总候选图；
     * 2) 并行打分；
     * 3) 按分数排序并截断到固定上限。
     */
    private List<ImageMatch> extractImageMatches(MultipartFile originalImage,
                                                 String uploadedImageUrl,
                                                 JsonNode lensRoot,
                                                 JsonNode yandexAboutRoot,
                                                 JsonNode yandexSimilarRoot,
                                                 JsonNode bingRoot) {
        List<ImageCandidate> candidates = collectImageCandidates(uploadedImageUrl, lensRoot, yandexAboutRoot, yandexSimilarRoot, bingRoot);
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<String> seedHints = extractSeedHints(lensRoot, candidates);
        List<CompletableFuture<ImageMatch>> matchFutures = new ArrayList<>();
        for (int rawIndex = 0; rawIndex < candidates.size(); rawIndex++) {
            final int candidateIndex = rawIndex;
            final ImageCandidate candidate = candidates.get(rawIndex);
            matchFutures.add(submitImageMatch(originalImage, candidate, candidateIndex, seedHints));
        }

        List<ImageMatch> matches = new ArrayList<>();
        for (CompletableFuture<ImageMatch> future : matchFutures) {
            matches.add(future.join());
        }
        matches.sort(Comparator.comparingDouble(ImageMatch::getSimilarityScore).reversed());
        matches = collapseSameFaceMatches(matches);
        List<ImageMatch> top = new ArrayList<>();
        int limit = Math.min(MAX_IMAGE_MATCHES, matches.size());
        for (int i = 0; i < limit; i++) {
            ImageMatch match = matches.get(i);
            match.setPosition(i + 1);
            top.add(match);
        }
        return top;
    }

    /**
     * 在线程池拒绝时回退到同步打分，保证请求可继续完成。
     */
    private CompletableFuture<ImageMatch> submitImageMatch(MultipartFile originalImage,
                                                           ImageCandidate candidate,
                                                           int rawIndex,
                                                           List<String> seedHints) {
        try {
            return CompletableFuture.supplyAsync(
                    () -> buildImageMatch(originalImage, candidate, rawIndex, seedHints),
                    executor
            );
        } catch (TaskRejectedException ex) {
            log.debug("图像匹配线程池繁忙，回退同步打分 thumbnailUrl={} error={}", candidate.imageUrl, ex.getMessage());
            return CompletableFuture.completedFuture(buildImageMatch(originalImage, candidate, rawIndex, seedHints));
        }
    }

    private ImageMatch buildImageMatch(MultipartFile originalImage,
                                       ImageCandidate candidate,
                                       int rawIndex,
                                       List<String> seedHints) {
        double fallbackScore = calculateSimilarityScore(rawIndex, candidate.title, candidate.source, candidate.link, candidate.imageUrl, seedHints);
        double similarityScore = imageSimilarityService.score(originalImage, candidate.imageUrl, fallbackScore);
        return new ImageMatch()
                .setPosition(candidate.position)
                .setTitle(candidate.title)
                .setLink(candidate.link)
                .setSource(candidate.source)
                .setThumbnailUrl(candidate.imageUrl)
                .setSimilarityScore(roundScore(similarityScore));
    }

    private List<ImageMatch> collapseSameFaceMatches(List<ImageMatch> matches) {
        List<ImageMatch> collapsed = new ArrayList<>();
        ImageMatch aggregatedPrimary = null;
        int aggregatedCount = 0;
        for (ImageMatch match : matches) {
            // 搜索结果阶段把 60% 以上的人脸归并为一张主图，并记录被聚合的总数供前端提示。
            if (match.getSimilarityScore() >= AGGREGATED_PRIMARY_THRESHOLD) {
                if (aggregatedPrimary == null) {
                    aggregatedPrimary = match;
                }
                aggregatedCount++;
                continue;
            }
            match.setAggregatedPrimary(false).setAggregatedCount(0);
            collapsed.add(match);
        }
        if (aggregatedPrimary != null) {
            aggregatedPrimary.setAggregatedPrimary(true).setAggregatedCount(aggregatedCount);
            collapsed.add(0, aggregatedPrimary);
        }
        return collapsed;
    }

    /**
     * 聚合各搜图源候选图并在入口阶段过滤无效项，避免后续打分噪声。
     */
    private List<ImageCandidate> collectImageCandidates(String uploadedImageUrl,
                                                        JsonNode lensRoot,
                                                        JsonNode yandexAboutRoot,
                                                        JsonNode yandexSimilarRoot,
                                                        JsonNode bingRoot) {
        List<ImageCandidate> collected = new ArrayList<>();
        collectCandidates(collected, lensRoot == null ? null : lensRoot.path("organic"), "serper_google_lens", uploadedImageUrl,
                "thumbnailUrl", "thumbnail", "imageUrl", "original");
        collectYandexAboutPreview(collected, yandexAboutRoot, "yandex_images_about", uploadedImageUrl);
        collectCandidates(collected, yandexAboutRoot == null ? null : yandexAboutRoot.path("image_results"), "yandex_images_about", uploadedImageUrl,
                "thumbnail", "original", "cdn_original", "image.link");
        collectCandidates(collected, yandexAboutRoot == null ? null : yandexAboutRoot.path("images_results"), "yandex_images_about", uploadedImageUrl,
                "thumbnail", "original", "cdn_original", "image.link");
        collectYandexAboutPreview(collected, yandexSimilarRoot, "yandex_images_similar", uploadedImageUrl);
        collectCandidates(collected, yandexSimilarRoot == null ? null : yandexSimilarRoot.path("image_results"), "yandex_images_similar", uploadedImageUrl,
                "thumbnail", "original", "cdn_original", "image.link");
        collectCandidates(collected, yandexSimilarRoot == null ? null : yandexSimilarRoot.path("images_results"), "yandex_images_similar", uploadedImageUrl,
                "thumbnail", "original", "cdn_original", "image.link");
        collectCandidates(collected, bingRoot == null ? null : bingRoot.path("related_content"), "bing_images", uploadedImageUrl,
                "thumbnail", "original", "cdn_original");
        collectCandidates(collected, bingRoot == null ? null : bingRoot.path("image_results"), "bing_images", uploadedImageUrl,
                "thumbnail", "thumbnailUrl", "original", "cdn_original");
        return deduplicateCandidates(collected);
    }

    private void collectYandexAboutPreview(List<ImageCandidate> collected, JsonNode root, String sourceEngine, String uploadedImageUrl) {
        if (root == null) {
            return;
        }
        JsonNode previewImage = root.path("image_preview").path("image");
        if (!previewImage.isObject()) {
            return;
        }
        String imageUrl = firstNonBlank(previewImage, "link", "original");
        if (!StringUtils.hasText(imageUrl)) {
            return;
        }
        String sourceUrl = firstNonBlank(previewImage, "link", "serpapi_link");
        if (shouldSkipTemporaryCandidate(imageUrl, sourceUrl, uploadedImageUrl)) {
            return;
        }
        collected.add(new ImageCandidate(
                sourceEngine,
                firstNonBlank(root, "search_information.query_displayed", "search_parameters.url"),
                sourceUrl,
                "Yandex",
                imageUrl,
                1
        ));
    }

    private void collectCandidates(List<ImageCandidate> collected,
                                   JsonNode nodes,
                                   String sourceEngine,
                                   String uploadedImageUrl,
                                   String... imageFieldCandidates) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        int index = 0;
        for (JsonNode node : nodes) {
            String imageUrl = firstNonBlank(node, imageFieldCandidates);
            String title = firstNonBlank(node, "title", "snippet");
            String link = firstNonBlank(node, "link", "source", "serpapi_link");
            String source = firstNonBlank(node, "source", "domain", "displayed_link");
            if (!StringUtils.hasText(imageUrl)) {
                index++;
                continue;
            }
            if (!StringUtils.hasText(title) && !StringUtils.hasText(link)) {
                index++;
                continue;
            }
            if (shouldSkipTemporaryCandidate(imageUrl, link, uploadedImageUrl)) {
                index++;
                continue;
            }
            collected.add(new ImageCandidate(sourceEngine, title, link, source, imageUrl, node.path("position").asInt(index + 1)));
            index++;
        }
    }

    private List<ImageCandidate> deduplicateCandidates(List<ImageCandidate> candidates) {
        Map<String, ImageCandidate> deduplicated = new LinkedHashMap<>();
        for (ImageCandidate candidate : candidates) {
            String key = StringUtils.hasText(candidate.imageUrl)
                    ? candidate.imageUrl
                    : (candidate.sourceEngine + "|" + candidate.link + "|" + candidate.title);
            deduplicated.putIfAbsent(key, candidate);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private List<String> extractSeedHints(JsonNode root, List<ImageCandidate> candidates) {
        Set<String> hints = new LinkedHashSet<>();
        if (root != null) {
            collectSeedHint(hints, firstNonBlank(root, "knowledgeGraph.title", "knowledge_graph.title"));
        }
        for (ImageCandidate candidate : candidates) {
            collectSeedHint(hints, candidate.title);
            if (hints.size() >= MAX_SEED_QUERIES) {
                break;
            }
        }
        return new ArrayList<>(hints);
    }

    private void collectSeedHint(Set<String> hints, String rawValue) {
        String cleaned = nameExtractor.cleanCandidateName(rawValue);
        if (StringUtils.hasText(cleaned)) {
            hints.add(cleaned.toLowerCase(Locale.ROOT));
        }
    }

    private double calculateSimilarityScore(int index,
                                            String title,
                                            String source,
                                            String link,
                                            String thumbnailUrl,
                                            List<String> seedHints) {
        double score = 92.0 - (index * 1.45) - (index * index * 0.08);

        String normalizedTitle = normalize(title);
        if (StringUtils.hasText(normalizedTitle) && seedHints.stream().anyMatch(normalizedTitle::contains)) {
            score += 5.5;
        }
        if (StringUtils.hasText(thumbnailUrl)) {
            score += 2.0;
        }
        if (hasTrustedSource(source, link)) {
            score += 2.5;
        }
        if (StringUtils.hasText(extractHost(link))) {
            score += 1.0;
        }

        return roundScore(Math.max(MIN_SIMILARITY_SCORE, Math.min(MAX_SIMILARITY_SCORE, score)));
    }

    private boolean hasTrustedSource(String source, String link) {
        String normalized = normalize(source) + " " + normalize(extractHost(link));
        return normalized.contains("wikipedia")
                || normalized.contains("official")
                || normalized.contains("linkedin")
                || normalized.contains("facebook")
                || normalized.contains("instagram")
                || normalized.contains("tiktok")
                || normalized.contains("medium")
                || normalized.contains("pandaily")
                || normalized.contains("xiaomi");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    /**
     * 过滤临时图床、上传原图自身及 Yandex CBIR 镜像链接，避免误命中和回环结果。
     */
    private boolean shouldSkipTemporaryCandidate(String imageUrl, String link, String uploadedImageUrl) {
        if (isSameUrl(imageUrl, uploadedImageUrl) || isSameUrl(link, uploadedImageUrl)) {
            return true;
        }
        if (isYandexCbirMirror(imageUrl) || isYandexCbirMirror(link)) {
            return true;
        }
        return isTemporaryImageHost(imageUrl) || isTemporaryImageHost(link);
    }

    private boolean isSameUrl(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean isTemporaryImageHost(String url) {
        String host = extractHost(url);
        if (!StringUtils.hasText(host)) {
            return false;
        }
        for (String keyword : TEMP_IMAGE_HOST_KEYWORDS) {
            if (host.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isYandexCbirMirror(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            String path = uri.getPath();
            if (!StringUtils.hasText(host) || !StringUtils.hasText(path)) {
                return false;
            }
            return host.toLowerCase(Locale.ROOT).contains("yandex")
                    && path.toLowerCase(Locale.ROOT).contains(YANDEX_CBIR_PATH_KEYWORD);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String extractHost(String link) {
        if (!StringUtils.hasText(link)) {
            return null;
        }
        try {
            String host = URI.create(link).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private double roundScore(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private int countArray(JsonNode node) {
        return node != null && node.isArray() ? node.size() : 0;
    }

    private static final class ImageCandidate {
        private final String sourceEngine;
        private final String title;
        private final String link;
        private final String source;
        private final String imageUrl;
        private final int position;

        private ImageCandidate(String sourceEngine, String title, String link, String source, String imageUrl, int position) {
            this.sourceEngine = sourceEngine;
            this.title = title;
            this.link = link;
            this.source = source;
            this.imageUrl = imageUrl;
            this.position = position;
        }
    }

    @FunctionalInterface
    private interface SearchSupplier {
        SerpApiResponse get();
    }

    /**
     * 并行搜图任务的标准返回结构：保留原始 root、抽取后证据和可追踪错误信息。
     */
    private record SearchOutcome(JsonNode root, List<WebEvidence> webEvidences, String error) {
        private static SearchOutcome success(JsonNode root, List<WebEvidence> webEvidences) {
            return new SearchOutcome(root, webEvidences == null ? List.of() : webEvidences, null);
        }

        private static SearchOutcome failure(String error) {
            return new SearchOutcome(null, List.of(), error);
        }

        private static SearchOutcome empty() {
            return new SearchOutcome(null, List.of(), null);
        }
    }
}
