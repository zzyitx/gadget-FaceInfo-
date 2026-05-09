package com.example.face2info.util;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 候选姓名提取工具。
 * 用于从标题文本中清洗噪声词，并提取中英文人物名称。
 */
@Component
public class NameExtractor {

    private static final List<String> NOISE_WORDS = List.of(
            "图片", "高清", "壁纸", "照片", "photo", "photos", "image", "images",
            "news", "media", "video", "videos", "official", "profile", "百科", "微博", "抖音"
    );
    private static final Set<String> NON_PERSON_NAME_WORDS = Set.of(
            "about", "article", "articles", "breaking", "channel", "example", "explore", "gallery", "global",
            "headline", "headlines", "home", "homepage", "image", "images", "latest", "local", "media", "news",
            "official", "page", "photo", "photos", "picture", "pictures", "press", "profile", "result", "results",
            "search", "story", "stories", "stock", "topic", "topics", "video", "videos", "website", "world"
    );
    private static final Pattern CHINESE_NAME = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})");
    private static final Pattern ENGLISH_NAME = Pattern.compile("([A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,2})");

    /**
     * 从原始标题中提取尽可能干净的人名。
     */
    public String cleanCandidateName(String rawTitle) {
        if (!StringUtils.hasText(rawTitle)) {
            return null;
        }
        String cleaned = rawTitle;
        for (String noise : NOISE_WORDS) {
            cleaned = removeNoiseWord(cleaned, noise);
        }
        cleaned = cleaned.replaceAll("[|_\\-()\\[\\]{}]", " ").replaceAll("\\s+", " ").trim();
        String chinese = firstMatch(cleaned, CHINESE_NAME);
        if (StringUtils.hasText(chinese)) {
            return normalizeCandidate(chinese);
        }
        String english = firstMatch(cleaned, ENGLISH_NAME);
        return normalizeCandidate(english);
    }

    /**
     * 判断文本是否能作为候选人名使用。
     */
    public boolean isLikelyPersonName(String value) {
        return StringUtils.hasText(cleanCandidateName(value));
    }

    /**
     * 根据来源类型和噪声特征估算候选名称可信度。
     */
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

    /**
     * 返回文本中第一个匹配正则的片段。
     */
    private String firstMatch(String value, Pattern pattern) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String removeNoiseWord(String value, String noise) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(noise)) {
            return value;
        }
        // 英文噪声词按单词边界删除，避免把人名中的局部字符误删成不可识别的候选名。
        if (noise.chars().allMatch(ch -> ch < 128 && Character.isLetterOrDigit(ch))) {
            return value.replaceAll("(?i)\\b" + Pattern.quote(noise) + "\\b", " ");
        }
        return value.replace(noise, " ");
    }

    private String normalizeCandidate(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String normalized = candidate.trim().replaceAll("\\s+", " ");
        return isGenericNonPersonName(normalized) ? null : normalized;
    }

    private boolean isGenericNonPersonName(String candidate) {
        String[] tokens = candidate.toLowerCase(Locale.ROOT).split("\\s+");
        if (tokens.length == 0) {
            return true;
        }
        // 只有全部 token 都是通用网页词时才判为非人名，避免误伤包含真实姓名的混合标题。
        for (String token : tokens) {
            if (!NON_PERSON_NAME_WORDS.contains(token)) {
                return false;
            }
        }
        return true;
    }
}
