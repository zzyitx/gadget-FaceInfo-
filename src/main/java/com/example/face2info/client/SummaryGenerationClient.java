package com.example.face2info.client;

import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 正文摘要和大模型视觉推理客户端抽象。
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

    /**
     * 基于图床 URL 对人脸图像做高清化处理。
     */
    MultipartFile enhanceFaceImageByUrl(String imageUrl, String filename, String contentType);

    /**
     * 基于主题相关的篇级摘要集合生成单段主题摘要。
     */
    String summarizeSectionFromPageSummaries(String resolvedName, String sectionType, List<PageSummary> pageSummaries);

    /**
     * 结合篇级总结和最终总结进行综合判断。
     */
    ResolvedPersonProfile applyComprehensiveJudgement(String fallbackName,
                                                      List<PageSummary> pageSummaries,
                                                      ResolvedPersonProfile draftProfile);
}

