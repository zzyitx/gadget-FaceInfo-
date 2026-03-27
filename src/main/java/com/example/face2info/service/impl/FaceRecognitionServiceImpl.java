package com.example.face2info.service.impl;

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

/**
 * 多信源图片识别实现。
 */
@Slf4j
@Service
public class FaceRecognitionServiceImpl implements FaceRecognitionService {

    private static final int MAX_IMAGE_MATCHES = 20;
    private static final int MAX_SEED_QUERIES = 3;

    private final SerpApiClient serpApiClient;
    private final NameExtractor nameExtractor;
    private final TmpfilesClient tmpfilesClient;

    public FaceRecognitionServiceImpl(SerpApiClient serpApiClient, NameExtractor nameExtractor, TmpfilesClient tmpfilesClient) {
        this.serpApiClient = serpApiClient;
        this.nameExtractor = nameExtractor;
        this.tmpfilesClient = tmpfilesClient;
    }

    @Override
    public RecognitionEvidence recognize(MultipartFile image) {
        log.info("Starting multi-source face recognition");
        String imageUrl = tmpfilesClient.uploadImage(image);
        return searchByImageUrl(imageUrl);
    }

    private RecognitionEvidence searchByImageUrl(String imageUrl) {
        RecognitionEvidence evidence = new RecognitionEvidence();
        List<WebEvidence> webEvidences = new ArrayList<>();

        SerpApiResponse lensResponse = safeSearch("google_lens", imageUrl, evidence,
                () -> serpApiClient.reverseImageSearchByUrl(imageUrl));
        if (lensResponse != null && lensResponse.getRoot() != null) {
            evidence.setImageMatches(extractImageMatches(lensResponse.getRoot()));
            webEvidences.addAll(extractWebEvidence(lensResponse.getRoot(), "google_lens"));
        }

        SerpApiResponse yandexAboutResponse = safeSearch("yandex_images_about", imageUrl, evidence,
                () -> serpApiClient.reverseImageSearchByUrlYandex(imageUrl, "about"));
        if (yandexAboutResponse != null && yandexAboutResponse.getRoot() != null) {
            webEvidences.addAll(extractWebEvidence(yandexAboutResponse.getRoot(), "yandex_images_about"));
        }

        SerpApiResponse yandexSimilarResponse = safeSearch("yandex_images_similar", imageUrl, evidence,
                () -> serpApiClient.reverseImageSearchByUrlYandex(imageUrl, "similar"));
        if (yandexSimilarResponse != null && yandexSimilarResponse.getRoot() != null) {
            webEvidences.addAll(extractWebEvidence(yandexSimilarResponse.getRoot(), "yandex_images_similar"));
        }

        List<String> seedQueries = extractSeedQueries(lensResponse, yandexAboutResponse, yandexSimilarResponse);
        evidence.setSeedQueries(seedQueries);

        SerpApiResponse bingResponse = safeSearch("bing_images", imageUrl, evidence,
                () -> serpApiClient.reverseImageSearchByUrlBing(imageUrl));
        if (bingResponse != null && bingResponse.getRoot() != null) {
            webEvidences.addAll(extractWebEvidence(bingResponse.getRoot(), "bing_images"));
        }

        evidence.setWebEvidences(deduplicateWebEvidence(webEvidences));
        return evidence;
    }

    private SerpApiResponse safeSearch(String source,
                                       String imageUrl,
                                       RecognitionEvidence evidence,
                                       SearchSupplier supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            log.warn("Recognition source failed source={} imageUrl={}", source, imageUrl, ex);
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
            collectSeedQuery(queries, response.getRoot().path("knowledge_graph").path("title").asText(null));
            collectSeedQueriesFromArray(queries, response.getRoot().path("visual_matches"));
            collectSeedQueriesFromArray(queries, response.getRoot().path("image_results"));
            collectSeedQueriesFromArray(queries, response.getRoot().path("organic_results"));
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
        collectWebEvidence(evidences, root.path("visual_matches"), sourceEngine);
        collectWebEvidence(evidences, root.path("image_results"), sourceEngine);
        collectWebEvidence(evidences, root.path("organic_results"), sourceEngine);
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
            String value = node.path(field).asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
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
        JsonNode visualMatches = root.path("visual_matches");
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
