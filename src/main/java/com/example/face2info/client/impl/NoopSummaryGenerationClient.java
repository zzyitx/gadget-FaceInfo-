package com.example.face2info.client.impl;

import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    public ResolvedPersonProfile summarizePerson(String fallbackName, List<PageContent> pages) {
        ResolvedPersonProfile profile = new ResolvedPersonProfile()
                .setResolvedName(fallbackName);

        if (pages != null && !pages.isEmpty()) {
            profile.setEvidenceUrls(pages.stream()
                    .map(PageContent::getUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .collect(Collectors.toList()));
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
}
