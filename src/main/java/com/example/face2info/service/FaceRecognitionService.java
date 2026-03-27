package com.example.face2info.service;

import com.example.face2info.entity.internal.RecognitionEvidence;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人脸识别服务。
 */
public interface FaceRecognitionService {

    /**
     * 根据上传图片提取多信源识别证据。
     *
     * @param image 上传图片
     * @return 识别证据
     */
    RecognitionEvidence recognize(MultipartFile image);
}
