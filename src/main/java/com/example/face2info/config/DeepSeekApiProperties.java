package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

/**
 * SophNet DeepSeek 摘要与人物画像能力配置。
 */
@Getter
@Setter
public class DeepSeekApiProperties {

    private String baseUrl;
    private String apiKey;
    private String model = "DeepSeek-V3.2-Fast";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 60000;
    private int maxRetries = 2;
    private long backoffInitialMs = 500L;
    private String systemPrompt = "你是一个人物信息抽取助手。所有任务都必须严格返回结构化 JSON 或函数参数，禁止输出解释、道歉、思考过程、Markdown 代码块和任何额外文本。即使信息不足，也必须按要求字段返回空值或保守结果。";
}
