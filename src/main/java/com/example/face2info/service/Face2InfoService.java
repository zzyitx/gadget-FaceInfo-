package com.example.face2info.service;

import com.example.face2info.entity.response.FaceInfoResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Face2Info 总服务抽象。
 */
public interface Face2InfoService {

    FaceInfoResponse process(MultipartFile image);
}
