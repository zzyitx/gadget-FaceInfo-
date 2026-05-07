package com.example.face2info.client.impl;

import com.example.face2info.client.RocketReachClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.RocketReachProperties;
import com.example.face2info.entity.response.SocialAccount;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class RocketReachClientImpl implements RocketReachClient {

    private static final String SOURCE = "rocketreach";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApiProperties properties;

    public RocketReachClientImpl(RestTemplate restTemplate, ObjectMapper objectMapper, ApiProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<SocialAccount> findProfileAccounts(String name) {
        RocketReachProperties rocketreach = rocketreach();
        if (!rocketreach.isEnabled() || !StringUtils.hasText(name)) {
            return List.of();
        }
        return RetryUtils.execute("RocketReach 人物资料搜索", rocketreach.getMaxRetries(), rocketreach.getBackoffInitialMs(), () -> {
            JsonNode root = executeSearch(name.trim(), rocketreach);
            return mapAccounts(root, rocketreach.getMaxResults());
        });
    }

    private JsonNode executeSearch(String name, RocketReachProperties rocketreach) {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint(rocketreach),
                    new HttpEntity<>(payload(name, rocketreach), headers(rocketreach)),
                    String.class
            );
            return objectMapper.readTree(response.getBody());
        } catch (IOException | RestClientException ex) {
            log.warn("RocketReach 搜索失败 name={} error={}", name, ex.getMessage(), ex);
            throw new ApiCallException("RocketReach 搜索失败", ex);
        }
    }

    private Map<String, Object> payload(String name, RocketReachProperties rocketreach) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("name", List.of(name));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("start", 1);
        payload.put("page_size", Math.max(1, rocketreach.getMaxResults()));
        return payload;
    }

    private HttpHeaders headers(RocketReachProperties rocketreach) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Api-Key", apiKey(rocketreach));
        return headers;
    }

    private String apiKey(RocketReachProperties rocketreach) {
        if (!StringUtils.hasText(rocketreach.getApiKey())) {
            throw new ApiCallException("RocketReach API Key 未配置。");
        }
        return rocketreach.getApiKey();
    }

    private String endpoint(RocketReachProperties rocketreach) {
        String baseUrl = trimRight(rocketreach.getBaseUrl(), "/");
        String path = rocketreach.getPersonSearchPath();
        if (!StringUtils.hasText(path)) {
            path = "/person/search";
        }
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private List<SocialAccount> mapAccounts(JsonNode root, int limit) {
        JsonNode people = firstArray(root, "people", "profiles", "results", "data");
        if (people == null) {
            return List.of();
        }
        Map<String, SocialAccount> accounts = new LinkedHashMap<>();
        for (JsonNode person : people) {
            collectPersonAccounts(person, accounts);
            if (accounts.size() >= limit) {
                break;
            }
        }
        return accounts.values().stream().limit(limit).toList();
    }

    private void collectPersonAccounts(JsonNode person, Map<String, SocialAccount> accounts) {
        String displayName = firstText(person, "name", "full_name", "fullName");
        collectUrl(accounts, "linkedin", firstText(person, "linkedin_url", "linkedinUrl", "linkedin"), displayName);
        collectUrl(accounts, "twitter", firstText(person, "twitter_url", "twitterUrl", "twitter"), displayName);
        collectUrl(accounts, "facebook", firstText(person, "facebook_url", "facebookUrl", "facebook"), displayName);
        collectUrl(accounts, "github", firstText(person, "github_url", "githubUrl", "github"), displayName);
        collectUrl(accounts, "website", firstText(person, "website", "homepage", "personal_website"), displayName);
        collectUrl(accounts, "rocketreach", firstText(person, "profile_url", "profileUrl", "url"), displayName);
        collectNestedLinks(person, displayName, accounts);
    }

    private void collectNestedLinks(JsonNode person, String displayName, Map<String, SocialAccount> accounts) {
        for (String field : List.of("links", "social_links", "socialLinks", "social_profiles", "socialProfiles")) {
            JsonNode links = person.path(field);
            if (links.isArray()) {
                for (JsonNode link : links) {
                    String url = link.isTextual() ? link.asText() : firstText(link, "url", "link", "profile_url", "profileUrl");
                    collectUrl(accounts, detectPlatform(url, firstText(link, "name", "type", "platform")), url, displayName);
                }
            } else if (links.isObject()) {
                links.fields().forEachRemaining(entry -> collectUrl(accounts, entry.getKey(), entry.getValue().asText(null), displayName));
            }
        }
    }

    private void collectUrl(Map<String, SocialAccount> accounts, String platformHint, String rawUrl, String displayName) {
        String url = normalizeUrl(rawUrl);
        if (!StringUtils.hasText(url)) {
            return;
        }
        String platform = normalizePlatform(detectPlatform(url, platformHint));
        if (!StringUtils.hasText(platform)) {
            return;
        }
        accounts.putIfAbsent(url, new SocialAccount()
                .setPlatform(platform)
                .setUrl(url)
                .setUsername(extractUsername(url, displayName))
                .setSource(SOURCE)
                .setSuspected(true)
                .setConfidence("profile_search"));
    }

    private JsonNode firstArray(JsonNode root, String... fields) {
        if (root == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = root.path(field);
            if (value.isArray()) {
                return value;
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private String normalizeUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        String url = rawUrl.trim();
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (!url.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return "https://" + url;
        }
        return url;
    }

    private String detectPlatform(String url, String fallback) {
        if (StringUtils.hasText(url)) {
            String normalized = url.toLowerCase(Locale.ROOT);
            if (normalized.contains("linkedin.com")) {
                return "linkedin";
            }
            if (normalized.contains("twitter.com") || normalized.contains("x.com")) {
                return "twitter";
            }
            if (normalized.contains("facebook.com")) {
                return "facebook";
            }
            if (normalized.contains("instagram.com")) {
                return "instagram";
            }
            if (normalized.contains("github.com")) {
                return "github";
            }
            if (normalized.contains("rocketreach.co")) {
                return "rocketreach";
            }
        }
        return fallback;
    }

    private String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return null;
        }
        return platform.trim().replaceAll("\\s+", "_").toLowerCase(Locale.ROOT);
    }

    private String extractUsername(String url, String fallback) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (StringUtils.hasText(path)) {
                List<String> segments = new ArrayList<>();
                for (String segment : path.split("/")) {
                    if (StringUtils.hasText(segment)) {
                        segments.add(segment);
                    }
                }
                String last = lastMeaningfulSegment(segments);
                if (StringUtils.hasText(last)) {
                    return last;
                }
            }
        } catch (URISyntaxException ex) {
            log.debug("RocketReach 资料链接用户名解析失败 url={} error={}", url, ex.getMessage());
        }
        return fallback;
    }

    private String lastMeaningfulSegment(List<String> segments) {
        Set<String> ignored = new LinkedHashSet<>(List.of("in", "pub", "company", "profile"));
        for (int i = segments.size() - 1; i >= 0; i--) {
            String segment = segments.get(i);
            if (!ignored.contains(segment.toLowerCase(Locale.ROOT))) {
                return segment;
            }
        }
        return null;
    }

    private String trimRight(String value, String suffix) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith(suffix)) {
            result = result.substring(0, result.length() - suffix.length());
        }
        return result;
    }

    private RocketReachProperties rocketreach() {
        return properties.getApi().getRocketreach();
    }
}
