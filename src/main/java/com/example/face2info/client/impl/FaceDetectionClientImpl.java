package com.example.face2info.client.impl;

import com.example.face2info.client.FaceDetectionClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.FaceBoundingBox;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.example.face2info.exception.FaceDetectionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
public class FaceDetectionClientImpl implements FaceDetectionClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApiProperties properties;

    public FaceDetectionClientImpl(RestTemplate restTemplate, ObjectMapper objectMapper, ApiProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public DetectionSession detect(MultipartFile image) {
        String endpoint = properties.getApi().getFaceDetection().getBaseUrl()
                + properties.getApi().getFaceDetection().getDetectPath();
        try {
            LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename();
                }
            });

            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(body, createMultipartHeaders()),
                    String.class);
            return mapSession(response.getBody());
        } catch (IOException | RestClientException ex) {
            log.warn("人脸检测呼叫失败={} message={}", endpoint, ex.getMessage());
            throw new FaceDetectionException("face detection request failed", ex);
        }
    }

    private HttpHeaders createMultipartHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private DetectionSession mapSession(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            DetectionSession session = new DetectionSession()
                    .setDetectionId(root.path("detection_id").asText(""))
                    .setPreviewImage(root.path("preview_image").asText(""));

            List<DetectedFace> faces = new ArrayList<>();
            for (JsonNode faceNode : root.path("faces")) {
                faces.add(mapFace(faceNode));
            }
            session.setFaces(faces);
            if (!StringUtils.hasText(session.getDetectionId())) {
                throw new FaceDetectionException("face detection response missing detection_id");
            }
            return session;
        } catch (IOException ex) {
            throw new FaceDetectionException("face detection response parse failed", ex);
        }
    }

    private DetectedFace mapFace(JsonNode faceNode) {
        String faceId = faceNode.path("face_id").asText("");
        FaceBoundingBox bbox = new FaceBoundingBox()
                .setX(faceNode.path("bbox").path("x").asInt(0))
                .setY(faceNode.path("bbox").path("y").asInt(0))
                .setWidth(faceNode.path("bbox").path("width").asInt(0))
                .setHeight(faceNode.path("bbox").path("height").asInt(0));
        String preview = faceNode.path("crop_preview").asText("");
        String contentType = readContentType(faceNode);
        SelectedFaceCrop crop = new SelectedFaceCrop()
                .setFilename(buildFilename(faceId, contentType))
                .setContentType(contentType)
                .setBytes(decodePreview(preview));
        return new DetectedFace()
                .setFaceId(faceId)
                .setConfidence(faceNode.path("confidence").asDouble(0))
                .setFaceBoundingBox(bbox)
                .setSelectedFaceCrop(crop);
    }

    private String readContentType(JsonNode faceNode) {
        String contentType = faceNode.path("content_type").asText("");
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        return "image/png";
    }

    private String buildFilename(String faceId, String contentType) {
        String baseName = StringUtils.hasText(faceId) ? faceId : "selected-face";
        if ("image/png".equalsIgnoreCase(contentType)) {
            return baseName + ".png";
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return baseName + ".webp";
        }
        return baseName + ".jpg";
    }

    private byte[] decodePreview(String preview) {
        if (!StringUtils.hasText(preview)) {
            return new byte[0];
        }
        String payload = preview;
        if (preview.startsWith("data:")) {
            int commaIndex = preview.indexOf(',');
            if (commaIndex >= 0 && commaIndex + 1 < preview.length()) {
                payload = preview.substring(commaIndex + 1);
            }
        }
        try {
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException ex) {
            return payload.getBytes(StandardCharsets.UTF_8);
        }
    }
}
