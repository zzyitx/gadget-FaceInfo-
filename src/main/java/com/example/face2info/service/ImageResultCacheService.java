package com.example.face2info.service;

import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.response.FaceInfoResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ImageResultCacheService {

    /**
     * 读取识别阶段缓存（候选名、图像匹配、网页证据）。
     */
    RecognitionEvidence getRecognitionEvidence(MultipartFile image);

    /**
     * 写入识别阶段缓存，供后续同图请求复用。
     */
    void cacheRecognitionEvidence(MultipartFile image, RecognitionEvidence evidence);

    /**
     * 读取最终聚合响应缓存（对外 API 响应结构）。
     */
    FaceInfoResponse getFaceInfoResponse(MultipartFile image);

    /**
     * 写入最终聚合响应缓存。
     */
    void cacheFaceInfoResponse(MultipartFile image, FaceInfoResponse response);
}
