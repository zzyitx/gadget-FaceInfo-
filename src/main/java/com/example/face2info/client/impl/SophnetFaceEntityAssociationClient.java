package com.example.face2info.client.impl;

import com.example.face2info.client.FaceEntityAssociationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.SophnetVisionApiProperties;
import com.example.face2info.entity.internal.FaceEntityAssociation;
import com.example.face2info.entity.internal.NamedEntity;
import com.example.face2info.entity.internal.PageSummary;
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
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "face2info.api.sophnet-vision", name = "enabled", havingValue = "true")
public class SophnetFaceEntityAssociationClient implements FaceEntityAssociationClient {

    private final RestTemplate restTemplate;
    private final ApiProperties properties;
    private final ObjectMapper objectMapper;

    public SophnetFaceEntityAssociationClient(RestTemplate restTemplate,
                                              ApiProperties properties,
                                              ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<FaceEntityAssociation> associate(String targetImageUrl, PageSummary pageSummary) {
        SophnetVisionApiProperties config = properties.getApi().getSophnetVision();
        if (!config.isEnabled()
                || !StringUtils.hasText(targetImageUrl)
                || pageSummary == null
                || pageSummary.getNamedEntities() == null
                || pageSummary.getNamedEntities().isEmpty()) {
            return List.of();
        }
        validateConfig(config);
        List<FaceEntityAssociation> associations = new ArrayList<>();
        for (String model : config.getModels()) {
            if (!StringUtils.hasText(model)) {
                continue;
            }
            try {
                associations.addAll(callModel(config, model.trim(), targetImageUrl, pageSummary));
            } catch (RuntimeException ex) {
                log.warn("Sophnet 人脸实体关联失败 model={} url={} error={}",
                        model, pageSummary.getSourceUrl(), ex.getMessage(), ex);
            }
        }
        return associations;
    }

    private List<FaceEntityAssociation> callModel(SophnetVisionApiProperties config,
                                                  String model,
                                                  String targetImageUrl,
                                                  PageSummary pageSummary) {
        return RetryUtils.execute("Sophnet 人脸实体关联", config.getMaxRetries(), config.getBackoffInitialMs(), () -> {
            JsonNode body = callSophnet(config, buildRequest(model, targetImageUrl, pageSummary));
            return parseAssociations(body, pageSummary.getSourceUrl());
        });
    }

    private JsonNode callSophnet(SophnetVisionApiProperties config, Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());
        ResponseEntity<String> response = restTemplate.exchange(
                config.getBaseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                String.class);
        String body = response.getBody();
        if (!StringUtils.hasText(body)) {
            throw new ApiCallException("Sophnet 人脸实体关联失败：EMPTY_RESPONSE");
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("Sophnet 人脸实体关联失败：INVALID_RESPONSE " + LogSanitizer.safeText(body), ex);
        }
    }

    private Map<String, Object> buildRequest(String model, String targetImageUrl, PageSummary pageSummary) {
        return Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You align one target face image with public article entities. Return strict JSON only."),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", buildPrompt(pageSummary)),
                                Map.of("type", "image_url", "image_url", Map.of("url", targetImageUrl))
                        ))
                )
        );
    }

    private String buildPrompt(PageSummary pageSummary) {
        return """
                Score whether each article entity describes the target person in the image.
                Use PERSON, ORG and OCCUPATION as important evidence. Occupation is a core identity signal.
                Return strict JSON:
                {"associations":[{"entityText":"...","entityType":"PERSON|ORG|OCCUPATION","confidenceScore":0,"reason":"short"}]}
                Article title: %s
                Article summary: %s
                Entities: %s
                Relations: %s
                """.formatted(
                pageSummary.getTitle(),
                pageSummary.getSummary(),
                summarizeEntities(pageSummary.getNamedEntities()),
                pageSummary.getEntityRelations());
    }

    private String summarizeEntities(List<NamedEntity> entities) {
        if (entities == null) {
            return "[]";
        }
        return entities.stream()
                .map(entity -> "%s:%s mentions=%d context=%s".formatted(
                        entity.getType(), entity.getText(), entity.getMentions(), entity.getContexts()))
                .toList()
                .toString();
    }

    private List<FaceEntityAssociation> parseAssociations(JsonNode body, String sourceUrl) {
        String content = body.path("choices").path(0).path("message").path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        String normalized = stripCodeFence(content);
        try {
            JsonNode payload = objectMapper.readTree(normalized);
            JsonNode array = payload.path("associations");
            if (!array.isArray()) {
                return List.of();
            }
            List<FaceEntityAssociation> result = new ArrayList<>();
            for (JsonNode item : array) {
                result.add(new FaceEntityAssociation()
                        .setEntityText(trimToNull(item.path("entityText").asText(null)))
                        .setEntityType(trimToNull(item.path("entityType").asText(null)))
                        .setConfidenceScore(item.path("confidenceScore").asDouble(0.0D))
                        .setReason(trimToNull(item.path("reason").asText(null)))
                        .setSourceUrl(sourceUrl));
            }
            return result.stream()
                    .filter(association -> StringUtils.hasText(association.getEntityText()))
                    .toList();
        } catch (JsonProcessingException ex) {
            throw new ApiCallException("Sophnet 人脸实体关联失败：INVALID_JSON_CONTENT " + LogSanitizer.safeText(normalized), ex);
        }
    }

    private String stripCodeFence(String content) {
        String normalized = trimToNull(content);
        if (normalized == null) {
            return "{}";
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

    private void validateConfig(SophnetVisionApiProperties config) {
        if (!StringUtils.hasText(config.getBaseUrl())) {
            throw new ApiCallException("Sophnet 人脸实体关联失败：baseUrl 未配置");
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            throw new ApiCallException("Sophnet 人脸实体关联失败：apiKey 未配置");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
