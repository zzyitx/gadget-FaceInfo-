package com.example.face2info.client;

import com.example.face2info.entity.internal.NewsApiResponse;

/**
 * NewsAPI 客户端抽象。
 * 负责按人物姓名查询相关新闻原始结果。
 */
public interface NewsApiClient {

    NewsApiResponse searchNews(String name);
}
