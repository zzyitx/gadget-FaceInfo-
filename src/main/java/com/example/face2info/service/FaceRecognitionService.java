package com.example.face2info.service;

import com.example.face2info.entity.internal.RecognitionCandidate;
import org.springframework.web.multipart.MultipartFile;

public interface FaceRecognitionService {

    RecognitionCandidate recognize(MultipartFile image);
}
