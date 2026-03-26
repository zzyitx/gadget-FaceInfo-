package com.example.face2info.entity.internal;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * NewsAPI 原始响应包装对象。
 */
public class NewsApiResponse {

    private JsonNode root;

    public JsonNode getRoot() {
        return root;
    }

    public NewsApiResponse setRoot(JsonNode root) {
        this.root = root;
        return this;
    }
}
