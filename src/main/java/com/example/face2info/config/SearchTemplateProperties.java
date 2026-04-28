package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 搜索查询模板配置。
 */
@Getter
@Setter
public class SearchTemplateProperties {

    private static final Map<String, List<String>> DEFAULT_QUERY_TEMPLATES = Map.ofEntries(
            Map.entry("secondary_profile", List.of(
                    "{name}",
                    "{name} biography",
                    "{name} official profile",
                    "{english_name} biography",
                    "{native_name} 人物简介"
            )),
            Map.entry("education", List.of(
                    "{name} education",
                    "{name} alma mater",
                    "{native_name} 教育经历",
                    "{native_name} 毕业院校"
            )),
            Map.entry("family", List.of(
                    "{name} family background",
                    "{name} upbringing",
                    "{native_name} 家庭背景",
                    "{native_name} 成长经历"
            )),
            Map.entry("family_background", List.of(
                    "{name} family background",
                    "{name} upbringing",
                    "{native_name} 家庭背景",
                    "{native_name} 成长经历"
            )),
            Map.entry("family_members", List.of(
                    "{name} family members",
                    "{name} relatives",
                    "{native_name} 家庭成员",
                    "{native_name} 亲属"
            )),
            Map.entry("family_member_situation", List.of(
                    "{name} family members",
                    "{name} relatives",
                    "{native_name} 家庭成员",
                    "{native_name} 亲属",
                    "{native_name} 经商",
                    "{native_name} 在华投资",
                    "{native_name} 商业纠纷"
            )),
            Map.entry("career", List.of(
                    "{name} career",
                    "{name} biography",
                    "{native_name} 职业经历",
                    "{native_name} 任职经历"
            )),
            Map.entry("contact_information", List.of(
                    "{native_name} 公开通讯",
                    "{native_name} 办公电话",
                    "{native_name} 官方邮箱",
                    "{native_name} 联系方式",
                    "{name} official website",
                    "{name} verified social accounts",
                    "{native_name} 官方网站",
                    "{native_name} 认证社交账号",
                    "{username} {platform}"
            )),
            Map.entry("china_related_statements", List.of(
                    "{native_name} 涉华言论",
                    "{native_name} 中国评价",
                    "{native_name} 中美关系",
                    "{native_name} 中欧关系",
                    "{name} China policy"
            )),
            Map.entry("political_view", List.of(
                    "{native_name} 政治倾向",
                    "{native_name} 政党",
                    "{native_name} 政策立场",
                    "{name} political stance"
            )),
            Map.entry("misconduct", List.of(
                    "{native_name} 违法记录",
                    "{native_name} 行政处罚",
                    "{native_name} 负面事件",
                    "{name} controversy"
            ))
    );

    private Map<String, List<String>> queryTemplates = new java.util.LinkedHashMap<>();
    private List<String> expandEnabledTopics = new ArrayList<>();
    private int expandMaxQueryCount = 4;
    private int expandMaxTermLength = 16;

    public List<String> resolveQueryTemplates(String topicKey) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        List<String> configured = queryTemplates.get(topicKey);
        if (configured != null) {
            configured.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(merged::add);
        }
        if (merged.isEmpty()) {
            List<String> defaults = DEFAULT_QUERY_TEMPLATES.get(topicKey);
            if (defaults != null) {
                defaults.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .forEach(merged::add);
            }
        }
        return new ArrayList<>(merged);
    }
}
