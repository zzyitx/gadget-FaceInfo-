package com.example.face2info.client.impl;

import com.example.face2info.client.SerpApiClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.RetryUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SerpApiClientImpl implements SerpApiClient {

    private static final Logger log = LoggerFactory.getLogger(SerpApiClientImpl.class);

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ApiProperties properties;

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

    private SerpApiResponse execute(String name, String url) {
        ApiProperties.Api api = properties.getApi();
        return RetryUtils.execute(name, api.getSerp().getMaxRetries(), api.getSerp().getBackoffInitialMs(), () -> {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return new SerpApiResponse().setRoot(root);
        });
    }

    private String apiKey() {
        if (!StringUtils.hasText(properties.getApi().getSerp().getApiKey())) {
            throw new ApiCallException("SerpAPI key not configured.");
        }
        return properties.getApi().getSerp().getApiKey();
    }
}
