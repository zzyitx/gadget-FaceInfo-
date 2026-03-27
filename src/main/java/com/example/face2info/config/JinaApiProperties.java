package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Jina 页面读取接口配置。
 */
@Getter
@Setter
public class JinaApiProperties {

    private String baseUrl = "https://r.jina.ai/";
    private String apiKey;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;
    private int maxRetries = 2;
    private long backoffInitialMs = 300L;
}
