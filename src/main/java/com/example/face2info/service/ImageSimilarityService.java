package com.example.face2info.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 计算原图与候选图之间的视觉相似度。
 */
public interface ImageSimilarityService {

    /**
     * @param originalImage 原始上传图
     * @param candidateImageUrl 候选图 URL
     * @param fallbackScore 候选图不可下载或不可解码时的回退分
     * @return 0-100 的相似度分数
     */
    double score(MultipartFile originalImage, String candidateImageUrl, double fallbackScore);
}
