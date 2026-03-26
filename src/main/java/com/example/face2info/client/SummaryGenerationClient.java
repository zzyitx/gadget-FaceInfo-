package com.example.face2info.client;

import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.ResolvedPersonProfile;

import java.util.List;

/**
 * 摘要生成客户端抽象。
 */
public interface SummaryGenerationClient {

    /**
     * 基于页面正文生成最终人物摘要。
     *
     * @param fallbackName 降级使用的人名
     * @param pages 页面正文结果
     * @return 摘要结果对象
     */
    ResolvedPersonProfile summarizePerson(String fallbackName, List<PageContent> pages);
}
