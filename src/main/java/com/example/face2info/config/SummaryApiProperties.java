package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

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
    private boolean pageRoutingEnabled = true;
    private int longContentThreshold = 4000;
    private List<String> structuredPageKeywords = new ArrayList<>(List.of(
            "简历",
            "履历",
            "资料",
            "档案",
            "人物简介",
            "作品列表",
            "获奖记录"
    ));
}
