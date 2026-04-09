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
     * 对上传的人脸图像做高清化处理，供后续识别主流程使用。
     */
    MultipartFile enhanceFaceImage(MultipartFile image);

    /**
     * 基于人脸图像进行人物识别，返回候选名称列表。
     */
    List<String> recognizeFaceCandidateNames(MultipartFile image);

    /**
     * 基于篇级摘要集合生成最终人物总结。
     */
    ResolvedPersonProfile summarizePersonFromPageSummaries(String fallbackName, List<PageSummary> pageSummaries);

    /**
     * 结合候选名称、篇级总结和最终总结进行综合判断。
     */
    ResolvedPersonProfile applyComprehensiveJudgement(String fallbackName,
                                                      List<String> candidateNames,
                                                      List<PageSummary> pageSummaries,
                                                      ResolvedPersonProfile draftProfile);
}

