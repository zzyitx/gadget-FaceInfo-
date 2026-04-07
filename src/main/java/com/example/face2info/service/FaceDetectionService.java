package com.example.face2info.service;

import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import org.springframework.web.multipart.MultipartFile;

public interface FaceDetectionService {

    DetectionSession detect(MultipartFile image);

    SelectedFaceCrop getSelectedFaceCrop(String detectionId, String faceId);
}
