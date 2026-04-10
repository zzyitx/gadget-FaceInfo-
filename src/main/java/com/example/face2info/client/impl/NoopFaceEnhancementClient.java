package com.example.face2info.client.impl;

import com.example.face2info.client.FaceEnhancementClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "face2info.api.face-enhance", name = "provider", havingValue = "noop", matchIfMissing = true)
public class NoopFaceEnhancementClient implements FaceEnhancementClient {

    @Override
    public MultipartFile enhanceFaceImageByUrl(String imageUrl, MultipartFile originalImage) {
        log.warn("当前使用 NoopFaceEnhancementClient，未调用任何高清化模型");
        return originalImage;
    }
}
