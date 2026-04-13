package com.example.face2info.service;

import com.example.face2info.entity.internal.RecognitionEvidence;
import org.springframework.web.multipart.MultipartFile;

public interface FaceRecognitionService {

    /**
     * 根据上传图片抽取识别证据（候选名、图片匹配、网页证据等）。
     */
    RecognitionEvidence recognize(MultipartFile image);
}
