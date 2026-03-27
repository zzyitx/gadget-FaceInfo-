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
import org.springframework.web.util.UriComponentsBuilder;

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
        String url = UriComponentsBuilder.fromHttpUrl(properties.getApi().getSerp().getBaseUrl())
                .queryParam("engine", "google_lens")
                .queryParam("url", imageUrl)
                .queryParam("api_key", apiKey())
                .encode()
                .build()
                .toUriString();
        log.info("Google Lens search for: {}", imageUrl);
        log.info("SerpAPI request URL: {}", url);
        return execute("SerpAPI reverse image search (URL)", url);
    }

    @Override
    public SerpApiResponse reverseImageSearchByUrlYandex(String imageUrl, String tab) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getApi().getSerp().getBaseUrl())
                .queryParam("engine", "yandex_images")
                .queryParam("url", imageUrl)
                .queryParam("tab", tab)
                .queryParam("api_key", apiKey())
                .encode()
                .build()
                .toUriString();
        return execute("SerpAPI Yandex reverse image search", url);
    }

    @Override
    public SerpApiResponse reverseImageSearchByUrlBing(String imageUrl) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getApi().getSerp().getBaseUrl())
                .queryParam("engine", "bing_images")
                .queryParam("url", imageUrl)
                .queryParam("mkt", properties.getApi().getSerp().getBingMarket())
                .queryParam("api_key", apiKey())
                .encode()
                .build()
                .toUriString();
        return execute("SerpAPI Bing image search by URL", url);
    }

    @Override
    public SerpApiResponse searchBingImages(String query) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getApi().getSerp().getBaseUrl())
                .queryParam("engine", "bing_images")
                .queryParam("q", query)
                .queryParam("mkt", properties.getApi().getSerp().getBingMarket())
                .queryParam("api_key", apiKey())
                .encode()
                .build()
                .toUriString();
        return execute("SerpAPI Bing image search", url);
    }

    /**
     * 调用常规 Google 搜索接口。
     */
    @Override
    public SerpApiResponse googleSearch(String query) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getApi().getSerp().getBaseUrl())
                .queryParam("engine", "google")
                .queryParam("q", query)
                .queryParam("api_key", apiKey())
                .encode()
                .build()
                .toUriString();
        return execute("SerpAPI Google search", url);
    }

    /**
     * 执行 HTTP 请求并把响应 JSON 解析成统一包装对象。
     */
    private SerpApiResponse execute(String name, String url) {
        ApiProperties.Api api = properties.getApi();
        return RetryUtils.execute(name, api.getSerp().getMaxRetries(), api.getSerp().getBackoffInitialMs(), () -> {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
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
}
