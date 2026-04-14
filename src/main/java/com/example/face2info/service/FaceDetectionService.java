package com.example.face2info.service;

import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.PreparedImageResult;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import org.springframework.web.multipart.MultipartFile;

public interface FaceDetectionService {

    DetectionSession detect(MultipartFile image);

    default DetectionSession detect(PreparedImageResult preparedImageResult) {
        if (preparedImageResult == null) {
            throw new IllegalArgumentException("preparedImageResult 不能为空");
        }
        return detect(preparedImageResult.getWorkingImage());
    }

    SelectedFaceCrop getSelectedFaceCrop(String detectionId, String faceId);
}
