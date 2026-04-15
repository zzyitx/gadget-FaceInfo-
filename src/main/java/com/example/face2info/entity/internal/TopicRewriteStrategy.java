package com.example.face2info.entity.internal;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 主题查询策略。
 */
public enum TopicRewriteStrategy {

    DIRECT,
    NORMALIZE,
    REWRITE;

    public static TopicRewriteStrategy fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return NORMALIZE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "direct" -> DIRECT;
            case "rewrite" -> REWRITE;
            default -> NORMALIZE;
        };
    }
}
