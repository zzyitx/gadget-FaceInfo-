package com.example.face2info.service.impl;

import com.example.face2info.client.RealtimeTranslationClient;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import com.example.face2info.entity.internal.SearchQueryTask;
import com.example.face2info.service.MultilingualQueryPlanningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MultilingualQueryPlanningServiceImpl implements MultilingualQueryPlanningService {

    private static final Map<String, Map<String, String>> TERM_TRANSLATIONS = Map.ofEntries(
            Map.entry("education", Map.of("en", "education", "ja", "学歴")),
            Map.entry("教育经历", Map.of("en", "education", "ja", "学歴")),
            Map.entry("family", Map.of("en", "family background", "ja", "家族背景")),
            Map.entry("家庭背景", Map.of("en", "family background", "ja", "家族背景")),
            Map.entry("career", Map.of("en", "career", "ja", "経歴")),
            Map.entry("职业经历", Map.of("en", "career", "ja", "経歴")),
            Map.entry("涉华言论", Map.of("en", "china-related statements", "ja", "対中発言")),
            Map.entry("中国评价", Map.of("en", "views on China", "ja", "中国評価")),
            Map.entry("中美关系", Map.of("en", "China-US relations", "ja", "米中関係")),
            Map.entry("中欧关系", Map.of("en", "China-EU relations", "ja", "中欧関係")),
            Map.entry("政治倾向", Map.of("en", "political stance", "ja", "政治的立場")),
            Map.entry("政党", Map.of("en", "political party", "ja", "政党")),
            Map.entry("政治理念", Map.of("en", "political ideology", "ja", "政治理念")),
            Map.entry("政策立场", Map.of("en", "policy stance", "ja", "政策スタンス")),
            Map.entry("公开通讯", Map.of("en", "public contact", "ja", "公開連絡先")),
            Map.entry("办公电话", Map.of("en", "office phone", "ja", "オフィス電話")),
            Map.entry("官方邮箱", Map.of("en", "official email", "ja", "公式メール")),
            Map.entry("认证社交账号", Map.of("en", "verified social accounts", "ja", "認証済みSNSアカウント")),
            Map.entry("联系方式", Map.of("en", "contact information", "ja", "連絡先")),
            Map.entry("家庭成员", Map.of("en", "family members", "ja", "家族")),
            Map.entry("亲属", Map.of("en", "relatives", "ja", "親族")),
            Map.entry("经商", Map.of("en", "business activities", "ja", "事業活動")),
            Map.entry("在华投资", Map.of("en", "investment in China", "ja", "対中投資")),
            Map.entry("商业纠纷", Map.of("en", "business disputes", "ja", "商業紛争")),
            Map.entry("违法记录", Map.of("en", "violations", "ja", "違法記録")),
            Map.entry("行政处罚", Map.of("en", "administrative penalties", "ja", "行政処分")),
            Map.entry("负面事件", Map.of("en", "negative incidents", "ja", "不祥事")),
            Map.entry("失信", Map.of("en", "dishonesty records", "ja", "信用失墜"))
    );

    private final RealtimeTranslationClient realtimeTranslationClient;

    public MultilingualQueryPlanningServiceImpl() {
        this((query, targetLanguageCode) -> null);
    }

    @Autowired
    public MultilingualQueryPlanningServiceImpl(RealtimeTranslationClient realtimeTranslationClient) {
        this.realtimeTranslationClient = realtimeTranslationClient;
    }

    @Override
    public List<SearchQueryTask> planSecondaryProfileQueries(SearchLanguageProfile profile) {
        return buildTasks(profile, "secondary_profile", List.of(""), 1);
    }

    @Override
    public List<SearchQueryTask> planSectionQueries(SearchLanguageProfile profile, String sectionType, List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }
        return buildTasks(profile, "section_base", terms, 10);
    }

    private List<SearchQueryTask> buildTasks(SearchLanguageProfile profile, String kind, List<String> terms, int priority) {
        if (profile == null) {
            return List.of();
        }
        List<String> normalizedTerms = terms == null || terms.isEmpty() ? List.of("") : terms.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedTerms.isEmpty()) {
            normalizedTerms = List.of("");
        }
        LinkedHashMap<String, SearchQueryTask> deduplicated = new LinkedHashMap<>();
        for (String term : normalizedTerms) {
            appendChineseTask(deduplicated, profile, kind, priority, term);
            appendEnglishTask(deduplicated, profile, kind, priority, term);
            appendNativeTask(deduplicated, profile, kind, priority, term);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private void appendChineseTask(Map<String, SearchQueryTask> tasks,
                                   SearchLanguageProfile profile,
                                   String kind,
                                   int priority,
                                   String term) {
        String resolvedName = resolveName(profile, "zh");
        String queryText = normalizeQuery(resolvedName, term);
        addTask(tasks, "zh", queryText, kind, "round_zh", priority);
    }

    private void appendEnglishTask(Map<String, SearchQueryTask> tasks,
                                   SearchLanguageProfile profile,
                                   String kind,
                                   int priority,
                                   String term) {
        String englishTerm = translateTerm("en", term);
        String resolvedName = resolveName(profile, "en");
        String queryText = normalizeQuery(resolvedName, englishTerm);
        addTask(tasks, "en", queryText, kind, "round_en", priority + 1);
    }

    private void appendNativeTask(Map<String, SearchQueryTask> tasks,
                                  SearchLanguageProfile profile,
                                  String kind,
                                  int priority,
                                  String term) {
        String nativeLanguage = resolveNativeLanguage(profile);
        if (!StringUtils.hasText(nativeLanguage)) {
            log.info("multilingual search round=native skipped resolvedName={} reason=no_native_language", profile.getResolvedName());
            return;
        }
        if ("zh".equals(nativeLanguage) || "en".equals(nativeLanguage)) {
            log.info("multilingual search round=native skipped resolvedName={} reason=native_language_is_{}",
                    profile.getResolvedName(), nativeLanguage);
            return;
        }
        String englishQuery = normalizeQuery(resolveName(profile, "en"), translateTerm("en", term));
        if (!StringUtils.hasText(englishQuery)) {
            log.info("multilingual search round=native skipped resolvedName={} language={} reason=empty_english_query",
                    profile.getResolvedName(), nativeLanguage);
            return;
        }
        String staticNativeQuery = normalizeQuery(resolveName(profile, nativeLanguage), translateTerm(nativeLanguage, term));
        try {
            String translatedQuery = realtimeTranslationClient.translateQuery(englishQuery, nativeLanguage);
            addTask(tasks, nativeLanguage,
                    StringUtils.hasText(translatedQuery) ? translatedQuery : staticNativeQuery,
                    kind, "round_native", priority + 2);
        } catch (RuntimeException ex) {
            if (StringUtils.hasText(staticNativeQuery) && !englishQuery.equalsIgnoreCase(staticNativeQuery)) {
                log.warn("multilingual search round=native fallback resolvedName={} language={} reason=translation_failed error={}",
                        profile.getResolvedName(), nativeLanguage, ex.getMessage());
                addTask(tasks, nativeLanguage, staticNativeQuery, kind, "round_native", priority + 2);
                return;
            }
            log.warn("multilingual search round=native skipped resolvedName={} language={} reason=translation_failed error={}",
                    profile.getResolvedName(), nativeLanguage, ex.getMessage());
        }
    }

    private void addTask(Map<String, SearchQueryTask> tasks,
                         String languageCode,
                         String queryText,
                         String kind,
                         String sourceReason,
                         int priority) {
        if (!StringUtils.hasText(queryText)) {
            return;
        }
        tasks.putIfAbsent(queryText, new SearchQueryTask()
                .setLanguageCode(languageCode)
                .setQueryText(queryText)
                .setQueryKind(kind)
                .setSourceReason(sourceReason)
                .setPriority(priority));
    }

    private String resolveName(SearchLanguageProfile profile, String language) {
        if (profile.getLocalizedNames() != null) {
            String localized = profile.getLocalizedNames().get(language);
            if (StringUtils.hasText(localized)) {
                return localized.trim();
            }
        }
        if ("zh".equals(language) && StringUtils.hasText(profile.getResolvedName())) {
            return profile.getResolvedName().trim();
        }
        if (profile.getLocalizedNames() != null) {
            String english = profile.getLocalizedNames().get("en");
            if (StringUtils.hasText(english)) {
                return english.trim();
            }
        }
        return profile.getResolvedName();
    }

    private String translateTerm(String language, String term) {
        if (!StringUtils.hasText(term) || "zh".equals(language)) {
            return term;
        }
        String normalized = term.trim();
        for (Map.Entry<String, Map<String, String>> entry : TERM_TRANSLATIONS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                String translated = entry.getValue().get(language);
                if (StringUtils.hasText(translated)) {
                    return translated;
                }
            }
        }
        return term;
    }

    private String resolveNativeLanguage(SearchLanguageProfile profile) {
        if (profile.getLanguageCodes() == null) {
            return null;
        }
        for (String language : profile.getLanguageCodes()) {
            if (!StringUtils.hasText(language)) {
                continue;
            }
            String normalized = language.trim().toLowerCase();
            if (!"zh".equals(normalized) && !"en".equals(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private String normalizeQuery(String resolvedName, String term) {
        String left = StringUtils.hasText(resolvedName) ? resolvedName.trim() : "";
        String right = StringUtils.hasText(term) ? term.trim() : "";
        String query = (left + " " + right).trim().replaceAll("\\s+", " ");
        return StringUtils.hasText(query) ? query : null;
    }
}
