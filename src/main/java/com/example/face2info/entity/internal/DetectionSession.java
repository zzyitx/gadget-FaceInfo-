package com.example.face2info.entity.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DetectionSession {

    private String detectionId;
    private String previewImage;
    private List<DetectedFace> faces = new ArrayList<>();
    private Instant expiresAt;

    public String getDetectionId() {
        return detectionId;
    }

    public DetectionSession setDetectionId(String detectionId) {
        this.detectionId = detectionId;
        return this;
    }

    public String getPreviewImage() {
        return previewImage;
    }

    public DetectionSession setPreviewImage(String previewImage) {
        this.previewImage = previewImage;
        return this;
    }

    public List<DetectedFace> getFaces() {
        return faces;
    }

    public DetectionSession setFaces(List<DetectedFace> faces) {
        this.faces = faces == null ? new ArrayList<>() : faces;
        return this;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public DetectionSession setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }
}
