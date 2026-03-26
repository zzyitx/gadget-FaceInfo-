package com.example.face2info.service;

import com.example.face2info.entity.internal.RecognitionCandidate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人脸识别服务。
 * 当前实现并不做人脸特征计算，而是通过反向搜图推断候选人物。
 */
public interface FaceRecognitionService {

    /**
     * 根据上传图片识别候选人物名称。
     *
     * @param image 上传图片
     * @return 识别候选结果，包含名称、置信度和图片匹配列表
     */
    RecognitionCandidate recognize(MultipartFile image);
}
