package com.example.face2info.client;

import com.example.face2info.entity.internal.FaceCheckSearchResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FaceCheckClient {

    FaceCheckSearchResponse search(MultipartFile image);
}
