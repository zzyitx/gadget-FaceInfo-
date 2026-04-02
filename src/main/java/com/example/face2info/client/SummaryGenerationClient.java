package com.example.face2info.client;

import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ResolvedPersonProfile;

import java.util.List;

/**
 * 正文摘要生成客户端抽象。
 */
public interface SummaryGenerationClient {

    /**
     * 基于单篇正文生成结构化篇级摘要。
     */
    PageSummary summarizePage(String fallbackName, PageContent page);

    /**
     * 基于篇级摘要集合生成最终人物总结。
     */
    ResolvedPersonProfile summarizePersonFromPageSummaries(String fallbackName, List<PageSummary> pageSummaries);
}
