package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文章实体关系")
public class EntityRelation {

    @Schema(description = "关系主体")
    private String subject;

    @Schema(description = "关系类型，如 HAS_OCCUPATION、AFFILIATED_WITH")
    private String relation;

    @Schema(description = "关系客体")
    private String object;

    @Schema(description = "关系置信度，0-100")
    private double confidence;

    @Schema(description = "关系来源文章 URL")
    private String sourceUrl;

    public String getSubject() {
        return subject;
    }

    public EntityRelation setSubject(String subject) {
        this.subject = subject;
        return this;
    }

    public String getRelation() {
        return relation;
    }

    public EntityRelation setRelation(String relation) {
        this.relation = relation;
        return this;
    }

    public String getObject() {
        return object;
    }

    public EntityRelation setObject(String object) {
        this.object = object;
        return this;
    }

    public double getConfidence() {
        return confidence;
    }

    public EntityRelation setConfidence(double confidence) {
        this.confidence = confidence;
        return this;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public EntityRelation setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
        return this;
    }
}
