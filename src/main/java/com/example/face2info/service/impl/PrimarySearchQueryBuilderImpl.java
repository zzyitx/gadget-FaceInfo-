package com.example.face2info.service.impl;

import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.client.impl.DeepSeekSummaryGenerationClient;
import com.example.face2info.entity.internal.PersonBasicInfo;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import com.example.face2info.service.PrimarySearchQueryBuilder;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class PrimarySearchQueryBuilderImpl implements PrimarySearchQueryBuilder {

    private static final int REQUIRED_QUERY_COUNT = 7;
    private static final int MIN_IDENTITY_OCCURRENCES = 5;
    private static final Map<String, SectionPromptMetadata> SECTION_METADATA = Map.ofEntries(
            Map.entry("secondary_profile", new SectionPromptMetadata(
                    "人物背景",
                    List.of("公开身份信息", "人物履历概览", "近期公开表态"),
                    "profile",
                    List.of("background", "biography", "recent statements")
            )),
            Map.entry("education", new SectionPromptMetadata(
                    "教育经历",
                    List.of("学历背景", "毕业院校", "学术经历"),
                    "education",
                    List.of("education background", "alma mater", "academic history")
            )),
            Map.entry("family", new SectionPromptMetadata(
                    "家庭背景",
                    List.of("家庭出身", "成长背景", "亲属情况"),
                    "family background",
                    List.of("family origin", "upbringing", "relatives")
            )),
            Map.entry("career", new SectionPromptMetadata(
                    "职业经历",
                    List.of("任职经历", "关键职位", "职业轨迹"),
                    "career",
                    List.of("positions", "key roles", "career path")
            )),
            Map.entry("china_related_statements", new SectionPromptMetadata(
                    "涉华言论",
                    List.of("驻华期间政策表态", "对华策略分析", "近期关于新兴威胁的论述"),
                    "China policy",
                    List.of("policy statements", "China strategy", "emerging threats")
            )),
            Map.entry("political_view", new SectionPromptMetadata(
                    "政治倾向",
                    List.of("党派与组织", "政治理念", "政策立场"),
                    "political stance",
                    List.of("party ties", "political ideology", "policy stance")
            )),
            Map.entry("contact_information", new SectionPromptMetadata(
                    "联系方式",
                    List.of("公开通讯", "官方邮箱", "认证社交账号"),
                    "contact information",
                    List.of("public contact", "official email", "verified social accounts")
            )),
            Map.entry("family_member_situation", new SectionPromptMetadata(
                    "家庭成员情况",
                    List.of("家庭成员", "经商与投资", "争议与纠纷"),
                    "family situation",
                    List.of("family members", "business ties", "disputes")
            )),
            Map.entry("misconduct", new SectionPromptMetadata(
                    "污点劣迹",
                    List.of("违法记录", "行政处罚", "负面事件"),
                    "controversies",
                    List.of("violations", "administrative penalties", "negative incidents")
            ))
    );

    private final DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient;
    private final SummaryGenerationClient summaryGenerationClient;

    public PrimarySearchQueryBuilderImpl(@Nullable DeepSeekSummaryGenerationClient deepSeekSummaryGenerationClient,
                                         SummaryGenerationClient summaryGenerationClient) {
        this.deepSeekSummaryGenerationClient = deepSeekSummaryGenerationClient;
        this.summaryGenerationClient = summaryGenerationClient;
    }

    @Override
    public List<String> buildSecondaryProfileQueries(String resolvedName,
                                                     SearchLanguageProfile languageProfile,
                                                     @Nullable ResolvedPersonProfile profile) {
        return buildInternal(resolvedName, languageProfile, profile, "secondary_profile");
    }

    @Override
    public List<String> buildSectionQueries(String resolvedName,
                                            SearchLanguageProfile languageProfile,
                                            @Nullable ResolvedPersonProfile profile,
                                            String sectionType) {
        return buildInternal(resolvedName, languageProfile, profile, sectionType);
    }

    private List<String> buildInternal(String resolvedName,
                                       SearchLanguageProfile languageProfile,
                                       @Nullable ResolvedPersonProfile profile,
                                       String sectionType) {
        List<String> deepSeekQueries = runModelSafely(
                () -> deepSeekSummaryGenerationClient == null ? null
                        : deepSeekSummaryGenerationClient.generatePrimarySearchQueries(resolvedName, languageProfile, profile, sectionType),
                resolvedName,
                languageProfile,
                profile,
                sectionType
        );
        if (!deepSeekQueries.isEmpty()) {
            return deepSeekQueries;
        }

        List<String> kimiQueries = runModelSafely(
                () -> summaryGenerationClient.generatePrimarySearchQueries(resolvedName, languageProfile, profile, sectionType),
                resolvedName,
                languageProfile,
                profile,
                sectionType
        );
        if (!kimiQueries.isEmpty()) {
            return kimiQueries;
        }

        return fallbackQueries(resolvedName, languageProfile, profile, sectionType);
    }

    private List<String> runModelSafely(QuerySupplier supplier,
                                        String resolvedName,
                                        SearchLanguageProfile languageProfile,
                                        @Nullable ResolvedPersonProfile profile,
                                        String sectionType) {
        try {
            return parseAndValidate(supplier.get(), resolvedName, languageProfile, profile, sectionType);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    List<String> parseAndValidate(String rawText,
                                  String resolvedName,
                                  SearchLanguageProfile languageProfile,
                                  @Nullable ResolvedPersonProfile profile,
                                  String sectionType) {
        if (!StringUtils.hasText(rawText)) {
            return List.of();
        }
        CanonicalIdentity identity = resolveIdentity(resolvedName, languageProfile, profile);
        if (!identity.hasAnyName()) {
            return List.of();
        }

        LinkedHashMap<String, String> deduplicated = new LinkedHashMap<>();
        for (String line : rawText.split("\\R")) {
            String cleaned = normalizeLine(line);
            if (!isValidQueryLine(cleaned, identity)) {
                continue;
            }
            deduplicated.putIfAbsent(cleaned, cleaned);
        }

        List<String> queries = new ArrayList<>(deduplicated.values());
        if (queries.size() != REQUIRED_QUERY_COUNT) {
            return List.of();
        }
        if (identity.hasDisambiguationIdentity()) {
            long identityCount = queries.stream().filter(query -> containsIdentity(query, identity)).count();
            if (identityCount < MIN_IDENTITY_OCCURRENCES) {
                return List.of();
            }
        }
        return queries;
    }

    private boolean isSecondaryProfile(String sectionType) {
        return "secondary_profile".equals(sectionType);
    }

    private List<String> fallbackQueries(String resolvedName,
                                         SearchLanguageProfile languageProfile,
                                         @Nullable ResolvedPersonProfile profile,
                                         String sectionType) {
        CanonicalIdentity identity = resolveIdentity(resolvedName, languageProfile, profile);
        SectionPromptMetadata metadata = metadata(sectionType);
        String zhName = firstNonBlank(identity.zhFullName(), identity.fallbackName(), identity.enFullName());
        String enName = firstNonBlank(identity.enFullName(), identity.zhFullName(), identity.fallbackName());
        String zhIdentity = identity.zhIdentity();
        String enIdentity = identity.enIdentity();
        String title = metadata.title();
        String subTopicA = metadata.subTopics().isEmpty() ? title : metadata.subTopics().get(0);
        String subTopicB = metadata.subTopics().size() > 1 ? metadata.subTopics().get(1) : title;
        String englishTopic = firstNonBlank(metadata.englishTitle(), "profile");

        if (!identity.hasDisambiguationIdentity()) {
            if ("china_related_statements".equals(sectionType)) {
                return List.of(
                        joinTokens(zhName, "涉华言论"),
                        joinTokens(zhName, "中国评价"),
                        joinTokens(zhName, "中美关系"),
                        joinTokens(zhName, "中欧关系"),
                        joinTokens(enName, englishTopic),
                        joinTokens(zhName, "演讲", "PDF"),
                        joinTokens(zhName, "争议", "采访")
                );
            }
            return List.of(
                    joinTokens(zhName),
                    joinTokens(zhName, title),
                    joinTokens(zhName, simplifyTopic(subTopicA)),
                    joinTokens(zhName, simplifyTopic(subTopicB)),
                    joinTokens(enName, englishTopic),
                    joinTokens(zhName, "演讲", "PDF"),
                    joinTokens(zhName, "争议", "采访")
            );
        }

        return List.of(
                joinTokens(zhName, zhIdentity),
                joinTokens(zhName, zhIdentity, title),
                joinTokens(zhName, zhIdentity, simplifyTopic(subTopicA)),
                joinTokens(zhName, zhIdentity, simplifyTopic(subTopicB)),
                joinTokens(enName, enIdentity, englishTopic),
                joinTokens(zhName, zhIdentity, "演讲", "PDF"),
                joinTokens(zhName, zhIdentity, "争议", "采访")
        );
    }

    private boolean isValidQueryLine(String line, CanonicalIdentity identity) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        String normalized = line.toLowerCase(Locale.ROOT);
        if (normalized.contains("```") || normalized.startsWith("{") || normalized.startsWith("[")) {
            return false;
        }
        int tokenCount = line.split("\\s+").length;
        if (tokenCount < 2 || tokenCount > 5) {
            return false;
        }
        return identity.acceptedNames().stream()
                .filter(StringUtils::hasText)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private boolean containsIdentity(String query, CanonicalIdentity identity) {
        String normalized = query.toLowerCase(Locale.ROOT);
        return (StringUtils.hasText(identity.zhIdentity()) && normalized.contains(identity.zhIdentity().toLowerCase(Locale.ROOT)))
                || (StringUtils.hasText(identity.enIdentity()) && normalized.contains(identity.enIdentity().toLowerCase(Locale.ROOT)));
    }

    private String normalizeLine(String line) {
        if (!StringUtils.hasText(line)) {
            return null;
        }
        return line.trim().replaceAll("\\s+", " ");
    }

    private CanonicalIdentity resolveIdentity(String resolvedName,
                                              SearchLanguageProfile languageProfile,
                                              @Nullable ResolvedPersonProfile profile) {
        String zhName = localizedName(languageProfile, "zh");
        String enName = localizedName(languageProfile, "en");
        String fallbackName = firstNonBlank(trimToNull(resolvedName),
                languageProfile == null ? null : trimToNull(languageProfile.getResolvedName()));
        String background = buildBackground(profile);
        String zhIdentity = extractZhIdentity(background, profile);
        String enIdentity = translateIdentity(zhIdentity, background);
        List<String> acceptedNames = new ArrayList<>();
        addIfPresent(acceptedNames, zhName);
        addIfPresent(acceptedNames, enName);
        addIfPresent(acceptedNames, fallbackName);
        return new CanonicalIdentity(zhName, enName, fallbackName, zhIdentity, enIdentity, acceptedNames.stream().filter(Objects::nonNull).distinct().toList());
    }

    private String buildBackground(@Nullable ResolvedPersonProfile profile) {
        if (profile == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, trimToNull(profile.getDescription()));
        addIfPresent(parts, trimToNull(profile.getSummary()));
        PersonBasicInfo basicInfo = profile.getBasicInfo();
        if (basicInfo != null) {
            if (basicInfo.getOccupations() != null && !basicInfo.getOccupations().isEmpty()) {
                addIfPresent(parts, basicInfo.getOccupations().get(0));
            }
            if (basicInfo.getBiographies() != null && !basicInfo.getBiographies().isEmpty()) {
                addIfPresent(parts, basicInfo.getBiographies().get(0));
            }
        }
        return String.join(" ", parts);
    }

    private String extractZhIdentity(String background, @Nullable ResolvedPersonProfile profile) {
        if (StringUtils.hasText(background)) {
            if (background.contains("驻华大使")) {
                return "驻华大使";
            }
            if (background.contains("大使")) {
                return "大使";
            }
            if (background.contains("创始人")) {
                return "创始人";
            }
            if (background.contains("首席执行官") || background.contains("CEO")) {
                return "CEO";
            }
            if (background.contains("外交官")) {
                return "外交官";
            }
        }
        if (profile != null && profile.getBasicInfo() != null
                && profile.getBasicInfo().getOccupations() != null
                && !profile.getBasicInfo().getOccupations().isEmpty()) {
            return simplifyTopic(profile.getBasicInfo().getOccupations().get(0));
        }
        return null;
    }

    private String translateIdentity(String zhIdentity, String background) {
        if (!StringUtils.hasText(zhIdentity)
                && !background.toLowerCase(Locale.ROOT).contains("ambassador")
                && !background.toLowerCase(Locale.ROOT).contains("founder")
                && !background.toLowerCase(Locale.ROOT).contains("chief executive")
                && !background.toLowerCase(Locale.ROOT).contains("diplomat")) {
            return null;
        }
        if ("驻华大使".equals(zhIdentity) || "大使".equals(zhIdentity) || background.toLowerCase(Locale.ROOT).contains("ambassador")) {
            return "Ambassador";
        }
        if ("创始人".equals(zhIdentity) || background.toLowerCase(Locale.ROOT).contains("founder")) {
            return "Founder";
        }
        if ("CEO".equalsIgnoreCase(zhIdentity) || background.toLowerCase(Locale.ROOT).contains("chief executive")) {
            return "CEO";
        }
        if ("外交官".equals(zhIdentity) || background.toLowerCase(Locale.ROOT).contains("diplomat")) {
            return "diplomat";
        }
        return "profile";
    }

    private String localizedName(SearchLanguageProfile languageProfile, String code) {
        if (languageProfile == null || languageProfile.getLocalizedNames() == null) {
            return null;
        }
        return trimToNull(languageProfile.getLocalizedNames().get(code));
    }

    private SectionPromptMetadata metadata(String sectionType) {
        return SECTION_METADATA.getOrDefault(sectionType, SECTION_METADATA.get("secondary_profile"));
    }

    private String simplifyTopic(String topic) {
        String normalized = trimToNull(topic);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized
                .replace("期间", "")
                .replace("分析", "")
                .replace("相关", "")
                .replace("近期关于", "")
                .replace("的论述", "")
                .replace("对华策略", "对华策略")
                .trim();
    }

    private String joinTokens(String... tokens) {
        List<String> normalized = new ArrayList<>();
        for (String token : tokens) {
            String value = trimToNull(token);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        return String.join(" ", normalized).trim();
    }

    private void addIfPresent(List<String> values, String value) {
        String normalized = trimToNull(value);
        if (StringUtils.hasText(normalized)) {
            values.add(normalized);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @FunctionalInterface
    private interface QuerySupplier {
        String get();
    }

    private record SectionPromptMetadata(String title,
                                         List<String> subTopics,
                                         String englishTitle,
                                         List<String> englishSubTopics) {
    }

    private record CanonicalIdentity(String zhFullName,
                                     String enFullName,
                                     String fallbackName,
                                     String zhIdentity,
                                     String enIdentity,
                                     List<String> acceptedNames) {
        private boolean hasAnyName() {
            return !acceptedNames.isEmpty();
        }

        private boolean hasDisambiguationIdentity() {
            return StringUtils.hasText(zhIdentity) || StringUtils.hasText(enIdentity);
        }
    }
}
