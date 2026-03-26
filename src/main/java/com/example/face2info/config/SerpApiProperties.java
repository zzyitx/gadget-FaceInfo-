package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

/**
 * SerpAPI 调用参数配置。
 */
@Getter
@Setter
public class SerpApiProperties {

    private String baseUrl;
    private String apiKey;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private int maxRetries = 3;
    private long backoffInitialMs = 300L;

}
