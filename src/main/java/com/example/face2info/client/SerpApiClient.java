package com.example.face2info.client;

import com.example.face2info.entity.internal.SerpApiResponse;

/**
 * SerpAPI 客户端抽象。
 * 用于执行 Google Lens 反向搜图和常规 Google 搜索。
 */
public interface SerpApiClient {

    /**
     * 通过图片 URL 调用 Google Lens 反向搜图。
     *
     * @param imageUrl 外部可访问的图片地址
     * @return SerpAPI 原始响应包装对象
     */
    SerpApiResponse reverseImageSearchByUrl(String imageUrl);

    /**
     * 执行常规 Google 搜索。
     *
     * @param query 搜索关键词
     * @return SerpAPI 原始响应包装对象
     */
    SerpApiResponse googleSearch(String query);
}
