package com.example.face2info.client;

import com.example.face2info.entity.internal.DetectionSession;
import org.springframework.web.multipart.MultipartFile;

public interface FaceDetectionClient {

    DetectionSession detect(MultipartFile image);
}
