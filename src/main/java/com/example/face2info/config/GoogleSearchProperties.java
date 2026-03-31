package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Google Serper 调用参数配置。
 */
@Getter
@Setter
public class GoogleSearchProperties {

    private String searchUrl;
    private String lensUrl;
    private String apiKey;
    private String hl = "zh-cn";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private int maxRetries = 3;
    private long backoffInitialMs = 300L;
}
