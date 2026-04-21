package com.example.face2info.client.impl;

import com.example.face2info.client.RealtimeTranslationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.RealtimeTranslationApiProperties;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.LogSanitizer;
import com.example.face2info.util.RetryUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DeepSeekRealtimeTranslationClient implements RealtimeTranslationClient {

    private final RestTemplate restTemplate;
    private final ApiProperties properties;
    private final ObjectMapper objectMapper;

    public DeepSeekRealtimeTranslationClient(RestTemplate restTemplate,
                                             ApiProperties properties,
                                             ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String translateQuery(String query, String targetLanguageCode) {
        RealtimeTranslationApiProperties translation = properties.getApi().getRealtimeTranslation();
        validateConfig(translation);
        return RetryUtils.execute("实时翻译查询", translation.getMaxRetries(), translation.getBackoffInitialMs(), () -> {
            JsonNode body = callTranslationApi(translation, buildTranslationRequest(translation, query, targetLanguageCode));
            return parseTranslatedText(body, query);
        });
    }

    private void validateConfig(RealtimeTranslationApiProperties translation) {
        if (!StringUtils.hasText(translation.getApiKey())
                || !StringUtils.hasText(translation.getBaseUrl())
                || !StringUtils.hasText(translation.getModel())) {
            throw new ApiCallException("CONFIG_MISSING: 实时翻译配置不完整");
        }
    }

    private JsonNode callTranslationApi(RealtimeTranslationApiProperties translation, Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(translation.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        log.info("实时翻译请求已发送 model={} url={}",
                translation.getModel(),
                LogSanitizer.maskUrl(translation.getBaseUrl()));
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                translation.getBaseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                JsonNode.class
        );
        return response.getBody();
    }

    private Map<String, Object> buildTranslationRequest(RealtimeTranslationApiProperties translation,
                                                        String query,
                                                        String targetLanguageCode) {
        return Map.of(
                "model", translation.getModel(),
                "max_tokens", 256,
                "temperature", 0,
                "system", firstNonBlank(translation.getSystemPrompt(),
                        "你是搜索查询翻译助手。只返回翻译后的单行查询词，不要解释、不要加引号、不要补充说明。"),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", buildTranslationPrompt(query, targetLanguageCode)
                ))
        );
    }

    private String buildTranslationPrompt(String query, String targetLanguageCode) {
        return """
                请把下面的人物搜索查询翻译成目标语言，保留人名的常用写法，适合直接用于搜索引擎。
                目标语言代码：%s
                原始查询：%s
                只输出翻译后的单行查询，不要输出解释。
                """.formatted(targetLanguageCode, firstNonBlank(query, ""));
    }

    private String parseTranslatedText(JsonNode body, String sourceQuery) {
        if (body == null) {
            throw new ApiCallException("EMPTY_RESPONSE: 实时翻译响应为空");
        }
        JsonNode contentNode = body.path("content");
        if (!contentNode.isArray()) {
            throw new ApiCallException("TRANSLATION_INVALID: 实时翻译响应缺少 content");
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : contentNode) {
            if (!"text".equals(item.path("type").asText())) {
                continue;
            }
            String text = item.path("text").asText(null);
            if (StringUtils.hasText(text)) {
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(text.trim());
            }
        }
        String translated = normalizeQuery(builder.toString());
        if (!StringUtils.hasText(translated)) {
            throw new ApiCallException("TRANSLATION_INVALID: 实时翻译结果为空");
        }
        if (normalizeQuery(sourceQuery).equalsIgnoreCase(translated)) {
            throw new ApiCallException("TRANSLATION_INVALID: 实时翻译结果未发生变化");
        }
        return translated;
    }

    private String normalizeQuery(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
