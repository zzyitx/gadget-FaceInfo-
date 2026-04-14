package com.example.face2info.service.impl;

import com.example.face2info.client.FaceEnhancementClient;
import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.PreparedImageResult;
import com.example.face2info.service.EnhancedImagePreparationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class EnhancedImagePreparationServiceImpl implements EnhancedImagePreparationService {

    static final String ENHANCEMENT_WARNING = "图片高清化失败，已自动回退原图继续处理。";
    private static final String PROVIDER_REPLICATE = "replicate";

    private final FaceEnhancementClient faceEnhancementClient;
    private final TmpfilesClient tmpfilesClient;
    private final ApiProperties properties;

    @Autowired
    public EnhancedImagePreparationServiceImpl(FaceEnhancementClient faceEnhancementClient,
                                               TmpfilesClient tmpfilesClient,
                                               ApiProperties properties) {
        this.faceEnhancementClient = faceEnhancementClient;
        this.tmpfilesClient = tmpfilesClient;
        this.properties = properties;
    }

    EnhancedImagePreparationServiceImpl(FaceEnhancementClient faceEnhancementClient,
                                        TmpfilesClient tmpfilesClient) {
        this(faceEnhancementClient, tmpfilesClient, new ApiProperties());
    }

    @Override
    public PreparedImageResult prepare(MultipartFile originalImage) {
        try {
            // 统一在入口产出“最终工作图”，后续检测、识别、聚合都只使用这一份结果。
            MultipartFile enhancedImage = enhance(originalImage);
            String uploadedImageUrl = tmpfilesClient.uploadImage(enhancedImage);
            return new PreparedImageResult()
                    .setOriginalImage(originalImage)
                    .setWorkingImage(enhancedImage)
                    .setUploadedImageUrl(uploadedImageUrl)
                    .setEnhancementApplied(!sameInstanceOrName(originalImage, enhancedImage));
        } catch (RuntimeException ex) {
            // 高清化失败只记 warning，不中断主流程；前端通过 warning 感知降级。
            log.warn("图片高清化失败，回退原图 fileName={} error={}", originalImage == null ? null : originalImage.getOriginalFilename(), ex.getMessage());
            String uploadedImageUrl = tmpfilesClient.uploadImage(originalImage);
            return new PreparedImageResult()
                    .setOriginalImage(originalImage)
                    .setWorkingImage(originalImage)
                    .setUploadedImageUrl(uploadedImageUrl)
                    .setEnhancementApplied(false)
                    .setWarning(ENHANCEMENT_WARNING)
                    .setDebugMessage(ex.getMessage());
        }
    }

    private MultipartFile enhance(MultipartFile originalImage) {
        if (PROVIDER_REPLICATE.equalsIgnoreCase(properties.getApi().getFaceEnhance().getProvider())) {
            // Replicate 仍依赖公网可访问 URL，因此这里单独保留“先上传再增强”的兼容路径。
            String sourceImageUrl = tmpfilesClient.uploadImage(originalImage);
            return faceEnhancementClient.enhanceFaceImageByUrl(sourceImageUrl, originalImage);
        }
        return faceEnhancementClient.enhanceFaceImage(originalImage);
    }

    private boolean sameInstanceOrName(MultipartFile originalImage, MultipartFile enhancedImage) {
        if (originalImage == enhancedImage) {
            return true;
        }
        if (originalImage == null || enhancedImage == null) {
            return false;
        }
        return StringUtils.hasText(originalImage.getOriginalFilename())
                && originalImage.getOriginalFilename().equals(enhancedImage.getOriginalFilename());
    }
}
