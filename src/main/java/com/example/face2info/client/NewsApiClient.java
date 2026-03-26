package com.example.face2info.client;

import com.example.face2info.entity.internal.NewsApiResponse;

/**
 * NewsAPI 客户端抽象。
 * 用于查询与候选名称相关的新闻列表。
 */
public interface NewsApiClient {

    /**
     * 按名称查询新闻。
     *
     * @param name 人物名称
     * @return NewsAPI 原始响应包装对象
     */
    NewsApiResponse searchNews(String name);
}
