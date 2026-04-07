package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "人脸选取请求")
public class FaceSelectionRequest {

    @JsonProperty("detection_id")
    @Schema(description = "检测会话 ID")
    private String detectionId;

    @JsonProperty("face_id")
    @Schema(description = "选中的人脸 ID")
    private String faceId;

    public String getDetectionId() {
        return detectionId;
    }

    public FaceSelectionRequest setDetectionId(String detectionId) {
        this.detectionId = detectionId;
        return this;
    }

    public String getFaceId() {
        return faceId;
    }

    public FaceSelectionRequest setFaceId(String faceId) {
        this.faceId = faceId;
        return this;
    }
}
