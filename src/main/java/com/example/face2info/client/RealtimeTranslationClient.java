package com.example.face2info.client;

/**
 * 主题查询实时翻译客户端抽象。
 */
public interface RealtimeTranslationClient {

    String translateQuery(String query, String targetLanguageCode);
}
