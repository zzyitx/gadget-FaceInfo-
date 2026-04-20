package com.example.face2info.client;

import com.example.face2info.entity.internal.DetectedFace;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CompreFaceDetectionClient {

    List<DetectedFace> detect(MultipartFile image);
}
