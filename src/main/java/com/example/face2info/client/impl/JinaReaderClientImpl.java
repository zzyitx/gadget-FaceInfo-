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
        String requestUrl = buildRequestUrl(api.getJina().getBaseUrl(), url);
        return RetryUtils.execute("Jina read page", api.getJina().getMaxRetries(), api.getJina().getBackoffInitialMs(), () -> {
            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.hasText(api.getJina().getApiKey())) {
                headers.setBearerAuth(api.getJina().getApiKey());
            }
            ResponseEntity<String> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            String body = response.getBody();
            if (!StringUtils.hasText(body)) {
                throw new ApiCallException("Jina read page returned empty body");
            }
            return new PageContent()
                    .setUrl(url)
                    .setTitle(url)
                    .setContent(body)
                    .setSourceEngine("jina");
        });
    }

    private String buildRequestUrl(String baseUrl, String originalUrl) {
        String normalizedUrl = originalUrl
                .replaceFirst("^https://", "")
                .replaceFirst("^http://", "");
        if (!StringUtils.hasText(baseUrl)) {
            return originalUrl;
        }
        return baseUrl + normalizedUrl;
    }
}
