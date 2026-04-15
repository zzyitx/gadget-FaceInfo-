package com.example.face2info.entity.internal;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 派生主题类型。
 */
public enum DerivedTopicType {

    EDUCATION("education"),
    FAMILY("family"),
    CAREER("career"),
    CHINA_RELATED_STATEMENTS("china_related_statements"),
    POLITICAL_VIEW("political_view"),
    CONTACT_INFORMATION("contact_information"),
    FAMILY_MEMBER_SITUATION("family_member_situation"),
    MISCONDUCT("misconduct"),
    CONTROVERSIAL_STATEMENT("controversial_statement"),
    CUSTOM("custom");

    private final String key;

    DerivedTopicType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static DerivedTopicType fromSectionType(String sectionType) {
        if (!StringUtils.hasText(sectionType)) {
            return CUSTOM;
        }
        String normalized = sectionType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "education" -> EDUCATION;
            case "family" -> FAMILY;
            case "career" -> CAREER;
            case "china_related_statements" -> CHINA_RELATED_STATEMENTS;
            case "political_view" -> POLITICAL_VIEW;
            case "contact_information" -> CONTACT_INFORMATION;
            case "family_member_situation" -> FAMILY_MEMBER_SITUATION;
            case "misconduct" -> MISCONDUCT;
            case "controversial_statement" -> CONTROVERSIAL_STATEMENT;
            default -> CUSTOM;
        };
    }
}
