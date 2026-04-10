package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.internal.WebEvidence;
import com.example.face2info.entity.response.ImageMatch;
import com.example.face2info.service.FaceRecognitionService;
import com.example.face2info.service.ImageSimilarityService;
import com.example.face2info.util.NameExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class FaceRecognitionServiceImpl implements FaceRecognitionService {

    private static final int MAX_IMAGE_MATCHES = 10;
    private static final int MAX_SEED_QUERIES = 3;
    private static final double MIN_SIMILARITY_SCORE = 65.0;
    private static final double MAX_SIMILARITY_SCORE = 99.0;

    private final GoogleSearchClient googleSearchClient;
    private final SerpApiClient serpApiClient;
    private final NameExtractor nameExtractor;
    private final TmpfilesClient tmpfilesClient;
    private final SummaryGenerationClient summaryGenerationClient;
    private final ImageSimilarityService imageSimilarityService;

    public FaceRecognitionServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      NameExtractor nameExtractor,
                                      TmpfilesClient tmpfilesClient,
                                      SummaryGenerationClient summaryGenerationClient,
                                      ImageSimilarityService imageSimilarityService) {
        this.googleSearchClient = googleSearchClient;
        this.serpApiClient = serpApiClient;
        this.nameExtractor = nameExtractor;
        this.tmpfilesClient = tmpfilesClient;
        this.summaryGenerationClient = summaryGenerationClient;
        this.imageSimilarityService = imageSimilarityService;
    }

    @Override
    public RecognitionEvidence recognize(MultipartFile image) {
        log.info("人脸识别模块开始 fileName={} size={} contentType={}",
                image.getOriginalFilename(), image.getSize(), image.getContentType());

        String imageUrl = tmpfilesClient.uploadImage(image);
        log.info("人脸识别模块已获得临时图片地址 imageUrl={}", imageUrl);

        RecognitionEvidence searchEvidence = searchByImageUrl(imageUrl, image);
        searchEvidence.setSeedQueries(recognizeCandidateNames(image, searchEvidence));

        log.info("人脸识别模块完成 imageMatchCount={} seedQueryCount={} webEvidenceCount={} errorCount={}",
                searchEvidence.getImageMatches().size(),
                searchEvidence.getSeedQueries().size(),
                searchEvidence.getWebEvidences().size(),
                searchEvidence.getErrors().size());
        return searchEvidence;
    }

    private List<String> recognizeCandidateNames(MultipartFile image, RecognitionEvidence evidence) {
        try {
            List<String> rawCandidates = summaryGenerationClient.recognizeFaceCandidateNames(image);
            Set<String> normalized = new LinkedHashSet<>();
            if (rawCandidates != null) {
                for (String rawCandidate : rawCandidates) {
                    String cleaned = nameExtractor.cleanCandidateName(rawCandidate);
                    if (StringUtils.hasText(cleaned)) {
                        normalized.add(cleaned);
                        if (normalized.size() >= MAX_SEED_QUERIES) {
                            break;
                        }
                    }
                }
            }
            List<String> candidates = new ArrayList<>(normalized);
            log.info("大模型候选名称识别完成 count={} candidates={}", candidates.size(), candidates);
            return candidates;
        } catch (RuntimeException ex) {
            log.warn("大模型候选名称识别失败 fileName={} error={}", image.getOriginalFilename(), ex.getMessage(), ex);
            evidence.getErrors().add("face_name_recognition: " + ex.getMessage());
            return List.of();
        }
    }

    private RecognitionEvidence searchByImageUrl(String imageUrl, MultipartFile originalImage) {
        RecognitionEvidence evidence = new RecognitionEvidence();
        List<WebEvidence> webEvidences = new ArrayList<>();
        JsonNode lensRoot = null;
        JsonNode yandexAboutRoot = null;
        JsonNode yandexSimilarRoot = null;
        JsonNode bingRoot = null;

        SerpApiResponse lensResponse = safeSearch("serper_google_lens", imageUrl, evidence,
                () -> googleSearchClient.reverseImageSearchByUrl(imageUrl));
        if (lensResponse != null && lensResponse.getRoot() != null) {
            lensRoot = lensResponse.getRoot();
            webEvidences.addAll(extractWebEvidence(lensResponse.getRoot(), "serper_google_lens"));
            log.info("识别来源完成 source=serper_google_lens imageMatchCount={} webEvidenceCount={}",
                    countArray(lensResponse.getRoot().path("organic")), webEvidences.size());
        }

        SerpApiResponse yandexAboutResponse = safeSearch("yandex_images_about", imageUrl, evidence,
                () -> serpApiClient.reverseImageSearchByUrlYandex(imageUrl, "about"));
        if (yandexAboutResponse != null && yandexAboutResponse.getRoot() != null) {
            yandexAboutRoot = yandexAboutResponse.getRoot();
            webEvidences.addAll(extractWebEvidence(yandexAboutResponse.getRoot(), "yandex_images_about"));
            log.info("识别来源完成 source=yandex_images_about cumulativeWebEvidenceCount={}", webEvidences.size());
        }

        SerpApiResponse yandexSimilarResponse = safeSearch("yandex_images_similar", imageUrl, evidence,
                () -> serpApiClient.reverseImageSearchByUrlYandex(imageUrl, "similar"));
        if (yandexSimilarResponse != null && yandexSimilarResponse.getRoot() != null) {
            yandexSimilarRoot = yandexSimilarResponse.getRoot();
            webEvidences.addAll(extractWebEvidence(yandexSimilarResponse.getRoot(), "yandex_images_similar"));
            log.info("识别来源完成 source=yandex_images_similar cumulativeWebEvidenceCount={}", webEvidences.size());
        }

        SerpApiResponse bingResponse = safeSearch("bing_images", imageUrl, evidence,
                () -> serpApiClient.reverseImageSearchByUrlBing(imageUrl));
        if (bingResponse != null && bingResponse.getRoot() != null) {
            bingRoot = bingResponse.getRoot();
            webEvidences.addAll(extractWebEvidence(bingResponse.getRoot(), "bing_images"));
            log.info("识别来源完成 source=bing_images cumulativeWebEvidenceCount={}", webEvidences.size());
        }

        evidence.setImageMatches(extractImageMatches(originalImage, lensRoot, yandexAboutRoot, yandexSimilarRoot, bingRoot));
        evidence.setWebEvidences(deduplicateWebEvidence(webEvidences));
        log.info("识别证据去重完成 before={} after={}", webEvidences.size(), evidence.getWebEvidences().size());
        return evidence;
    }

    private SerpApiResponse safeSearch(String source,
                                       String imageUrl,
                                       RecognitionEvidence evidence,
                                       SearchSupplier supplier) {
        try {
            log.info("识别来源开始 source={} imageUrl={}", source, imageUrl);
            return supplier.get();
        } catch (RuntimeException ex) {
            log.warn("识别来源失败 source={} imageUrl={} error={}", source, imageUrl, ex.getMessage(), ex);
            evidence.getErrors().add(source + ": " + ex.getMessage());
            return null;
        }
    }

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

    private List<ImageMatch> extractImageMatches(MultipartFile originalImage,
                                                 JsonNode lensRoot,
                                                 JsonNode yandexAboutRoot,
                                                 JsonNode yandexSimilarRoot,
                                                 JsonNode bingRoot) {
        List<ImageCandidate> candidates = collectImageCandidates(lensRoot, yandexAboutRoot, yandexSimilarRoot, bingRoot);
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<String> seedHints = extractSeedHints(lensRoot, candidates);
        List<ImageMatch> matches = new ArrayList<>();
        int rawIndex = 0;
        for (ImageCandidate candidate : candidates) {
            double fallbackScore = calculateSimilarityScore(rawIndex, candidate.title, candidate.source, candidate.link, candidate.imageUrl, seedHints);
            double similarityScore = imageSimilarityService.score(originalImage, candidate.imageUrl, fallbackScore);
            matches.add(new ImageMatch()
                    .setPosition(candidate.position)
                    .setTitle(candidate.title)
                    .setLink(candidate.link)
                    .setSource(candidate.source)
                    .setThumbnailUrl(candidate.imageUrl)
                    .setSimilarityScore(roundScore(similarityScore)));
            rawIndex++;
        }

        matches.sort(Comparator.comparingDouble(ImageMatch::getSimilarityScore).reversed());
        List<ImageMatch> top = new ArrayList<>();
        int limit = Math.min(MAX_IMAGE_MATCHES, matches.size());
        for (int i = 0; i < limit; i++) {
            ImageMatch match = matches.get(i);
            match.setPosition(i + 1);
            top.add(match);
        }
        return top;
    }

    private List<ImageCandidate> collectImageCandidates(JsonNode lensRoot,
                                                        JsonNode yandexAboutRoot,
                                                        JsonNode yandexSimilarRoot,
                                                        JsonNode bingRoot) {
        List<ImageCandidate> collected = new ArrayList<>();
        collectCandidates(collected, lensRoot == null ? null : lensRoot.path("organic"), "serper_google_lens",
                "thumbnailUrl", "thumbnail", "imageUrl", "original");
        collectYandexAboutPreview(collected, yandexAboutRoot, "yandex_images_about");
        collectCandidates(collected, yandexAboutRoot == null ? null : yandexAboutRoot.path("image_results"), "yandex_images_about",
                "thumbnail", "original", "cdn_original", "image.link");
        collectCandidates(collected, yandexAboutRoot == null ? null : yandexAboutRoot.path("images_results"), "yandex_images_about",
                "thumbnail", "original", "cdn_original", "image.link");
        collectYandexAboutPreview(collected, yandexSimilarRoot, "yandex_images_similar");
        collectCandidates(collected, yandexSimilarRoot == null ? null : yandexSimilarRoot.path("image_results"), "yandex_images_similar",
                "thumbnail", "original", "cdn_original", "image.link");
        collectCandidates(collected, yandexSimilarRoot == null ? null : yandexSimilarRoot.path("images_results"), "yandex_images_similar",
                "thumbnail", "original", "cdn_original", "image.link");
        collectCandidates(collected, bingRoot == null ? null : bingRoot.path("related_content"), "bing_images",
                "thumbnail", "original", "cdn_original");
        collectCandidates(collected, bingRoot == null ? null : bingRoot.path("image_results"), "bing_images",
                "thumbnail", "thumbnailUrl", "original", "cdn_original");
        return deduplicateCandidates(collected);
    }

    private void collectYandexAboutPreview(List<ImageCandidate> collected, JsonNode root, String sourceEngine) {
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
}
