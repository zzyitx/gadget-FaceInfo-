package com.example.face2info.entity.internal;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * NewsAPI 原始响应包装对象。
 */
@Schema(description = "NewsAPI 原始响应包装")
public class NewsApiResponse {

    @Schema(description = "NewsAPI 返回的原始 JSON 根节点")
    private JsonNode root;

    public JsonNode getRoot() {
        return root;
    }

    public NewsApiResponse setRoot(JsonNode root) {
        this.root = root;
        return this;
    }
}
