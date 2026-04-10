package com.example.face2info.client;

import org.springframework.web.multipart.MultipartFile;

public interface FaceEnhancementClient {

    MultipartFile enhanceFaceImageByUrl(String imageUrl, MultipartFile originalImage);
}
