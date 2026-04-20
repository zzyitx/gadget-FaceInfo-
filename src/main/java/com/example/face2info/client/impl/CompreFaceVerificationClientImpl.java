package com.example.face2info.client.impl;

import com.example.face2info.client.CompreFaceVerificationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.exception.FaceDetectionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.OptionalDouble;

@Slf4j
@Component
public class CompreFaceVerificationClientImpl implements CompreFaceVerificationClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApiProperties properties;

    public CompreFaceVerificationClientImpl(RestTemplate restTemplate, ObjectMapper objectMapper, ApiProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public OptionalDouble verify(byte[] sourceImage, byte[] targetImage, String contentType) {
        String endpoint = buildEndpoint();
        try {
            LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("source_image", new NamedByteArrayResource(sourceImage, contentType, "source-image"));
            body.add("target_image", new NamedByteArrayResource(targetImage, contentType, "target-image"));
            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(body, createHeaders()),
                    String.class
            );
            return readSimilarity(response.getBody());
        } catch (ResourceAccessException ex) {
            log.warn("CompreFace verification 服务不可用 endpoint={} message={}", endpoint, ex.getMessage());
            throw new ApiCallException("CompreFace verification 服务不可用：" + endpoint, ex);
        } catch (IOException | RestClientException ex) {
            log.warn("CompreFace verification 调用失败 endpoint={} message={}", endpoint, ex.getMessage());
            throw new FaceDetectionException("CompreFace verification 请求失败。", ex);
        }
    }

    private String buildEndpoint() {
        ApiProperties.Api api = properties.getApi();
        return UriComponentsBuilder
                .fromHttpUrl(api.getCompreface().getBaseUrl() + api.getCompreface().getVerification().getPath())
                .queryParam("limit", api.getCompreface().getVerification().getLimit())
                .queryParam("det_prob_threshold", api.getCompreface().getVerification().getDetProbThreshold())
                .queryParam("prediction_count", api.getCompreface().getVerification().getPredictionCount())
                .queryParam("status", api.getCompreface().getVerification().isStatus())
                .toUriString();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String apiKey = properties.getApi().getCompreface().getVerification().getApiKey();
        if (StringUtils.hasText(apiKey)) {
            headers.set("x-api-key", apiKey);
        }
        return headers;
    }

    private OptionalDouble readSimilarity(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        double maxSimilarity = -1.0D;
        // 多脸场景下取最大相似度，作为当前候选图与原图的最终比对分。
        for (JsonNode resultNode : root.path("result")) {
            for (JsonNode faceMatch : resultNode.path("face_matches")) {
                maxSimilarity = Math.max(maxSimilarity, faceMatch.path("similarity").asDouble(-1.0D));
            }
        }
        return maxSimilarity < 0.0D ? OptionalDouble.empty() : OptionalDouble.of(maxSimilarity);
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;
        private final String contentType;

        private NamedByteArrayResource(byte[] byteArray, String contentType, String filename) {
            super(byteArray == null ? new byte[0] : byteArray);
            this.contentType = StringUtils.hasText(contentType) ? contentType : "image/jpeg";
            this.filename = filename + resolveExtension(this.contentType);
        }

        @Override
        public String getFilename() {
            return filename;
        }

        public String getContentType() {
            return contentType;
        }

        private static String resolveExtension(String contentType) {
            if ("image/png".equalsIgnoreCase(contentType)) {
                return ".png";
            }
            if ("image/webp".equalsIgnoreCase(contentType)) {
                return ".webp";
            }
            return ".jpg";
        }
    }
}
