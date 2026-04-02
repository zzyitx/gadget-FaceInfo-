package com.example.face2info.client.impl;

import com.example.face2info.client.SerpApiClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.SerpApiResponse;
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
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
@Component
public class SerpApiClientImpl implements SerpApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApiProperties properties;

    public SerpApiClientImpl(RestTemplate restTemplate, ObjectMapper objectMapper, ApiProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public SerpApiResponse reverseImageSearchByUrlYandex(String imageUrl, String tab) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("engine", "yandex_images");
        params.put("url", imageUrl);
        params.put("tab", tab);
        params.put("api_key", apiKey());
        String url = buildUrl(params);
        log.info("SerpAPI Yandex 识图开始 imageUrl={} tab={}", imageUrl, tab);
        return execute("SerpAPI Yandex 识图", url);
    }

    @Override
    public SerpApiResponse reverseImageSearchByUrlBing(String imageUrl) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("engine", "bing_reverse_image");
        params.put("image_url", imageUrl);
        params.put("mkt", properties.getApi().getSerp().getBingMarket());
        params.put("api_key", apiKey());
        String url = buildUrl(params);
        log.info("SerpAPI Bing 识图开始 imageUrl={} market={}", imageUrl, properties.getApi().getSerp().getBingMarket());
        return execute("SerpAPI Bing 识图", url);
    }

    @Override
    public SerpApiResponse searchBingImages(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("engine", "bing_images");
        params.put("q", normalizeQuery(query));
        params.put("mkt", properties.getApi().getSerp().getBingMarket());
        params.put("api_key", apiKey());
        String url = buildUrl(params);
        log.info("SerpAPI Bing 图片搜索开始 query={}", query);
        return execute("SerpAPI Bing 图片搜索", url);
    }

    private SerpApiResponse execute(String name, String url) {
        ApiProperties.Api api = properties.getApi();
        return RetryUtils.execute(name, api.getSerp().getMaxRetries(), api.getSerp().getBackoffInitialMs(), () -> {
            log.info("{} 请求地址={}", name, LogSanitizer.maskUrl(url));
            ResponseEntity<String> response = restTemplate.getForEntity(URI.create(url), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            int organicCount = countArray(root.path("organic_results"));
            int visualCount = countArray(root.path("visual_matches"));
            int imageCount = countArray(root.path("image_results"));
            log.info("{} 完成 organicCount={} visualCount={} imageCount={}", name, organicCount, visualCount, imageCount);
            return new SerpApiResponse().setRoot(root);
        });
    }

    private String apiKey() {
        if (!StringUtils.hasText(properties.getApi().getSerp().getApiKey())) {
            throw new ApiCallException("SerpAPI key not configured.");
        }
        return properties.getApi().getSerp().getApiKey();
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

    private String buildUrl(Map<String, String> queryParams) {
        StringJoiner joiner = new StringJoiner("&", properties.getApi().getSerp().getBaseUrl() + "?", "");
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            joiner.add(entry.getKey() + "=" + UriUtils.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return joiner.toString();
    }

    private int countArray(JsonNode node) {
        return node != null && node.isArray() ? node.size() : 0;
    }
}
