package com.example.face2info.client.impl;

import com.example.face2info.client.JinaReaderClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.LogSanitizer;
import com.example.face2info.util.RetryUtils;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
public class JinaReaderClientImpl implements JinaReaderClient {

    private static final String DEFAULT_ACCEPT = "application/json";
    private static final String DEFAULT_KEEP_IMG_DATA_URL = "true";
    private static final String DEFAULT_RETURN_FORMAT = "text";

    private final RestTemplate restTemplate;
    private final ApiProperties properties;

    public JinaReaderClientImpl(RestTemplate restTemplate, ApiProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public List<PageContent> readPages(List<String> urls) {
        List<PageContent> pages = new ArrayList<>();
        ApiCallException lastException = null;
        if (urls == null || urls.isEmpty()) {
            log.info("Jina 正文读取跳过，未收到可用链接");
            return pages;
        }

        log.info("Jina 正文读取开始 urlCount={}", urls.size());
        for (String url : urls) {
            if (!StringUtils.hasText(url)) {
                continue;
            }
            try {
                pages.add(readSinglePage(url));
            } catch (ApiCallException ex) {
                lastException = ex;
                log.warn("Jina 页面读取失败，但批处理继续 sourceUrl={} error={}",
                        LogSanitizer.maskUrl(url), ex.getMessage());
            }
        }
        if (pages.isEmpty() && lastException != null) {
            throw lastException;
        }
        log.info("Jina 正文读取完成 successCount={}", pages.size());
        return pages;
    }

    private PageContent readSinglePage(String url) {
        ApiProperties.Api api = properties.getApi();
        String normalizedUrl = normalizeOriginalUrl(url);
        String requestUrl = buildRequestUrl(api.getJina().getBaseUrl(), normalizedUrl);
        return RetryUtils.execute("Jina 页面读取", api.getJina().getMaxRetries(), api.getJina().getBackoffInitialMs(), () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT, DEFAULT_ACCEPT);
            if (StringUtils.hasText(api.getJina().getApiKey())) {
                headers.setBearerAuth(api.getJina().getApiKey());
            }
            headers.set("X-Keep-Img-Data-Url", DEFAULT_KEEP_IMG_DATA_URL);
            headers.set("X-Return-Format", DEFAULT_RETURN_FORMAT);
            log.info("Jina 正在读取页面 sourceUrl={} requestUrl={}",
                    normalizedUrl, LogSanitizer.maskUrl(requestUrl));
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(requestUrl),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            String body = response.getBody();
            if (!StringUtils.hasText(body)) {
                throw new ApiCallException("Jina 页面读取返回空响应体");
            }
            log.info("Jina 页面读取成功 sourceUrl={} contentLength={}", normalizedUrl, body.length());
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
        if (!StringUtils.hasText(originalUrl)) {
            return originalUrl;
        }
        return originalUrl.trim().replaceAll("\\s", "%20");
    }
}
