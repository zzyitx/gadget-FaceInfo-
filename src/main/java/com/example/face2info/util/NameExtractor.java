package com.example.face2info.util;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
/**
 * 候选姓名提取工具。
 * 用于从标题中去噪并提取中英文姓名。
 */
public class NameExtractor {

    private static final List<String> NOISE_WORDS = List.of(
            "图片", "高清", "壁纸", "照片", "photo", "image", "official", "profile", "百科", "微博", "抖音");
    private static final Pattern CHINESE_NAME = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})");
    private static final Pattern ENGLISH_NAME = Pattern.compile("([A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,2})");

    public String cleanCandidateName(String rawTitle) {
        if (!StringUtils.hasText(rawTitle)) {
            return null;
        }
        String cleaned = rawTitle;
        for (String noise : NOISE_WORDS) {
            cleaned = cleaned.replace(noise, " ");
        }
        cleaned = cleaned.replaceAll("[|_\\-()\\[\\]{}]", " ").replaceAll("\\s+", " ").trim();
        String chinese = firstMatch(cleaned, CHINESE_NAME);
        if (StringUtils.hasText(chinese)) {
            return chinese;
        }
        String english = firstMatch(cleaned, ENGLISH_NAME);
        return StringUtils.hasText(english) ? english.trim() : null;
    }

    public double estimateConfidence(String rawTitle, String extractedName, boolean knowledgeGraphMatched) {
        if (!StringUtils.hasText(extractedName)) {
            return 0.0;
        }
        double score = knowledgeGraphMatched ? 0.95 : 0.65;
        String lower = rawTitle == null ? "" : rawTitle.toLowerCase(Locale.ROOT);
        if (lower.contains("wallpaper") || lower.contains("壁纸")) {
            score -= 0.2;
        }
        if (extractedName.length() <= 1) {
            score -= 0.4;
        }
        return Math.max(0.0, Math.min(score, 1.0));
    }

    private String firstMatch(String value, Pattern pattern) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }
}
