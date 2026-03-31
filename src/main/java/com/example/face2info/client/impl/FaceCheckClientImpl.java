package com.example.face2info.client.impl;

import com.example.face2info.client.FaceCheckClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.FaceCheckMatchCandidate;
import com.example.face2info.entity.internal.FaceCheckSearchResponse;
import com.example.face2info.entity.internal.FaceCheckUploadResponse;
import com.example.face2info.exception.ApiCallException;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public FaceCheckSearchResponse search(MultipartFile image) {
        FaceCheckUploadResponse uploadResponse = upload(image);
        if (!StringUtils.hasText(uploadResponse.getIdSearch())) {
            throw new ApiCallException("facecheck upload failed: missing id_search");
        }
        return pollSearch(uploadResponse.getIdSearch());
    }

    FaceCheckUploadResponse upload(MultipartFile image) {
        ApiProperties.Api api = properties.getApi();
        String endpoint = api.getFacecheck().getBaseUrl() + api.getFacecheck().getUploadPath();

        try {
            LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("images", new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename();
                }
            });

            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(body, createMultipartHeaders()),
                    String.class);
            return mapUploadResponse(objectMapper.readTree(response.getBody()));
        } catch (IOException | RestClientException ex) {
            log.warn("FaceCheck 调用失败 endpoint={} message={}", endpoint, ex.getMessage());
            throw new ApiCallException("facecheck upload failed", ex);
        }
    }

    FaceCheckSearchResponse pollSearch(String idSearch) {
        ApiProperties.Api api = properties.getApi();
        String endpoint = api.getFacecheck().getBaseUrl() + api.getFacecheck().getSearchPath();
        long deadline = System.currentTimeMillis() + api.getFacecheck().getSearchTimeoutMillis();

        while (System.currentTimeMillis() <= deadline) {
            JsonNode body = doSearchRequest(endpoint, idSearch);
            if (hasRemoteError(body)) {
                throw new ApiCallException("facecheck search failed: " + body.path("error").asText(""));
            }
            JsonNode itemsNode = extractItemsNode(body);
            if (itemsNode.isArray()) {
                return new FaceCheckSearchResponse().setItems(mapItems(itemsNode));
            }
            sleep(api.getFacecheck().getPollIntervalMillis());
        }

        return new FaceCheckSearchResponse().setTimedOut(true);
    }

    private HttpHeaders createMultipartHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(properties.getApi().getFacecheck().getApiKey())) {
            headers.set("Authorization", properties.getApi().getFacecheck().getApiKey());
        }
        return headers;
    }

    private HttpHeaders createJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(properties.getApi().getFacecheck().getApiKey())) {
            headers.set("Authorization", properties.getApi().getFacecheck().getApiKey());
        }
        return headers;
    }

    private JsonNode doSearchRequest(String endpoint, String idSearch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id_search", idSearch);
        body.put("demo", properties.getApi().getFacecheck().isDemo());
        body.put("page", 1);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(body, createJsonHeaders()),
                    String.class);
            return objectMapper.readTree(response.getBody());
        } catch (IOException | RestClientException ex) {
            log.warn("FaceCheck 调用失败 endpoint={} message={}", endpoint, ex.getMessage());
            throw new ApiCallException("facecheck search failed", ex);
        }
    }

    private FaceCheckUploadResponse mapUploadResponse(JsonNode body) {
        if (hasRemoteError(body)) {
            throw new ApiCallException("facecheck upload failed: " + body.path("error").asText(""));
        }
        return new FaceCheckUploadResponse()
                .setIdSearch(body.path("id_search").asText(""))
                .setMessage(body.path("message").asText(""));
    }

    private boolean hasRemoteError(JsonNode body) {
        return StringUtils.hasText(body.path("error").asText(""));
    }

    private List<FaceCheckMatchCandidate> mapItems(JsonNode itemNodes) {
        List<FaceCheckMatchCandidate> items = new ArrayList<>();
        for (JsonNode itemNode : itemNodes) {
            FaceCheckMatchCandidate candidate = mapItem(itemNode);
            if (StringUtils.hasText(candidate.getImageDataUrl())
                    || StringUtils.hasText(candidate.getSourceUrl())) {
                items.add(candidate);
            }
        }
        return items;
    }

    private void sleep(int pollIntervalMillis) {
        if (pollIntervalMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(pollIntervalMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiCallException("facecheck search interrupted", ex);
        }
    }

    private FaceCheckMatchCandidate mapItem(JsonNode item) {
        String sourceUrl = readSourceUrl(item);
        return new FaceCheckMatchCandidate()
                .setImageDataUrl(readImageData(item))
                .setSimilarityScore(item.path("score").asDouble(0))
                .setSourceHost(readSourceHost(item, sourceUrl))
                .setSourceUrl(sourceUrl)
                .setGroup(item.path("group").asInt(0))
                .setSeen(item.path("seen").asInt(0))
                .setIndex(item.path("index").asInt(0));
    }

    private JsonNode extractItemsNode(JsonNode body) {
        JsonNode outputNode = body.path("output");
        if (outputNode.isArray()) {
            return outputNode;
        }
        return outputNode.path("items");
    }

    private String readSourceUrl(JsonNode item) {
        if (item.path("url").isObject()) {
            return item.path("url").path("value").asText("");
        }
        String link = item.path("link").asText("");
        if (StringUtils.hasText(link)) {
            return link;
        }
        return item.path("url").asText("");
    }

    private String readImageData(JsonNode item) {
        String base64 = item.path("base64").asText("");
        if (StringUtils.hasText(base64)) {
            return toDataUrl(base64);
        }
        String thumbnail = item.path("thumbnail").asText("");
        if (StringUtils.hasText(thumbnail)) {
            return thumbnail;
        }
        return item.path("img_url").asText("");
    }

    private String readSourceHost(JsonNode item, String sourceUrl) {
        String sourceHost = item.path("source").asText("");
        if (StringUtils.hasText(sourceHost)) {
            return sourceHost;
        }
        return extractHost(sourceUrl);
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
