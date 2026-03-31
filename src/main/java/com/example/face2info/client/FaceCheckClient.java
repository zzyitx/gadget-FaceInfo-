package com.example.face2info.client;

import com.example.face2info.entity.internal.FaceCheckUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FaceCheckClient {

    FaceCheckUploadResponse upload(MultipartFile image);
}
