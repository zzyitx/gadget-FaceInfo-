package com.example.face2info.util;

import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LogSanitizer {

    private static final String MASK = "***";

    private LogSanitizer() {
    }

    public static String maskUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        try {
            URI uri = URI.create(url);
            Map<String, List<String>> queryParams = new LinkedHashMap<>(UriComponentsBuilder.fromUri(uri).build(true).getQueryParams());
            if (queryParams.isEmpty()) {
                return url;
            }
            UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri).replaceQuery(null);
            for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
                String key = entry.getKey();
                List<String> values = entry.getValue();
                if (values == null || values.isEmpty()) {
                    builder.queryParam(key);
                    continue;
                }
                for (String value : values) {
                    builder.queryParam(key, shouldMask(key) ? MASK : value);
                }
            }
            return builder.build(true).toUriString();
        } catch (IllegalArgumentException ex) {
            return url;
        }
    }

    public static String safeText(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private static boolean shouldMask(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.contains("api_key")
                || normalized.contains("apikey")
                || normalized.contains("token")
                || normalized.contains("authorization")
                || normalized.contains("access_key")
                || normalized.contains("secret");
    }
}
