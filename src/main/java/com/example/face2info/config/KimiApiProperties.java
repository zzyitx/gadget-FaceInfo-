package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Kimi 大模型接口配置。
 */
@Getter
@Setter
public class KimiApiProperties {

    private String baseUrl = "https://api.moonshot.cn/v1/chat/completions";
    private String apiKey;
    private String model = "moonshot-v1-8k";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;
    private int maxRetries = 2;
    private long backoffInitialMs = 300L;
    private String systemPrompt = "你是一个人物信息抽取助手，只能输出JSON。";
}
