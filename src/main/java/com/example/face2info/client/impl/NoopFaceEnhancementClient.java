package com.example.face2info.client.impl;

import com.example.face2info.client.FaceEnhancementClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(prefix = "face2info.api.face-enhance", name = "provider", havingValue = "noop", matchIfMissing = true)
public class NoopFaceEnhancementClient implements FaceEnhancementClient {

    @Override
    public MultipartFile enhanceFaceImageByUrl(String imageUrl, MultipartFile originalImage) {
        return originalImage;
    }
}
