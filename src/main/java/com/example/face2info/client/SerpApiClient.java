package com.example.face2info.client;

import com.example.face2info.entity.internal.SerpApiResponse;

/**
 * SerpAPI 客户端抽象。
 */
public interface SerpApiClient {

    /**
     * 通过图片 URL 调用 Yandex 反向搜图。
     */
    SerpApiResponse reverseImageSearchByUrlYandex(String imageUrl, String tab);

    /**
     * 通过图片 URL 调用 Bing 图片搜索。
     */
    SerpApiResponse reverseImageSearchByUrlBing(String imageUrl);

    /**
     * 执行 Bing 图片文本搜索。
     */
    SerpApiResponse searchBingImages(String query);
}
