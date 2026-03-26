package com.example.face2info.entity.internal;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * SerpAPI 原始响应包装对象。
 */
public class SerpApiResponse {

    private JsonNode root;

    public JsonNode getRoot() {
        return root;
    }

    public SerpApiResponse setRoot(JsonNode root) {
        this.root = root;
        return this;
    }
}
