package com.example.face2info.entity.response;

import com.example.face2info.entity.internal.FaceBoundingBox;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "检测到的人脸")
public class DetectedFaceResponse {

    @JsonProperty("face_id")
    @Schema(description = "人脸 ID")
    private String faceId;

    @Schema(description = "置信度")
    private double confidence;

    @JsonProperty("crop_preview")
    @Schema(description = "裁剪预览图")
    private String cropPreview;

    @Schema(description = "人脸边界框")
    private FaceBoundingBox bbox;

    public String getFaceId() {
        return faceId;
    }

    public DetectedFaceResponse setFaceId(String faceId) {
        this.faceId = faceId;
        return this;
    }

    public double getConfidence() {
        return confidence;
    }

    public DetectedFaceResponse setConfidence(double confidence) {
        this.confidence = confidence;
        return this;
    }

    public String getCropPreview() {
        return cropPreview;
    }

    public DetectedFaceResponse setCropPreview(String cropPreview) {
        this.cropPreview = cropPreview;
        return this;
    }

    public FaceBoundingBox getBbox() {
        return bbox;
    }

    public DetectedFaceResponse setBbox(FaceBoundingBox bbox) {
        this.bbox = bbox;
        return this;
    }
}
