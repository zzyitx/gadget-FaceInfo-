package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

/**
 * NewsAPI 调用参数配置。
 */
@Getter
@Setter
public class NewsApiProperties {

    private String baseUrl;
    private String apiKey;
    private String language = "zh";
    private String sortBy = "relevancy";
    private int pageSize = 10;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private int maxRetries = 3;
    private long backoffInitialMs = 300L;

}
