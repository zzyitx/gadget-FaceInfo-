package com.example.face2info.entity.internal;

import org.springframework.util.StringUtils;

/**
 * 查询改写模型提供方。
 */
public enum QueryRewriteProvider {

    DEEPSEEK,
    KIMI;

    public static QueryRewriteProvider fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return DEEPSEEK;
        }
        return "kimi".equalsIgnoreCase(value.trim()) ? KIMI : DEEPSEEK;
    }
}
