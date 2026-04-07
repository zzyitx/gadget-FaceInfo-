package com.example.face2info.service;

import com.example.face2info.entity.response.FaceInfoResponse;
import org.springframework.web.multipart.MultipartFile;

public interface Face2InfoService {

    FaceInfoResponse process(MultipartFile image);

    FaceInfoResponse processSelectedFace(String detectionId, String faceId);
}
