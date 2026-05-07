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
    /**
     * 单页正文送入大模型前的最大字符数，避免超长网页直接打满上下文。
     */
    private int pageContentMaxLength = 12000;
    /**
     * 最终人物画像分层汇总时每组包含的篇级摘要数量，允许范围 5-10。
     */
    private int profileSummaryBatchSize = 8;
}
