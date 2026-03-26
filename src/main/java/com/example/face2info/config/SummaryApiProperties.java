package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

/**
 * 摘要生成客户端配置。
 */
@Getter
@Setter
public class SummaryApiProperties {

    private boolean enabled;
    private String provider = "noop";
    private String baseUrl;
    private String apiKey;
    private String model;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;
}
