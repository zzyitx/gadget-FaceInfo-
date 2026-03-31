package com.example.face2info.client;

import com.example.face2info.entity.internal.SerpApiResponse;

/**
 * Google 搜索与 Lens 客户端抽象。
 */
public interface GoogleSearchClient {

    /**
     * 通过图片 URL 调用 Google Lens 反向搜图。
     */
    SerpApiResponse reverseImageSearchByUrl(String imageUrl);

    /**
     * 执行常规 Google 搜索。
     */
    SerpApiResponse googleSearch(String query);
}
