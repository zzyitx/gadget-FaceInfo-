package com.example.face2info.client.impl;

import com.example.face2info.client.NewsApiClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.NewsApiResponse;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.RetryUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * NewsAPI 客户端实现。
 * 负责拼装新闻查询参数并把返回结果解析成内部统一结构。
 */
@Component
public class NewsApiClientImpl implements NewsApiClient {

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ApiProperties properties;

    public NewsApiClientImpl(RestTemplate restTemplate, ObjectMapper objectMapper, ApiProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 按名称搜索相关新闻。
     */
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
        return RetryUtils.execute("NewsAPI 搜索", api.getNews().getMaxRetries(), api.getNews().getBackoffInitialMs(), () -> {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return new NewsApiResponse().setRoot(root);
        });
    }

    /**
     * 校验 NewsAPI Key 是否存在。
     */
    private String apiKey() {
        if (!StringUtils.hasText(properties.getApi().getNews().getApiKey())) {
            throw new ApiCallException("NewsAPI key 未配置。");
        }
        return properties.getApi().getNews().getApiKey();
    }
}
