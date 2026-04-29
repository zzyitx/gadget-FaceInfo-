package com.example.face2info.client;

import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.SearchLanguageProfile;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageInferenceResult;
import com.example.face2info.entity.internal.SectionedSummary;
import com.example.face2info.entity.internal.TopicExpansionDecision;
import com.example.face2info.entity.internal.WebEvidence;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 正文摘要和大模型视觉推理客户端抽象。
 */
public interface SummaryGenerationClient {

    /**
     * 基于单篇正文生成结构化页面摘要。
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
     * 基于主题相关的篇级摘要集合生成第二轮搜索扩展词。
     */
    TopicExpansionDecision expandTopicQueriesFromPageSummaries(String resolvedName, String sectionType, List<PageSummary> pageSummaries);

    /**
     * 基于主题相关的篇级摘要集合生成分段摘要。
     */
    SectionedSummary summarizeSectionedSectionFromPageSummaries(String resolvedName, String sectionType, List<PageSummary> pageSummaries);

    /**
     * 结合页面摘要集合和最终总结进行综合判断。
     */
    ResolvedPersonProfile applyComprehensiveJudgement(String fallbackName,
                                                      List<PageSummary> pageSummaries,
                                                      ResolvedPersonProfile draftProfile);

    /**
     * 基于已聚合人物信息推断后续检索语言与多语言姓名。
     */
    SearchLanguageInferenceResult inferSearchLanguageProfile(String resolvedName,
                                                             ResolvedPersonProfile profile);

    /**
     * 生成用于人物数字指纹检索的纯文本 query 列表。
     */
    String generateDigitalFootprintQueries(String resolvedName,
                                           SearchLanguageProfile languageProfile,
                                           @Nullable ResolvedPersonProfile profile);

    /**
     * 生成用于人物主路径检索的精准 Google query 列表。
     */
    String generatePrimarySearchQueries(String resolvedName,
                                        SearchLanguageProfile languageProfile,
                                        @Nullable ResolvedPersonProfile profile,
                                        String sectionType);

    /**
     * 从账号相关搜索结果中判断最可能属于目标人物的用户名候选。
     */
    default List<String> inferLikelySocialUsernames(String resolvedName, List<WebEvidence> evidences) {
        return List.of();
    }
}
