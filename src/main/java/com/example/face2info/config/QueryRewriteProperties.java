package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 派生主题查询改写配置。
 */
@Getter
@Setter
public class QueryRewriteProperties {

    private boolean enabled;
    private List<String> providerPriority = new ArrayList<>(List.of("deepseek", "kimi"));
    private int candidateCount = 3;
    private Map<String, String> topicStrategies = new LinkedHashMap<>();
    private Map<String, List<String>> sensitiveTopicPatterns = new LinkedHashMap<>();
    private Map<String, List<String>> fallbackTemplates = new LinkedHashMap<>();
    private boolean logOriginalQuery = true;
    private boolean logFinalQuery = true;
}
