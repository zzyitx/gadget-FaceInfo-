package com.example.face2info.service.impl;

import com.example.face2info.client.FaceDetectionClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.example.face2info.exception.FaceDetectionException;
import com.example.face2info.service.FaceDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class FaceDetectionServiceImpl implements FaceDetectionService {

    private final FaceDetectionClient faceDetectionClient;
    private final ApiProperties properties;
    private final Map<String, DetectionSession> sessions = new ConcurrentHashMap<>();

    public FaceDetectionServiceImpl(FaceDetectionClient faceDetectionClient, ApiProperties properties) {
        this.faceDetectionClient = faceDetectionClient;
        this.properties = properties;
    }

    @Override
    public DetectionSession detect(MultipartFile image) {
        purgeExpiredSessions();
        DetectionSession session = faceDetectionClient.detect(image);
        if (session == null || !StringUtils.hasText(session.getDetectionId())) {
            throw new FaceDetectionException("face detection response missing detection_id");
        }
        session.setExpiresAt(Instant.now().plusSeconds(getSessionTtlSeconds()));
        sessions.put(session.getDetectionId(), session);
        return session;
    }

    @Override
    public SelectedFaceCrop getSelectedFaceCrop(String detectionId, String faceId) {
        DetectionSession session = sessions.get(detectionId);
        if (session == null) {
            throw new FaceDetectionException("Detection session not found: detection_id=" + detectionId);
        }
        if (isExpired(session)) {
            sessions.remove(detectionId);
            throw new FaceDetectionException("Detection session expired: detection_id=" + detectionId);
        }
        for (DetectedFace detectedFace : session.getFaces()) {
            if (detectedFace != null && faceId.equals(detectedFace.getFaceId())) {
                SelectedFaceCrop crop = detectedFace.getSelectedFaceCrop();
                if (crop == null || crop.getBytes().length == 0) {
                    throw new FaceDetectionException("Selected face crop is missing: face_id=" + faceId);
                }
                return crop;
            }
        }
        throw new FaceDetectionException("Selected face not found: detection_id=" + detectionId + ", face_id=" + faceId);
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
}
