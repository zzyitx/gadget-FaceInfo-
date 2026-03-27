package com.example.face2info.client.impl;

import com.example.face2info.client.SerpApiClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.RetryUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * SerpAPI 客户端实现。
 * 统一封装 Google Lens 和 Google Search 的 URL 构造、请求发送与重试逻辑。
 */
@Slf4j
@Component
public class SerpApiClientImpl implements SerpApiClient {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiProperties properties;

    /**
     * 调用 Google Lens 反向搜图接口。
     */
    @Override
    public SerpApiResponse reverseImageSearchByUrl(String imageUrl) {
        String url = buildUrl(Map.of(
                "engine", "google_lens",
                "url", imageUrl,
                "api_key", apiKey()
        ));
        log.info("Google Lens search for: {}", imageUrl);
        log.info("SerpAPI request URL: {}", url);
        return execute("SerpAPI reverse image search (URL)", url);
    }

    @Override
    public SerpApiResponse reverseImageSearchByUrlYandex(String imageUrl, String tab) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("engine", "yandex_images");
        params.put("url", imageUrl);
        params.put("tab", tab);
        params.put("api_key", apiKey());
        String url = buildUrl(params);
        return execute("SerpAPI Yandex reverse image search", url);
    }

    @Override
    public SerpApiResponse reverseImageSearchByUrlBing(String imageUrl) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("engine", "bing_reverse_image");
        params.put("image_url", imageUrl);
        params.put("mkt", properties.getApi().getSerp().getBingMarket());
        params.put("api_key", apiKey());
        String url = buildUrl(params);
        return execute("SerpAPI Bing image search by URL", url);
    }

    @Override
    public SerpApiResponse searchBingImages(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("engine", "bing_images");
        params.put("q", normalizeQuery(query));
        params.put("mkt", properties.getApi().getSerp().getBingMarket());
        params.put("api_key", apiKey());
        String url = buildUrl(params);
        return execute("SerpAPI Bing image search", url);
    }

    /**
     * 调用常规 Google 搜索接口。
     */
    @Override
    public SerpApiResponse googleSearch(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("engine", "google");
        params.put("q", normalizeQuery(query));
        params.put("api_key", apiKey());
        String url = buildUrl(params);
        return execute("SerpAPI Google search", url);
    }

    /**
     * 执行 HTTP 请求并把响应 JSON 解析成统一包装对象。
     */
    private SerpApiResponse execute(String name, String url) {
        ApiProperties.Api api = properties.getApi();
        return RetryUtils.execute(name, api.getSerp().getMaxRetries(), api.getSerp().getBackoffInitialMs(), () -> {
            ResponseEntity<String> response = restTemplate.getForEntity(URI.create(url), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return new SerpApiResponse().setRoot(root);
        });
    }

    /**
     * 校验 SerpAPI Key 是否存在。
     */
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
            log.debug("跳过对错误编码值的查询归一化: {}", query);
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
}
