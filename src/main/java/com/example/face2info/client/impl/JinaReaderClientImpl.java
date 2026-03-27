package com.example.face2info.client.impl;

import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.RetryUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Jina 页面正文读取客户端实现。
 */
@Component
public class JinaReaderClientImpl implements JinaReaderClient {

    private final RestTemplate restTemplate;
    private final ApiProperties properties;

    public JinaReaderClientImpl(RestTemplate restTemplate, ApiProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public List<PageContent> readPages(List<String> urls) {
        List<PageContent> pages = new ArrayList<>();
        if (urls == null || urls.isEmpty()) {
            return pages;
        }

        for (String url : urls) {
            if (!StringUtils.hasText(url)) {
                continue;
            }
            pages.add(readSinglePage(url));
        }
        return pages;
    }

    private PageContent readSinglePage(String url) {
        ApiProperties.Api api = properties.getApi();
        String normalizedUrl = normalizeOriginalUrl(url);
        String requestUrl = buildRequestUrl(api.getJina().getBaseUrl(), normalizedUrl);
        return RetryUtils.execute("Jina read page", api.getJina().getMaxRetries(), api.getJina().getBackoffInitialMs(), () -> {
            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.hasText(api.getJina().getApiKey())) {
                headers.setBearerAuth(api.getJina().getApiKey());
            }
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(requestUrl),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            String body = response.getBody();
            if (!StringUtils.hasText(body)) {
                throw new ApiCallException("Jina read page returned empty body");
            }
            return new PageContent()
                    .setUrl(normalizedUrl)
                    .setTitle(normalizedUrl)
                    .setContent(body)
                    .setSourceEngine("jina");
        });
    }

    private String buildRequestUrl(String baseUrl, String originalUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return originalUrl;
        }
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        return normalizedBaseUrl + originalUrl;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmedBaseUrl = baseUrl.trim();
        int gatewayIndex = trimmedBaseUrl.indexOf("r.jina.ai/");
        if (gatewayIndex >= 0) {
            return trimmedBaseUrl.substring(0, gatewayIndex + "r.jina.ai/".length());
        }
        return trimmedBaseUrl.endsWith("/") ? trimmedBaseUrl : trimmedBaseUrl + "/";
    }

    private String normalizeOriginalUrl(String originalUrl) {
        return originalUrl.replaceAll("\\s+", "");
    }
}
