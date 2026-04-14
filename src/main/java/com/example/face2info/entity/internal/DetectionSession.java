package com.example.face2info.entity.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DetectionSession {

    private String detectionId;
    private String previewImage;
    private String enhancedImageUrl;
    private String uploadedImageUrl;
    private boolean enhancementApplied;
    private String enhancementWarning;
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

    public String getEnhancedImageUrl() {
        return enhancedImageUrl;
    }

    public DetectionSession setEnhancedImageUrl(String enhancedImageUrl) {
        this.enhancedImageUrl = enhancedImageUrl;
        return this;
    }

    public String getUploadedImageUrl() {
        return uploadedImageUrl;
    }

    public DetectionSession setUploadedImageUrl(String uploadedImageUrl) {
        this.uploadedImageUrl = uploadedImageUrl;
        return this;
    }

    public boolean isEnhancementApplied() {
        return enhancementApplied;
    }

    public DetectionSession setEnhancementApplied(boolean enhancementApplied) {
        this.enhancementApplied = enhancementApplied;
        return this;
    }

    public String getEnhancementWarning() {
        return enhancementWarning;
    }

    public DetectionSession setEnhancementWarning(String enhancementWarning) {
        this.enhancementWarning = enhancementWarning;
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
