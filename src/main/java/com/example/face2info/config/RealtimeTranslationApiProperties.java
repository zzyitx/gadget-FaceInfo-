package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

/**
 * 主题查询实时翻译配置。
 */
@Getter
@Setter
public class RealtimeTranslationApiProperties {

    private String baseUrl;
    private String apiKey;
    private String model = "DeepSeek-R1-Distill-Qwen-7B";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
    private int maxRetries = 2;
    private long backoffInitialMs = 500L;
    private String systemPrompt = "你是搜索查询翻译助手。只返回翻译后的单行查询词，不要解释、不要加引号、不要补充说明。";
}
