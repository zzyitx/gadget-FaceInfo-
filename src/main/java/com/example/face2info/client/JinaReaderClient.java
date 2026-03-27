package com.example.face2info.client;

import com.example.face2info.entity.internal.PageContent;

import java.util.List;

/**
 * Jina 页面正文读取客户端抽象。
 */
public interface JinaReaderClient {

    /**
     * 读取多个 URL 的正文内容。
     *
     * @param urls 页面地址列表
     * @return 页面正文结果列表
     */
    List<PageContent> readPages(List<String> urls);
}
