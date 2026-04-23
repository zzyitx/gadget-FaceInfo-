package com.example.face2info.client.impl;

import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import com.example.face2info.entity.internal.SearchLanguageInferenceResult;
import com.example.face2info.entity.internal.SectionedSummary;
import com.example.face2info.entity.internal.TopicExpansionDecision;
import org.springframework.lang.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 摘要生成客户端默认占位实现。
 */
@Component
@ConditionalOnProperty(prefix = "face2info.api.summary", name = "provider", havingValue = "noop", matchIfMissing = true)
public class NoopSummaryGenerationClient implements SummaryGenerationClient {

    private final ApiProperties properties;

    public NoopSummaryGenerationClient(ApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public PageSummary summarizePage(String fallbackName, PageContent page) {
        if (page == null) {
            return null;
        }
        return new PageSummary()
                .setSourceUrl(page.getUrl())
                .setTitle(page.getTitle());
    }

    @Override
    public ResolvedPersonProfile summarizePersonFromPageSummaries(String fallbackName, List<PageSummary> pageSummaries) {
        ResolvedPersonProfile profile = new ResolvedPersonProfile()
                .setResolvedName(fallbackName);

        if (pageSummaries != null && !pageSummaries.isEmpty()) {
            List<String> evidenceUrls = new ArrayList<>();
            for (PageSummary pageSummary : pageSummaries) {
                if (pageSummary == null) {
                    continue;
                }
                String url = pageSummary.getSourceUrl();
                if (url != null && !url.isBlank()) {
                    evidenceUrls.add(url);
                }
            }
            profile.setEvidenceUrls(evidenceUrls);
        } else {
            profile.setEvidenceUrls(new ArrayList<>());
        }

        if (!properties.getApi().getSummary().isEnabled()) {
            return profile;
        }

        if (!"noop".equalsIgnoreCase(properties.getApi().getSummary().getProvider())) {
            return profile;
        }

        return profile;
    }

    @Override
    public MultipartFile enhanceFaceImageByUrl(String imageUrl, String filename, String contentType) {
        throw new UnsupportedOperationException("noop summary provider does not support image enhancement by URL");
    }

    @Override
    public String summarizeSectionFromPageSummaries(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        throw new UnsupportedOperationException("noop summary provider does not support section summary generation");
    }

    @Override
    public TopicExpansionDecision expandTopicQueriesFromPageSummaries(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        throw new UnsupportedOperationException("noop summary provider does not support topic expansion generation");
    }

    @Override
    public SectionedSummary summarizeSectionedSectionFromPageSummaries(String resolvedName, String sectionType, List<PageSummary> pageSummaries) {
        throw new UnsupportedOperationException("noop summary provider does not support sectioned summary generation");
    }

    @Override
    public ResolvedPersonProfile applyComprehensiveJudgement(String fallbackName,
                                                             List<PageSummary> pageSummaries,
                                                             ResolvedPersonProfile draftProfile) {
        return draftProfile == null ? new ResolvedPersonProfile().setResolvedName(fallbackName) : draftProfile;
    }

    @Override
    public SearchLanguageInferenceResult inferSearchLanguageProfile(String resolvedName,
                                                                    ResolvedPersonProfile profile) {
        throw new UnsupportedOperationException("noop summary provider does not support search language inference");
    }

    @Override
    public String generateDigitalFootprintQueries(String resolvedName,
                                                  SearchLanguageProfile languageProfile,
                                                  @Nullable ResolvedPersonProfile profile) {
        return "";
    }

    @Override
    public String generatePrimarySearchQueries(String resolvedName,
                                               SearchLanguageProfile languageProfile,
                                               @Nullable ResolvedPersonProfile profile,
                                               String sectionType) {
        return "";
    }
}

