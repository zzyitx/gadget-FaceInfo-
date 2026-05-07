package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

/**
 * RocketReach 人物资料搜索配置。
 */
@Getter
@Setter
public class RocketReachProperties {

    private boolean enabled;
    private String baseUrl = "https://api.rocketreach.co/api/v2";
    private String personSearchPath = "/person/search";
    private String apiKey;
    private int maxResults = 5;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;
    private int maxRetries = 2;
    private long backoffInitialMs = 500L;
}
