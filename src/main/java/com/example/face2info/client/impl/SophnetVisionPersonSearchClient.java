package com.example.face2info.client.impl;

import com.example.face2info.client.VisionPersonSearchClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.SophnetVisionApiProperties;
import com.example.face2info.entity.internal.VisionModelSearchResult;
import com.example.face2info.entity.response.SocialAccount;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.LogSanitizer;
import com.example.face2info.util.RetryUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "face2info.api.sophnet-vision", name = "enabled", havingValue = "true")
public class SophnetVisionPersonSearchClient implements VisionPersonSearchClient {

    private static final String PROVIDER = "sophnet_vision";

    private final RestTemplate restTemplate;
    private final ApiProperties properties;
    private final ObjectMapper objectMapper;

    public SophnetVisionPersonSearchClient(RestTemplate restTemplate,
                                           ApiProperties properties,
                                           ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<VisionModelSearchResult> searchPersonByImageUrl(String imageUrl) {
        SophnetVisionApiProperties config = properties.getApi().getSophnetVision();
        if (!config.isEnabled()) {
            return List.of();
        }
        validateConfig(config, imageUrl);

        List<VisionModelSearchResult> results = new ArrayList<>();
        for (String model : config.getModels()) {
            if (!StringUtils.hasText(model)) {
                continue;
            }
            try {
                VisionModelSearchResult result = callModel(config, model.trim(), imageUrl);
                if (hasUsableResult(result)) {
                    results.add(result);
                }
            } catch (RuntimeException ex) {
                log.warn("Sophnet 视觉模型搜索失败 model={} imageUrl={} error={}",
                        model, imageUrl, ex.getMessage(), ex);
            }
        }
        return results;
    }

    private VisionModelSearchResult callModel(SophnetVisionApiProperties config, String model, String imageUrl) {
        return RetryUtils.execute("Sophnet 视觉人物搜索", config.getMaxRetries(), config.getBackoffInitialMs(), () -> {
            log.info("Sophnet 视觉人物搜索开始 model={} imageUrl={}", model, imageUrl);
            JsonNode body = callSophnet(config, buildRequest(config, model, imageUrl));
            VisionModelSearchResult result = parseResult(model, body, config.getMaxEvidenceUrls());
            log.info("Sophnet 视觉人物搜索完成 model={} candidateName={} urlCount={} summaryLength={}",
                    model,
                    result.getCandidateName(),
                    result.getEvidenceUrls().size(),
                    result.getSummary() == null ? 0 : result.getSummary().length());
            return result;
        });
    }

    private JsonNode callSophnet(SophnetVisionApiProperties config, Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(config.getBaseUrl(), HttpMethod.POST, entity, String.class);
        String body = response.getBody();
        if (!StringUtils.hasText(body)) {
            throw new ApiCallException("Sophnet 视觉人物搜索失败：EMPTY_RESPONSE");
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("Sophnet 视觉人物搜索失败：INVALID_RESPONSE " + LogSanitizer.safeText(body), ex);
        }
    }

    private Map<String, Object> buildRequest(SophnetVisionApiProperties config, String model, String imageUrl) {
        return Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", config.getSystemPrompt()),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", config.getUserPrompt()),
                                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
                        ))
                )
        );
    }

    private VisionModelSearchResult parseResult(String model, JsonNode body, int maxEvidenceUrls) {
        String content = body.path("choices").path(0).path("message").path("content").asText(null);
        JsonNode payload = parsePayload(content);
        return new VisionModelSearchResult()
                .setProvider(PROVIDER)
                .setModel(model)
                .setCandidateName(trimToNull(payload.path("candidateName").asText(null)))
                .setConfidence(payload.path("confidence").isNumber() ? payload.path("confidence").asDouble() : null)
                .setSummary(trimToNull(payload.path("summary").asText(null)))
                .setCompany(trimToNull(payload.path("company").asText(null)))
                .setPosition(trimToNull(payload.path("position").asText(null)))
                .setSocialAccounts(readSocialAccounts(payload.path("socialAccounts")))
                .setVisualGroundTruth(readStringObject(firstExisting(payload, "visualGroundTruth", "visual_ground_truth")))
                .setEvidenceUrls(readStringArray(payload.path("evidenceUrls"), maxEvidenceUrls))
                .setTags(readStringArray(payload.path("tags"), 10))
                .setSourceNotes(readStringArray(payload.path("sourceNotes"), 10));
    }

    private JsonNode parsePayload(String content) {
        String normalized = stripCodeFence(content);
        if (!StringUtils.hasText(normalized)) {
            throw new ApiCallException("Sophnet 视觉人物搜索失败：EMPTY_CONTENT");
        }
        try {
            return objectMapper.readTree(normalized);
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("Sophnet 视觉人物搜索失败：INVALID_JSON_CONTENT " + LogSanitizer.safeText(normalized), ex);
        }
    }

    private String stripCodeFence(String content) {
        String normalized = trimToNull(content);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        int objectStart = normalized.indexOf('{');
        int objectEnd = normalized.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return normalized.substring(objectStart, objectEnd + 1);
        }
        return normalized;
    }

    private List<String> readStringArray(JsonNode node, int limit) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value = trimToNull(item.asText(null));
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
            if (values.size() >= Math.max(0, limit)) {
                break;
            }
        }
        return new ArrayList<>(values);
    }

    private Map<String, String> readStringObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            String key = trimToNull(entry.getKey());
            String value = trimToNull(entry.getValue().asText(null));
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                values.put(key, value);
            }
        });
        return values;
    }

    private JsonNode firstExisting(JsonNode payload, String... fieldNames) {
        if (payload == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode node = payload.get(fieldName);
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    private List<SocialAccount> readSocialAccounts(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<SocialAccount> accounts = new ArrayList<>();
        for (JsonNode item : node) {
            String platform = trimToNull(item.path("platform").asText(null));
            String url = trimToNull(item.path("url").asText(null));
            String username = trimToNull(item.path("username").asText(null));
            if (!StringUtils.hasText(platform) && !StringUtils.hasText(url) && !StringUtils.hasText(username)) {
                continue;
            }
            accounts.add(new SocialAccount()
                    .setPlatform(platform)
                    .setUrl(url)
                    .setUsername(username)
                    .setSource(PROVIDER)
                    .setSuspected(true)
                    .setConfidence(trimToNull(item.path("confidence").asText(null))));
        }
        return accounts;
    }

    private boolean hasUsableResult(VisionModelSearchResult result) {
        return result != null
                && (StringUtils.hasText(result.getCandidateName())
                || StringUtils.hasText(result.getSummary())
                || !result.getEvidenceUrls().isEmpty()
                || !result.getSocialAccounts().isEmpty()
                || !result.getVisualGroundTruth().isEmpty());
    }

    private void validateConfig(SophnetVisionApiProperties config, String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            throw new ApiCallException("Sophnet 视觉人物搜索失败：imageUrl 不能为空");
        }
        if (!StringUtils.hasText(config.getBaseUrl())) {
            throw new ApiCallException("Sophnet 视觉人物搜索失败：baseUrl 未配置");
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            throw new ApiCallException("Sophnet 视觉人物搜索失败：apiKey 未配置");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
