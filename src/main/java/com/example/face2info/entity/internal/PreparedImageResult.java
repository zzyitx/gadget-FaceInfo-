package com.example.face2info.entity.internal;

import org.springframework.web.multipart.MultipartFile;

public class PreparedImageResult {

    private MultipartFile originalImage;
    private MultipartFile workingImage;
    private String uploadedImageUrl;
    private boolean enhancementApplied;
    private String warning;
    private String debugMessage;

    public MultipartFile getOriginalImage() {
        return originalImage;
    }

    public PreparedImageResult setOriginalImage(MultipartFile originalImage) {
        this.originalImage = originalImage;
        return this;
    }

    public MultipartFile getWorkingImage() {
        return workingImage;
    }

    public PreparedImageResult setWorkingImage(MultipartFile workingImage) {
        this.workingImage = workingImage;
        return this;
    }

    public String getUploadedImageUrl() {
        return uploadedImageUrl;
    }

    public PreparedImageResult setUploadedImageUrl(String uploadedImageUrl) {
        this.uploadedImageUrl = uploadedImageUrl;
        return this;
    }

    public boolean isEnhancementApplied() {
        return enhancementApplied;
    }

    public PreparedImageResult setEnhancementApplied(boolean enhancementApplied) {
        this.enhancementApplied = enhancementApplied;
        return this;
    }

    public String getWarning() {
        return warning;
    }

    public PreparedImageResult setWarning(String warning) {
        this.warning = warning;
        return this;
    }

    public String getDebugMessage() {
        return debugMessage;
    }

    public PreparedImageResult setDebugMessage(String debugMessage) {
        this.debugMessage = debugMessage;
        return this;
    }
}
