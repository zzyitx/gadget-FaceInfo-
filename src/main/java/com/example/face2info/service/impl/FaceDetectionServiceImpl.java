package com.example.face2info.service.impl;

import com.example.face2info.client.CompreFaceDetectionClient;
import com.example.face2info.client.FaceEnhancementClient;
import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.FaceBoundingBox;
import com.example.face2info.entity.internal.PreparedImageResult;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.example.face2info.exception.FaceDetectionException;
import com.example.face2info.service.FaceDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

@Slf4j
@Service
public class FaceDetectionServiceImpl implements FaceDetectionService {

    private static final String MISSING_DETECTION_RESULT_ERROR = "CompreFace detection 未返回有效人脸结果";

    private final CompreFaceDetectionClient faceDetectionClient;
    private final FaceEnhancementClient faceEnhancementClient;
    private final TmpfilesClient tmpfilesClient;
    private final ApiProperties properties;
    private final Map<String, DetectionSession> sessions = new ConcurrentHashMap<>();

    @Autowired
    public FaceDetectionServiceImpl(CompreFaceDetectionClient faceDetectionClient,
                                    FaceEnhancementClient faceEnhancementClient,
                                    TmpfilesClient tmpfilesClient,
                                    ApiProperties properties) {
        this.faceDetectionClient = faceDetectionClient;
        this.faceEnhancementClient = faceEnhancementClient;
        this.tmpfilesClient = tmpfilesClient;
        this.properties = properties;
    }

    FaceDetectionServiceImpl(CompreFaceDetectionClient faceDetectionClient,
                             ApiProperties properties) {
        this(faceDetectionClient, null, null, properties);
    }

    @Override
    public DetectionSession detect(MultipartFile image) {
        return detectInternal(image);
    }

    @Override
    public DetectionSession detect(PreparedImageResult preparedImageResult) {
        purgeExpiredSessions();
        if (preparedImageResult == null || preparedImageResult.getWorkingImage() == null) {
            throw new FaceDetectionException("检测输入不能为空");
        }
        DetectionSession session = detectWithConfiguredClient(preparedImageResult.getWorkingImage());
        session.setUploadedImageUrl(preparedImageResult.getUploadedImageUrl());
        session.setEnhancementApplied(preparedImageResult.isEnhancementApplied());
        session.setEnhancementWarning(preparedImageResult.getWarning());
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

    private DetectionSession detectInternal(MultipartFile image) {
        // 检测前先清理过期会话，避免内存会话长期堆积。
        purgeExpiredSessions();
        EnhancedImageResult enhancedImageResult = enhanceImageSafely(image);
        DetectionSession session = detectWithConfiguredClient(enhancedImageResult.imageForDetection());
        if (StringUtils.hasText(enhancedImageResult.enhancedImageUrl())) {
            session.setEnhancedImageUrl(enhancedImageResult.enhancedImageUrl());
        }
        session.setExpiresAt(Instant.now().plusSeconds(getSessionTtlSeconds()));
        sessions.put(session.getDetectionId(), session);
        return session;
    }

    private EnhancedImageResult enhanceImageSafely(MultipartFile image) {
        try {
            // 增强流程依赖外部可访问 URL，因此先把图片上传到临时图床。
            String enhancementSourceUrl = tmpfilesClient.uploadImage(image);
            MultipartFile enhanced = faceEnhancementClient.enhanceFaceImageByUrl(enhancementSourceUrl, image);
            if (enhanced == null || enhanced.isEmpty()) {
                throw new IllegalStateException("enhanced image is empty");
            }
            return new EnhancedImageResult(enhanced, toDataUrl(enhanced, image.getContentType()));
        } catch (RuntimeException | IOException ex) {
            log.warn("图像高清化失败，检测流程回退原图 fileName={} error={}", image.getOriginalFilename(), ex.getMessage());
            return new EnhancedImageResult(image, null);
        }
    }

    private DetectionSession buildSession(MultipartFile image) {
        try {
            List<DetectedFace> detectedFaces = faceDetectionClient.detect(image);
            if (detectedFaces == null || detectedFaces.isEmpty()) {
                throw new FaceDetectionException(MISSING_DETECTION_RESULT_ERROR);
            }
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(image.getBytes()));
            if (source == null) {
                throw new FaceDetectionException("检测输入不是有效图片。");
            }
            // CompreFace 只返回框信息，这里统一补全 detectionId / faceId / 裁剪图，保持后续会话结构不变。
            String detectionId = UUID.randomUUID().toString();
            List<DetectedFace> sessionFaces = new ArrayList<>();
            for (int index = 0; index < detectedFaces.size(); index++) {
                DetectedFace detectedFace = detectedFaces.get(index);
                if (detectedFace == null || detectedFace.getFaceBoundingBox() == null) {
                    continue;
                }
                FaceBoundingBox normalized = normalizeBoundingBox(detectedFace.getFaceBoundingBox(), source);
                // 保护裁剪边界，避免第三方返回越界框时直接裁剪失败。
                SelectedFaceCrop crop = cropFace(source, normalized, detectionId + "-face-" + (index + 1) + ".png");
                sessionFaces.add(new DetectedFace()
                        .setFaceId(detectionId + "-face-" + (index + 1))
                        .setConfidence(detectedFace.getConfidence())
                        .setFaceBoundingBox(normalized)
                        .setSelectedFaceCrop(crop));
            }
            if (sessionFaces.isEmpty()) {
                throw new FaceDetectionException(MISSING_DETECTION_RESULT_ERROR);
            }
            return new DetectionSession()
                    .setDetectionId(detectionId)
                    .setPreviewImage(toDataUrl(image, image.getContentType()))
                    .setFaces(sessionFaces);
        } catch (IOException ex) {
            throw new FaceDetectionException("构建检测会话失败。", ex);
        }
    }

    private DetectionSession detectWithConfiguredClient(MultipartFile image) {
        DetectionSession session = isComprefaceEnabled()
                ? buildSession(image)
                : detectWithoutCompreface(image);
        if (session == null || session.getFaces() == null || session.getFaces().isEmpty()) {
            throw new FaceDetectionException(MISSING_DETECTION_RESULT_ERROR);
        }
        return session;
    }

    private DetectionSession detectWithoutCompreface(MultipartFile image) {
        try {
            // 本地禁用 CompreFace 时仍复用后续选脸流程，把整张图作为唯一候选脸。
            String detectionId = UUID.randomUUID().toString();
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(image.getBytes()));
            FaceBoundingBox boundingBox = source == null
                    ? new FaceBoundingBox().setX(0).setY(0).setWidth(0).setHeight(0)
                    : new FaceBoundingBox().setX(0).setY(0).setWidth(source.getWidth()).setHeight(source.getHeight());
            SelectedFaceCrop crop = new SelectedFaceCrop()
                    .setFilename(resolveBypassFilename(image))
                    .setContentType(resolveContentType(image))
                    .setBytes(image.getBytes());
            DetectedFace face = new DetectedFace()
                    .setFaceId(detectionId + "-face-1")
                    .setConfidence(1.0D)
                    .setFaceBoundingBox(boundingBox)
                    .setSelectedFaceCrop(crop);
            return new DetectionSession()
                    .setDetectionId(detectionId)
                    .setPreviewImage(toDataUrl(image, image.getContentType()))
                    .setFaces(List.of(face));
        } catch (IOException ex) {
            throw new FaceDetectionException("跳过人脸检测时构建会话失败。", ex);
        }
    }

    private boolean isComprefaceEnabled() {
        return properties == null
                || properties.getApi() == null
                || properties.getApi().getCompreface() == null
                || properties.getApi().getCompreface().isEnabled();
    }

    private String resolveContentType(MultipartFile image) {
        return StringUtils.hasText(image.getContentType()) ? image.getContentType() : "image/jpeg";
    }

    private String resolveBypassFilename(MultipartFile image) {
        if (StringUtils.hasText(image.getOriginalFilename())) {
            return image.getOriginalFilename();
        }
        return "face-detection-skipped.jpg";
    }

    private FaceBoundingBox normalizeBoundingBox(FaceBoundingBox sourceBox, BufferedImage image) {
        // 第三方框可能越界，先做裁剪保护，避免 getSubimage 抛 RasterFormatException。
        int safeX = Math.max(0, sourceBox.getX());
        int safeY = Math.max(0, sourceBox.getY());
        int maxWidth = Math.max(0, image.getWidth() - safeX);
        int maxHeight = Math.max(0, image.getHeight() - safeY);
        int safeWidth = Math.max(1, Math.min(sourceBox.getWidth(), maxWidth));
        int safeHeight = Math.max(1, Math.min(sourceBox.getHeight(), maxHeight));
        return new FaceBoundingBox()
                .setX(safeX)
                .setY(safeY)
                .setWidth(safeWidth)
                .setHeight(safeHeight);
    }

    private SelectedFaceCrop cropFace(BufferedImage source, FaceBoundingBox boundingBox, String filename) throws IOException {
        BufferedImage cropped = source.getSubimage(
                boundingBox.getX(),
                boundingBox.getY(),
                boundingBox.getWidth(),
                boundingBox.getHeight()
        );
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(cropped, "png", outputStream);
        return new SelectedFaceCrop()
                .setFilename(filename)
                .setContentType("image/png")
                .setBytes(outputStream.toByteArray());
    }

    private String toDataUrl(MultipartFile image, String fallbackContentType) throws IOException {
        String contentType = StringUtils.hasText(image.getContentType()) ? image.getContentType() : fallbackContentType;
        if (!StringUtils.hasText(contentType)) {
            contentType = "image/jpeg";
        }
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(image.getBytes());
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
        long ttlSeconds = properties.getApi().getCompreface().getSessionTtlSeconds();
        return ttlSeconds > 0 ? ttlSeconds : 600L;
    }

    private record EnhancedImageResult(MultipartFile imageForDetection, String enhancedImageUrl) {
    }
}
