package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "目标人脸与文章实体的关联置信度")
public class FaceEntityAssociation {

    @Schema(description = "实体文本")
    private String entityText;

    @Schema(description = "实体类型")
    private String entityType;

    @Schema(description = "关联置信度，0-100")
    private double confidenceScore;

    @Schema(description = "关联原因")
    private String reason;

    @Schema(description = "来源文章 URL")
    private String sourceUrl;

    public String getEntityText() {
        return entityText;
    }

    public FaceEntityAssociation setEntityText(String entityText) {
        this.entityText = entityText;
        return this;
    }

    public String getEntityType() {
        return entityType;
    }

    public FaceEntityAssociation setEntityType(String entityType) {
        this.entityType = entityType;
        return this;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public FaceEntityAssociation setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
        return this;
    }

    public String getReason() {
        return reason;
    }

    public FaceEntityAssociation setReason(String reason) {
        this.reason = reason;
        return this;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public FaceEntityAssociation setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
        return this;
    }
}
