package com.example.face2info.service.impl;

import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.entity.internal.RecognitionCandidate;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.response.ImageMatch;
import com.example.face2info.service.FaceRecognitionService;
import com.example.face2info.util.NameExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 候选人物识别实现。
 * 通过上传图片到临时文件服务，再调用 Google Lens 结果推断人物名称。
 */
@Slf4j
@Service
public class FaceRecognitionServiceImpl implements FaceRecognitionService {
    private static final double CONFIDENCE_THRESHOLD = 0.7;
    private static final int MAX_IMAGE_MATCHES = 20;
    private static final String RECOGNITION_FAILED_MESSAGE = "Unable to recognize the person from the image.";

    private final SerpApiClient serpApiClient;
    private final NameExtractor nameExtractor;
    private final TmpfilesClient tmpfilesClient;

    public FaceRecognitionServiceImpl(SerpApiClient serpApiClient, NameExtractor nameExtractor, TmpfilesClient tmpfilesClient) {
        this.serpApiClient = serpApiClient;
        this.nameExtractor = nameExtractor;
        this.tmpfilesClient = tmpfilesClient;
    }

    /**
     * 执行识别流程并返回候选结果。
     */
    @Override
    public RecognitionCandidate recognize(MultipartFile image) {
        log.info("Starting face recognition by reverse image search (TempFile + URL method)");
        String imageUrl = tmpfilesClient.uploadImage(image);
        return searchByImageUrl(imageUrl);
    }

    /**
     * 使用临时图片地址发起反向搜图。
     */
    private RecognitionCandidate searchByImageUrl(String imageUrl) {
        log.info("Searching with image URL: {}", imageUrl);
        SerpApiResponse response = serpApiClient.reverseImageSearchByUrl(imageUrl);
        return parseResponse(response);
    }

    /**
     * 按可信度依次从知识图谱、视觉匹配、图片结果和自然搜索结果中提取人物名称。
     */
    private RecognitionCandidate parseResponse(SerpApiResponse response) {
        JsonNode root = response.getRoot();
        RecognitionCandidate candidate = new RecognitionCandidate()
                .setImageMatches(extractImageMatches(root));

        log.info("SerpAPI response: {}", root);

        JsonNode kgNode = root.path("knowledge_graph");
        String kgTitle = kgNode.path("title").asText(null);
        if (StringUtils.hasText(kgTitle)) {
            // 知识图谱通常最可靠，优先尝试直接命中人物名称。
            String name = nameExtractor.cleanCandidateName(kgTitle);
            double confidence = nameExtractor.estimateConfidence(kgTitle, name, true);
            if (StringUtils.hasText(name) && confidence >= CONFIDENCE_THRESHOLD) {
                return confidentCandidate(candidate, name, confidence, "knowledge_graph");
            }
        }

        JsonNode visualMatches = root.path("visual_matches");
        if (visualMatches.isArray() && visualMatches.size() > 0) {
            // 如果知识图谱缺失，则退化到视觉匹配首条结果。
            JsonNode firstVisual = visualMatches.get(0);
            String visualTitle = firstVisual.path("title").asText(null);
            if (StringUtils.hasText(visualTitle)) {
                String name = nameExtractor.cleanCandidateName(visualTitle);
                double confidence = nameExtractor.estimateConfidence(visualTitle, name, false);
                if (StringUtils.hasText(name) && confidence >= CONFIDENCE_THRESHOLD) {
                    return confidentCandidate(candidate, name, confidence, "visual_matches");
                }
            }
        }

        JsonNode imageResults = root.path("image_results");
        if (imageResults.isArray() && imageResults.size() > 0) {
            // 图片结果可作为次级候选来源，用于补充 Lens 未给出明确人名的情况。
            JsonNode firstImage = imageResults.get(0);
            String imageTitle = firstImage.path("title").asText(null);
            if (StringUtils.hasText(imageTitle)) {
                String name = nameExtractor.cleanCandidateName(imageTitle);
                double confidence = nameExtractor.estimateConfidence(imageTitle, name, false);
                if (StringUtils.hasText(name) && confidence >= CONFIDENCE_THRESHOLD) {
                    return confidentCandidate(candidate, name, confidence, "image_results");
                }
            }
        }

        JsonNode organicResults = root.path("organic_results");
        if (organicResults.isArray() && organicResults.size() > 0) {
            // 最后回退到自然搜索结果，尽量从标题中恢复人物名称。
            JsonNode firstOrganic = organicResults.get(0);
            String organicTitle = firstOrganic.path("title").asText(null);
            if (StringUtils.hasText(organicTitle)) {
                String name = nameExtractor.cleanCandidateName(organicTitle);
                double confidence = nameExtractor.estimateConfidence(organicTitle, name, false);
                if (StringUtils.hasText(name) && confidence >= CONFIDENCE_THRESHOLD) {
                    return confidentCandidate(candidate, name, confidence, "organic_results");
                }
            }
        }

        return candidate.setError(RECOGNITION_FAILED_MESSAGE);
    }

    /**
     * 构造一个通过阈值校验的候选结果。
     */
    private RecognitionCandidate confidentCandidate(RecognitionCandidate candidate, String name, double confidence, String source) {
        if (!StringUtils.hasText(name) || confidence < CONFIDENCE_THRESHOLD) {
            return candidate.setError(RECOGNITION_FAILED_MESSAGE);
        }
        log.info("Recognized person name={} confidence={} source={}", name, confidence, source);
        return candidate
                .setName(name)
                .setConfidence(confidence)
                .setSource(source);
    }

    /**
     * 提取前 20 条视觉匹配结果，供前端直接展示。
     */
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

            // 保留标题、来源和链接，避免把原始杂乱结构直接暴露给前端。
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
}
