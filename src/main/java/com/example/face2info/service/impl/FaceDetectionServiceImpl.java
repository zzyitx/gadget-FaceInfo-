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

    private static final double CONTEXT_EXPAND_LEFT_RIGHT = 0.6D;
    private static final double CONTEXT_EXPAND_TOP = 0.35D;
    private static final double CONTEXT_EXPAND_BOTTOM = 1.6D;
    private static final double EXCLUSION_FACE_MARGIN = 0.25D;

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
            List<DetectedFace> validDetectedFaces = new ArrayList<>();
            List<FaceBoundingBox> normalizedFaces = new ArrayList<>();
            for (DetectedFace detectedFace : detectedFaces) {
                if (detectedFace == null || detectedFace.getFaceBoundingBox() == null) {
                    continue;
                }
                validDetectedFaces.add(detectedFace);
                normalizedFaces.add(normalizeBoundingBox(detectedFace.getFaceBoundingBox(), source));
            }
            List<DetectedFace> sessionFaces = new ArrayList<>();
            for (int index = 0; index < normalizedFaces.size(); index++) {
                DetectedFace detectedFace = validDetectedFaces.get(index);
                FaceBoundingBox normalized = normalizedFaces.get(index);
                SelectedFaceCrop crop = cropFace(source, normalized, normalizedFaces, detectionId + "-face-" + (index + 1) + ".png");
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

    private SelectedFaceCrop cropFace(BufferedImage source,
                                      FaceBoundingBox boundingBox,
                                      List<FaceBoundingBox> allFaces,
                                      String filename) throws IOException {
        FaceBoundingBox cropBox = expandContextBoundingBox(boundingBox, allFaces, source);
        BufferedImage cropped = source.getSubimage(
                cropBox.getX(),
                cropBox.getY(),
                cropBox.getWidth(),
                cropBox.getHeight()
        );
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(cropped, "png", outputStream);
        return new SelectedFaceCrop()
                .setFilename(filename)
                .setContentType("image/png")
                .setBytes(outputStream.toByteArray());
    }

    private FaceBoundingBox expandContextBoundingBox(FaceBoundingBox target,
                                                     List<FaceBoundingBox> allFaces,
                                                     BufferedImage source) {
        int left = (int) Math.floor(target.getX() - target.getWidth() * CONTEXT_EXPAND_LEFT_RIGHT);
        int right = (int) Math.ceil(target.getX() + target.getWidth() * (1.0D + CONTEXT_EXPAND_LEFT_RIGHT));
        int top = (int) Math.floor(target.getY() - target.getHeight() * CONTEXT_EXPAND_TOP);
        int bottom = (int) Math.ceil(target.getY() + target.getHeight() * (1.0D + CONTEXT_EXPAND_BOTTOM));

        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(source.getWidth(), right);
        bottom = Math.min(source.getHeight(), bottom);

        if (allFaces != null) {
            for (FaceBoundingBox other : allFaces) {
                if (other == null || sameBox(target, other)) {
                    continue;
                }
                FaceBoundingBox exclusion = expandExclusionBox(other, source);
                if (!intersects(left, top, right, bottom, exclusion)) {
                    continue;
                }
                if (exclusion.getX() >= target.getX() + target.getWidth()) {
                    right = Math.min(right, exclusion.getX());
                } else if (exclusion.getX() + exclusion.getWidth() <= target.getX()) {
                    left = Math.max(left, exclusion.getX() + exclusion.getWidth());
                }
                if (exclusion.getY() >= target.getY() + target.getHeight()) {
                    bottom = Math.min(bottom, exclusion.getY());
                } else if (exclusion.getY() + exclusion.getHeight() <= target.getY()) {
                    top = Math.max(top, exclusion.getY() + exclusion.getHeight());
                }
            }
        }

        left = Math.min(left, target.getX());
        top = Math.min(top, target.getY());
        right = Math.max(right, target.getX() + target.getWidth());
        bottom = Math.max(bottom, target.getY() + target.getHeight());

        return new FaceBoundingBox()
                .setX(left)
                .setY(top)
                .setWidth(Math.max(1, right - left))
                .setHeight(Math.max(1, bottom - top));
    }

    private FaceBoundingBox expandExclusionBox(FaceBoundingBox box, BufferedImage source) {
        int marginX = (int) Math.ceil(box.getWidth() * EXCLUSION_FACE_MARGIN);
        int marginY = (int) Math.ceil(box.getHeight() * EXCLUSION_FACE_MARGIN);
        int x = Math.max(0, box.getX() - marginX);
        int y = Math.max(0, box.getY() - marginY);
        int right = Math.min(source.getWidth(), box.getX() + box.getWidth() + marginX);
        int bottom = Math.min(source.getHeight(), box.getY() + box.getHeight() + marginY);
        return new FaceBoundingBox()
                .setX(x)
                .setY(y)
                .setWidth(Math.max(1, right - x))
                .setHeight(Math.max(1, bottom - y));
    }

    private boolean intersects(int left, int top, int right, int bottom, FaceBoundingBox box) {
        return left < box.getX() + box.getWidth()
                && right > box.getX()
                && top < box.getY() + box.getHeight()
                && bottom > box.getY();
    }

    private boolean sameBox(FaceBoundingBox left, FaceBoundingBox right) {
        return left.getX() == right.getX()
                && left.getY() == right.getY()
                && left.getWidth() == right.getWidth()
                && left.getHeight() == right.getHeight();
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
