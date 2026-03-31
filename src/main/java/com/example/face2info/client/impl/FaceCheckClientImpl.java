package com.example.face2info.client.impl;

import com.example.face2info.client.FaceCheckClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.FaceCheckMatchCandidate;
import com.example.face2info.entity.internal.FaceCheckUploadResponse;
import com.example.face2info.exception.ApiCallException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class FaceCheckClientImpl implements FaceCheckClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApiProperties properties;

    public FaceCheckClientImpl(RestTemplate restTemplate, ObjectMapper objectMapper, ApiProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public FaceCheckUploadResponse upload(MultipartFile image) {
        ApiProperties.Api api = properties.getApi();
        String endpoint = api.getFacecheck().getBaseUrl() + api.getFacecheck().getUploadPath();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("images", List.of(Base64.getEncoder().encodeToString(image.getBytes())));
            body.put("id_search", UUID.randomUUID().toString());
            body.put("reset_prev_images", api.getFacecheck().isResetPrevImages());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(api.getFacecheck().getApiKey())) {
                headers.setBearerAuth(api.getFacecheck().getApiKey());
            }

            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(body, headers), String.class);
            return mapResponse(objectMapper.readTree(response.getBody()));
        } catch (IOException | RestClientException ex) {
            log.warn("FaceCheck 调用失败 endpoint={} message={}", endpoint, ex.getMessage());
            throw new ApiCallException("facecheck upload failed", ex);
        }
    }

    FaceCheckUploadResponse mapResponse(JsonNode body) {
        List<FaceCheckMatchCandidate> items = new ArrayList<>();
        JsonNode itemNodes = body.path("output").path("items");
        if (itemNodes.isArray()) {
            for (JsonNode itemNode : itemNodes) {
                FaceCheckMatchCandidate candidate = mapItem(itemNode);
                if (StringUtils.hasText(candidate.getImageDataUrl())) {
                    items.add(candidate);
                }
            }
        }
        return new FaceCheckUploadResponse()
                .setIdSearch(body.path("id_search").asText(""))
                .setItems(items);
    }

    private FaceCheckMatchCandidate mapItem(JsonNode item) {
        String sourceUrl = item.path("url").path("value").asText("");
        return new FaceCheckMatchCandidate()
                .setImageDataUrl(toDataUrl(item.path("base64").asText("")))
                .setSimilarityScore(item.path("score").asDouble(0))
                .setSourceHost(extractHost(sourceUrl))
                .setSourceUrl(sourceUrl)
                .setGroup(item.path("group").asInt(0))
                .setSeen(item.path("seen").asInt(0))
                .setIndex(item.path("index").asInt(0));
    }

    private String toDataUrl(String base64) {
        if (!StringUtils.hasText(base64)) {
            return "";
        }
        return "data:image/jpeg;base64," + base64;
    }

    private String extractHost(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            return "";
        }
        try {
            String host = new URI(sourceUrl).getHost();
            if (!StringUtils.hasText(host)) {
                return "";
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (URISyntaxException ex) {
            return "";
        }
    }
}
