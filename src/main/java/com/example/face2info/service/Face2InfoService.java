package com.example.face2info.service;

import com.example.face2info.entity.response.FaceInfoResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人脸信息聚合总服务。
 * 负责串联图片校验、人名识别和公开信息聚合三个阶段。
 */
public interface Face2InfoService {

    /**
     * 处理上传图片并返回聚合后的公开信息结果。
     *
     * @param image 用户上传的人脸图片
     * @return 聚合后的接口响应
     */
    FaceInfoResponse process(MultipartFile image);
}
