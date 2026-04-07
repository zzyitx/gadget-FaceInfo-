package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "多脸待选择响应载荷")
public class FaceSelectionPayload {

    @JsonProperty("detection_id")
    @Schema(description = "检测会话 ID")
    private String detectionId;

    @JsonProperty("preview_image")
    @Schema(description = "带有人脸框的预览图")
    private String previewImage;

    @Schema(description = "检测到的人脸列表")
    private List<DetectedFaceResponse> faces = new ArrayList<>();

    public String getDetectionId() {
        return detectionId;
    }

    public FaceSelectionPayload setDetectionId(String detectionId) {
        this.detectionId = detectionId;
        return this;
    }

    public String getPreviewImage() {
        return previewImage;
    }

    public FaceSelectionPayload setPreviewImage(String previewImage) {
        this.previewImage = previewImage;
        return this;
    }

    public List<DetectedFaceResponse> getFaces() {
        return faces;
    }

    public FaceSelectionPayload setFaces(List<DetectedFaceResponse> faces) {
        this.faces = faces == null ? new ArrayList<>() : faces;
        return this;
    }
}
