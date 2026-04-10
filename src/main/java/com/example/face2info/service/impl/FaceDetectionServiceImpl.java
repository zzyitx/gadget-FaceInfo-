package com.example.face2info.service.impl;

import com.example.face2info.client.FaceDetectionClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.example.face2info.exception.FaceDetectionException;
import com.example.face2info.service.FaceDetectionService;
import com.example.face2info.service.MinioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class FaceDetectionServiceImpl implements FaceDetectionService {

    private static final String MISSING_DETECTION_ID_ERROR = "人脸检测响应缺少 detection_id。";

    private final FaceDetectionClient faceDetectionClient;
    private final SummaryGenerationClient summaryGenerationClient;
    private final TmpfilesClient tmpfilesClient;
    private final MinioService minioService;
    private final ApiProperties properties;
    private final Map<String, DetectionSession> sessions = new ConcurrentHashMap<>();

    public FaceDetectionServiceImpl(FaceDetectionClient faceDetectionClient,
                                    SummaryGenerationClient summaryGenerationClient,
                                    TmpfilesClient tmpfilesClient,
                                    MinioService minioService,
                                    ApiProperties properties) {
        this.faceDetectionClient = faceDetectionClient;
        this.summaryGenerationClient = summaryGenerationClient;
        this.tmpfilesClient = tmpfilesClient;
        this.minioService = minioService;
        this.properties = properties;
    }

    @Override
    public DetectionSession detect(MultipartFile image) {
        purgeExpiredSessions();
        EnhancedImageResult enhancedImageResult = enhanceImageSafely(image);
        DetectionSession session = faceDetectionClient.detect(enhancedImageResult.imageForDetection());
        if (session == null || !StringUtils.hasText(session.getDetectionId())) {
            throw new FaceDetectionException(MISSING_DETECTION_ID_ERROR);
        }
        if (StringUtils.hasText(enhancedImageResult.enhancedImageUrl())) {
            session.setEnhancedImageUrl(enhancedImageResult.enhancedImageUrl());
        }
        session.setExpiresAt(Instant.now().plusSeconds(getSessionTtlSeconds()));
        sessions.put(session.getDetectionId(), session);
        return session;
    }

    @Override
    public SelectedFaceCrop getSelectedFaceCrop(String detectionId, String faceId) {
        DetectionSession session = sessions.get(detectionId);
        if (session == null) {
            throw new FaceDetectionException("未找到检测会话：detection_id=" + detectionId);
        }
        if (isExpired(session)) {
            sessions.remove(detectionId);
            throw new FaceDetectionException("检测会话已过期：detection_id=" + detectionId);
        }
        for (DetectedFace detectedFace : session.getFaces()) {
            if (detectedFace != null && faceId.equals(detectedFace.getFaceId())) {
                SelectedFaceCrop crop = detectedFace.getSelectedFaceCrop();
                if (crop == null || crop.getBytes().length == 0) {
                    throw new FaceDetectionException("所选人脸裁剪图缺失：face_id=" + faceId);
                }
                return crop;
            }
        }
        throw new FaceDetectionException("未找到所选人脸：detection_id=" + detectionId + "，face_id=" + faceId);
    }

    private EnhancedImageResult enhanceImageSafely(MultipartFile image) {
        try {
            String imageUrl = tmpfilesClient.uploadImage(image);
            MultipartFile enhanced = summaryGenerationClient.enhanceFaceImageByUrl(
                    imageUrl,
                    image.getOriginalFilename(),
                    image.getContentType());
            if (enhanced == null || enhanced.isEmpty()) {
                throw new IllegalStateException("enhanced image is empty");
            }
            String filename = StringUtils.hasText(enhanced.getOriginalFilename())
                    ? enhanced.getOriginalFilename()
                    : (StringUtils.hasText(image.getOriginalFilename()) ? image.getOriginalFilename() : "enhanced-face.jpg");
            String contentType = StringUtils.hasText(enhanced.getContentType())
                    ? enhanced.getContentType()
                    : (StringUtils.hasText(image.getContentType()) ? image.getContentType() : "image/jpeg");
            String minioUrl = minioService.upload(enhanced.getBytes(), filename, contentType);
            return new EnhancedImageResult(enhanced, minioUrl);
        } catch (RuntimeException | IOException ex) {
            log.warn("图像高清化失败，检测流程回退原图 fileName={} error={}", image.getOriginalFilename(), ex.getMessage());
            return new EnhancedImageResult(image, null);
        }
    }

    private void purgeExpiredSessions() {
        sessions.entrySet().removeIf(entry -> isExpired(entry.getValue()));
    }

    private boolean isExpired(DetectionSession session) {
        return session != null
                && session.getExpiresAt() != null
                && !Instant.now().isBefore(session.getExpiresAt());
    }

    private long getSessionTtlSeconds() {
        long ttlSeconds = properties.getApi().getFaceDetection().getSessionTtlSeconds();
        return ttlSeconds > 0 ? ttlSeconds : 600L;
    }

    private record EnhancedImageResult(MultipartFile imageForDetection, String enhancedImageUrl) {
    }
}
