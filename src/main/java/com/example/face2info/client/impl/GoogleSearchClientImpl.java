package com.example.face2info.client.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.GoogleSearchProperties;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.RetryUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class GoogleSearchClientImpl implements GoogleSearchClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApiProperties properties;

    public GoogleSearchClientImpl(RestTemplate restTemplate, ObjectMapper objectMapper, ApiProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public SerpApiResponse reverseImageSearchByUrl(String imageUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", imageUrl);
        payload.put("hl", google().getHl());
        log.info("Serper Google Lens 识图开始 imageUrl={}", imageUrl);
        return execute("Serper Google Lens 识图", google().getLensUrl(), payload);
    }

    @Override
    public SerpApiResponse googleSearch(String query) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("q", normalizeQuery(query));
        payload.put("hl", google().getHl());
        log.info("Serper Google 搜索开始 query={}", query);
        return execute("Serper Google 搜索", google().getSearchUrl(), payload);
    }

    private SerpApiResponse execute(String name, String url, Map<String, Object> payload) {
        GoogleSearchProperties google = google();
        return RetryUtils.execute(name, google.getMaxRetries(), google.getBackoffInitialMs(), () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-KEY", apiKey());
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return new SerpApiResponse().setRoot(root);
        });
    }

    private GoogleSearchProperties google() {
        return properties.getApi().getGoogle();
    }

    private String apiKey() {
        if (!StringUtils.hasText(google().getApiKey())) {
            throw new ApiCallException("Serper API key not configured.");
        }
        return google().getApiKey();
    }

    private String normalizeQuery(String query) {
        if (!StringUtils.hasText(query) || !query.contains("%")) {
            return query;
        }
        try {
            return UriUtils.decode(query, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            log.debug("跳过错误编码查询的归一化 query={}", query);
            return query;
        }
    }
}
