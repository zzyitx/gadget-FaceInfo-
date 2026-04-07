package com.example.face2info.entity.internal;

public class DetectedFace {

    private String faceId;
    private double confidence;
    private FaceBoundingBox faceBoundingBox;
    private SelectedFaceCrop selectedFaceCrop;

    public String getFaceId() {
        return faceId;
    }

    public DetectedFace setFaceId(String faceId) {
        this.faceId = faceId;
        return this;
    }

    public double getConfidence() {
        return confidence;
    }

    public DetectedFace setConfidence(double confidence) {
        this.confidence = confidence;
        return this;
    }

    public FaceBoundingBox getFaceBoundingBox() {
        return faceBoundingBox;
    }

    public DetectedFace setFaceBoundingBox(FaceBoundingBox faceBoundingBox) {
        this.faceBoundingBox = faceBoundingBox;
        return this;
    }

    public SelectedFaceCrop getSelectedFaceCrop() {
        return selectedFaceCrop;
    }

    public DetectedFace setSelectedFaceCrop(SelectedFaceCrop selectedFaceCrop) {
        this.selectedFaceCrop = selectedFaceCrop;
        return this;
    }
}
