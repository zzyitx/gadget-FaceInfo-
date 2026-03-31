package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.internal.WebEvidence;
import com.example.face2info.entity.response.ImageMatch;
import com.example.face2info.service.FaceRecognitionService;
import com.example.face2info.util.NameExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class FaceRecognitionServiceImpl implements FaceRecognitionService {

    private static final int MAX_IMAGE_MATCHES = 20;
    private static final int MAX_SEED_QUERIES = 3;

    private final GoogleSearchClient googleSearchClient;
    private final SerpApiClient serpApiClient;
    private final NameExtractor nameExtractor;
    private final TmpfilesClient tmpfilesClient;

    public FaceRecognitionServiceImpl(GoogleSearchClient googleSearchClient,
                                      SerpApiClient serpApiClient,
                                      NameExtractor nameExtractor,
                                      TmpfilesClient tmpfilesClient) {
        this.googleSearchClient = googleSearchClient;
        this.serpApiClient = serpApiClient;
        this.nameExtractor = nameExtractor;
        this.tmpfilesClient = tmpfilesClient;
    }

    @Override
    public RecognitionEvidence recognize(MultipartFile image) {
        log.info("人脸识别模块开始 fileName={} size={} contentType={}",
                image.getOriginalFilename(), image.getSize(), image.getContentType());
        String imageUrl = tmpfilesClient.uploadImage(image);
        log.info("人脸识别模块已获得临时图片地址 imageUrl={}", imageUrl);
        RecognitionEvidence evidence = searchByImageUrl(imageUrl);
        log.info("人脸识别模块完成 imageMatchCount={} seedQueryCount={} webEvidenceCount={} errorCount={}",
                evidence.getImageMatches().size(),
                evidence.getSeedQueries().size(),
                evidence.getWebEvidences().size(),
                evidence.getErrors().size());
        return evidence;
    }

    private RecognitionEvidence searchByImageUrl(String imageUrl) {
        RecognitionEvidence evidence = new RecognitionEvidence();
        List<WebEvidence> webEvidences = new ArrayList<>();

        SerpApiResponse lensResponse = safeSearch("google_lens", imageUrl, evidence,
                () -> googleSearchClient.reverseImageSearchByUrl(imageUrl));
        if (lensResponse != null && lensResponse.getRoot() != null) {
            evidence.setImageMatches(extractImageMatches(lensResponse.getRoot()));
            webEvidences.addAll(extractWebEvidence(lensResponse.getRoot(), "google_lens"));
            log.info("识别来源完成 source=google_lens imageMatchCount={} webEvidenceCount={}",
                    evidence.getImageMatches().size(), webEvidences.size());
        }

        SerpApiResponse yandexAboutResponse = safeSearch("yandex_images_about", imageUrl, evidence,
                () -> serpApiClient.reverseImageSearchByUrlYandex(imageUrl, "about"));
        if (yandexAboutResponse != null && yandexAboutResponse.getRoot() != null) {
            webEvidences.addAll(extractWebEvidence(yandexAboutResponse.getRoot(), "yandex_images_about"));
            log.info("识别来源完成 source=yandex_images_about cumulativeWebEvidenceCount={}", webEvidences.size());
        }

        SerpApiResponse yandexSimilarResponse = safeSearch("yandex_images_similar", imageUrl, evidence,
                () -> serpApiClient.reverseImageSearchByUrlYandex(imageUrl, "similar"));
        if (yandexSimilarResponse != null && yandexSimilarResponse.getRoot() != null) {
            webEvidences.addAll(extractWebEvidence(yandexSimilarResponse.getRoot(), "yandex_images_similar"));
            log.info("识别来源完成 source=yandex_images_similar cumulativeWebEvidenceCount={}", webEvidences.size());
        }

        List<String> seedQueries = extractSeedQueries(lensResponse, yandexAboutResponse, yandexSimilarResponse);
        evidence.setSeedQueries(seedQueries);
        log.info("候选名称提取完成 seedQueries={}", seedQueries);

        SerpApiResponse bingResponse = safeSearch("bing_images", imageUrl, evidence,
                () -> serpApiClient.reverseImageSearchByUrlBing(imageUrl));
        if (bingResponse != null && bingResponse.getRoot() != null) {
            webEvidences.addAll(extractWebEvidence(bingResponse.getRoot(), "bing_images"));
            log.info("识别来源完成 source=bing_images cumulativeWebEvidenceCount={}", webEvidences.size());
        }

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

    private List<String> extractSeedQueries(SerpApiResponse... responses) {
        Set<String> queries = new LinkedHashSet<>();
        for (SerpApiResponse response : responses) {
            if (response == null || response.getRoot() == null) {
                continue;
            }
            collectSeedQuery(queries, firstNonBlank(response.getRoot(), "knowledgeGraph.title", "knowledge_graph.title"));
            collectSeedQueriesFromArray(queries, response.getRoot().path("organic"));
            collectSeedQueriesFromArray(queries, response.getRoot().path("image_results"));
            if (queries.size() >= MAX_SEED_QUERIES) {
                break;
            }
        }
        return new ArrayList<>(queries).subList(0, Math.min(queries.size(), MAX_SEED_QUERIES));
    }

    private void collectSeedQueriesFromArray(Set<String> queries, JsonNode nodes) {
        if (!nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            collectSeedQuery(queries, node.path("title").asText(null));
            if (queries.size() >= MAX_SEED_QUERIES) {
                return;
            }
        }
    }

    private void collectSeedQuery(Set<String> queries, String rawTitle) {
        String query = nameExtractor.cleanCandidateName(rawTitle);
        if (StringUtils.hasText(query)) {
            queries.add(query);
        }
    }

    private List<WebEvidence> extractWebEvidence(JsonNode root, String sourceEngine) {
        List<WebEvidence> evidences = new ArrayList<>();
        if ("google_lens".equals(sourceEngine)) {
            collectWebEvidence(evidences, root.path("organic"), sourceEngine);
            return evidences;
        }
        collectWebEvidence(evidences, root.path("image_results"), sourceEngine);
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

    private List<ImageMatch> extractImageMatches(JsonNode root) {
        JsonNode visualMatches = root.path("organic");
        List<ImageMatch> matches = new ArrayList<>();
        if (!visualMatches.isArray()) {
            return matches;
        }

        for (JsonNode node : visualMatches) {
            if (matches.size() >= MAX_IMAGE_MATCHES) {
                break;
            }

            String title = node.path("title").asText(null);
            String link = node.path("link").asText(null);
            String source = node.path("source").asText(null);
            if (!StringUtils.hasText(title) && !StringUtils.hasText(link)) {
                continue;
            }

            matches.add(new ImageMatch()
                    .setPosition(node.path("position").asInt(matches.size() + 1))
                    .setTitle(title)
                    .setLink(link)
                    .setSource(source));
        }

        return matches;
    }

    @FunctionalInterface
    private interface SearchSupplier {
        SerpApiResponse get();
    }
}
