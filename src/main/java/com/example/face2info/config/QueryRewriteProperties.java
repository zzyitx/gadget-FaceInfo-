package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 派生主题查询改写配置。
 */
@Getter
@Setter
public class QueryRewriteProperties {

    private static final Map<String, List<String>> DEFAULT_BASE_QUERY_TEMPLATES = Map.of(
            "china_related_statements", List.of("%s 涉华言论", "%s 中国评价", "%s 中美关系", "%s 中欧关系"),
            "political_view", List.of("%s 政治倾向", "%s 政党", "%s 政治理念", "%s 政策立场"),
            "contact_information", List.of("%s 公开通讯", "%s 办公电话", "%s 官方邮箱", "%s 认证社交账号", "%s 联系方式"),
            "family_member_situation", List.of("%s 家庭成员", "%s 亲属", "%s 经商", "%s 在华投资", "%s 商业纠纷"),
            "misconduct", List.of("%s 违法记录", "%s 行政处罚", "%s 负面事件", "%s 失信行为")
    );

    private boolean enabled;
    private List<String> providerPriority = new ArrayList<>(List.of("deepseek", "kimi"));
    private int candidateCount = 3;
    private Map<String, String> topicStrategies = new LinkedHashMap<>();
    private Map<String, List<String>> sensitiveTopicPatterns = new LinkedHashMap<>();
    private Map<String, List<String>> baseQueryTemplates = new LinkedHashMap<>();
    private Map<String, List<String>> fallbackTemplates = new LinkedHashMap<>();
    private List<String> expandEnabledTopics = new ArrayList<>();
    private int expandMaxQueryCount = 4;
    private int expandMaxTermLength = 16;
    private boolean logOriginalQuery = true;
    private boolean logFinalQuery = true;

    public List<String> resolveBaseQueryTemplates(String topicKey) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        List<String> configured = baseQueryTemplates.get(topicKey);
        if (configured != null) {
            configured.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(merged::add);
        }
        if (merged.isEmpty()) {
            List<String> defaults = DEFAULT_BASE_QUERY_TEMPLATES.get(topicKey);
            if (defaults != null) {
                defaults.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .forEach(merged::add);
            }
        }
        return List.copyOf(merged);
    }
}
