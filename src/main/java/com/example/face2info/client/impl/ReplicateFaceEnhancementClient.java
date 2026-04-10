package com.example.face2info.client.impl;

import com.example.face2info.client.FaceEnhancementClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.FaceEnhanceProperties;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.InMemoryMultipartFile;
import com.example.face2info.util.RetryUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "face2info.api.face-enhance", name = "provider", havingValue = "replicate")
public class ReplicateFaceEnhancementClient implements FaceEnhancementClient {

    private static final String FAILED_STATUS = "failed";
    private static final String CANCELED_STATUS = "canceled";
    private static final String SUCCEEDED_STATUS = "succeeded";

    private final RestTemplate restTemplate;
    private final ApiProperties properties;

    public ReplicateFaceEnhancementClient(RestTemplate restTemplate, ApiProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public MultipartFile enhanceFaceImageByUrl(String imageUrl, MultipartFile originalImage) {
        FaceEnhanceProperties faceEnhance = properties.getApi().getFaceEnhance();
        if (!faceEnhance.isEnabled()) {
            log.info("人脸增强已禁用，跳过 Replicate 调用");
            return originalImage;
        }
        validateInput(imageUrl, originalImage, faceEnhance.getReplicate());
        log.info("开始调用 Replicate GFP-GAN imageUrl={} model={}/{} version={}",
                imageUrl,
                faceEnhance.getReplicate().getModelOwner(),
                faceEnhance.getReplicate().getModelName(),
                faceEnhance.getReplicate().getModelVersion());

        JsonNode createdPrediction = RetryUtils.execute(
                "Replicate 人脸增强创建任务",
                faceEnhance.getReplicate().getMaxRetries(),
                faceEnhance.getReplicate().getBackoffInitialMs(),
                () -> createPrediction(imageUrl, faceEnhance.getReplicate()));

        JsonNode completedPrediction = waitPredictionIfNeeded(createdPrediction, faceEnhance.getReplicate());
        String outputUrl = extractOutputUrl(completedPrediction);
        return downloadAsMultipart(outputUrl, originalImage);
    }

    private void validateInput(String imageUrl, MultipartFile originalImage, FaceEnhanceProperties.Replicate replicate) {
        if (!StringUtils.hasText(imageUrl)) {
            throw new ApiCallException("Replicate 人脸增强失败：imageUrl 不能为空");
        }
        if (originalImage == null || originalImage.isEmpty()) {
            throw new ApiCallException("Replicate 人脸增强失败：originalImage 不能为空");
        }
        if (!StringUtils.hasText(replicate.getApiKey())) {
            throw new ApiCallException("Replicate 人脸增强失败：apiKey 未配置");
        }
        if (!StringUtils.hasText(replicate.getModelVersion())) {
            throw new ApiCallException("Replicate 人脸增强失败：modelVersion 未配置");
        }
    }

    private JsonNode createPrediction(String imageUrl, FaceEnhanceProperties.Replicate replicate) {
        String endpoint = normalizeBaseUrl(replicate.getBaseUrl()) + "/v1/predictions";
        Map<String, Object> input = Map.of(
                "img", imageUrl,
                "version", replicate.getGfpganVersion(),
                "scale", replicate.getScale()
        );
        Map<String, Object> body = Map.of(
                "version", replicate.getModelVersion(),
                "input", input
        );
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                new HttpEntity<>(body, buildPredictionHeaders(replicate)),
                JsonNode.class
        );
        JsonNode result = response.getBody();
        if (result == null) {
            throw new ApiCallException("Replicate 人脸增强失败：创建任务响应为空");
        }
        return result;
    }

    private JsonNode waitPredictionIfNeeded(JsonNode prediction, FaceEnhanceProperties.Replicate replicate) {
        String status = readStatus(prediction);
        if (SUCCEEDED_STATUS.equals(status)) {
            return prediction;
        }
        if (FAILED_STATUS.equals(status) || CANCELED_STATUS.equals(status)) {
            throw new ApiCallException("Replicate 人脸增强失败，状态=" + status + "，错误信息=" + readError(prediction));
        }

        String predictionId = prediction.path("id").asText(null);
        if (!StringUtils.hasText(predictionId)) {
            throw new ApiCallException("Replicate 人脸增强失败：任务未返回 prediction id");
        }

        String endpoint = normalizeBaseUrl(replicate.getBaseUrl()) + "/v1/predictions/" + predictionId;
        Instant deadline = Instant.now().plus(Duration.ofMillis(replicate.getPollTimeoutMs()));
        while (Instant.now().isBefore(deadline)) {
            sleep(replicate.getPollIntervalMs());
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.GET,
                    new HttpEntity<>(buildAuthHeaders(replicate)),
                    JsonNode.class
            );
            JsonNode current = response.getBody();
            if (current == null) {
                continue;
            }
            String currentStatus = readStatus(current);
            if (SUCCEEDED_STATUS.equals(currentStatus)) {
                return current;
            }
            if (FAILED_STATUS.equals(currentStatus) || CANCELED_STATUS.equals(currentStatus)) {
                throw new ApiCallException("Replicate 人脸增强失败，状态=" + currentStatus + "，错误信息=" + readError(current));
            }
        }
        throw new ApiCallException("Replicate 人脸增强超时，pollTimeoutMs=" + replicate.getPollTimeoutMs());
    }

    private MultipartFile downloadAsMultipart(String outputUrl, MultipartFile originalImage) {
        if (!StringUtils.hasText(outputUrl)) {
            throw new ApiCallException("Replicate 人脸增强失败：未返回 output URL");
        }
        ResponseEntity<byte[]> response = restTemplate.getForEntity(URI.create(outputUrl), byte[].class);
        byte[] bytes = response.getBody();
        if (bytes == null || bytes.length == 0) {
            throw new ApiCallException("Replicate 人脸增强失败：output URL 返回空字节");
        }
        String contentType = response.getHeaders().getContentType() == null
                ? originalImage.getContentType()
                : response.getHeaders().getContentType().toString();
        String filename = buildEnhancedFilename(originalImage.getOriginalFilename());
        return new InMemoryMultipartFile(filename, contentType, bytes);
    }

    private String extractOutputUrl(JsonNode prediction) {
        JsonNode output = prediction.path("output");
        if (output.isTextual()) {
            return output.asText();
        }
        if (output.isArray() && !output.isEmpty() && output.get(0).isTextual()) {
            return output.get(0).asText();
        }
        return null;
    }

    private String readStatus(JsonNode prediction) {
        return prediction.path("status").asText("").toLowerCase();
    }

    private String readError(JsonNode prediction) {
        String error = prediction.path("error").asText(null);
        if (StringUtils.hasText(error)) {
            return error;
        }
        JsonNode logs = prediction.path("logs");
        return logs.isTextual() ? logs.asText() : null;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "https://api.replicate.com";
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private HttpHeaders buildPredictionHeaders(FaceEnhanceProperties.Replicate replicate) {
        HttpHeaders headers = buildAuthHeaders(replicate);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.put("Prefer", List.of("wait=" + replicate.getPreferWaitSeconds()));
        return headers;
    }

    private HttpHeaders buildAuthHeaders(FaceEnhanceProperties.Replicate replicate) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(replicate.getApiKey());
        return headers;
    }

    private String buildEnhancedFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "enhanced-face.jpg";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == originalFilename.length() - 1) {
            return originalFilename + "-enhanced";
        }
        return originalFilename.substring(0, dotIndex)
                + "-enhanced."
                + originalFilename.substring(dotIndex + 1);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(Math.max(ms, 100L));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiCallException("Replicate 人脸增强轮询被中断", ex);
        }
    }
}
