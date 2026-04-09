package com.example.face2info.client.impl;

import com.example.face2info.client.NewsApiClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.NewsApiResponse;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.LogSanitizer;
import com.example.face2info.util.RetryUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class NewsApiClientImpl implements NewsApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApiProperties properties;

    public NewsApiClientImpl(RestTemplate restTemplate, ObjectMapper objectMapper, ApiProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public NewsApiResponse searchNews(String name) {
        ApiProperties.Api api = properties.getApi();
        String url = UriComponentsBuilder.fromHttpUrl(api.getNews().getBaseUrl())
                .queryParam("q", name)
                .queryParam("language", api.getNews().getLanguage())
                .queryParam("sortBy", api.getNews().getSortBy())
                .queryParam("pageSize", api.getNews().getPageSize())
                .queryParam("apiKey", apiKey())
                .encode()
                .build()
                .toUriString();
        log.info("NewsAPI 新闻检索开始 query={} url={}", name, LogSanitizer.maskUrl(url));
        return RetryUtils.execute("NewsAPI 搜索", api.getNews().getMaxRetries(), api.getNews().getBackoffInitialMs(), () -> {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            int articleCount = root.path("articles").isArray() ? root.path("articles").size() : 0;
            log.info("NewsAPI 新闻检索完成 query={} articleCount={}", name, articleCount);
            return new NewsApiResponse().setRoot(root);
        });
    }

    private String apiKey() {
        if (!StringUtils.hasText(properties.getApi().getNews().getApiKey())) {
            throw new ApiCallException("NewsAPI Key 未配置。");
        }
        return properties.getApi().getNews().getApiKey();
    }
}
