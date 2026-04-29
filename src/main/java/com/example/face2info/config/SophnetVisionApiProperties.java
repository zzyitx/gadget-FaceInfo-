package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Sophnet 视觉大模型人物搜索配置。
 */
@Getter
@Setter
public class SophnetVisionApiProperties {

    private boolean enabled;
    private String baseUrl = "https://www.sophnet.com/api/open-apis/v1/chat/completions";
    private String apiKey;
    private List<String> models = new ArrayList<>(List.of(
            "grok-4-1-fast-non-reasoning",
            "gemini-3.1-pro-preview"
    ));
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 75000;
    private int maxRetries = 2;
    private long backoffInitialMs = 500L;
    private int maxEvidenceUrls = 8;
    private String systemPrompt = "你是人物公开信息检索助手。必须基于用户提供的图片识别人物，并自行搜索公开来源后输出严格 JSON。";
    private String userPrompt = """
            请直接根据图片判断图中最可能的人物，并自行检索公开数据源进行核验与摘要。
            只输出 JSON，不要 Markdown、解释、道歉或推理过程。字段固定为：
            {
              "candidateName": "最可能的人物姓名；无法判断则为空字符串",
              "confidence": 0.0,
              "summary": "基于公开来源的中文摘要；无法确认则说明证据不足",
              "evidenceUrls": ["公开来源 URL"],
              "tags": ["身份或职业标签"],
              "sourceNotes": ["简短说明使用了哪些公开数据源"]
            }
            要求：
            1. 不要把图片中的普通人强行识别为公众人物。
            2. 不要编造来源 URL；来源不足时 evidenceUrls 返回空数组。
            3. 摘要只写可由公开来源支持的信息。
            """;
}
