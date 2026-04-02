package com.example.face2info.entity.internal;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SerpAPI 原始响应包装对象。
 */
@Schema(description = "SerpAPI 原始响应包装")
public class SerpApiResponse {

    @Schema(description = "SerpAPI 返回的原始 JSON 根节点")
    private JsonNode root;

    public JsonNode getRoot() {
        return root;
    }

    public SerpApiResponse setRoot(JsonNode root) {
        this.root = root;
        return this;
    }
}
