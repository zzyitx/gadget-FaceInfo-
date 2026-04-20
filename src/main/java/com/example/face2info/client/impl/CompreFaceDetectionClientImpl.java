package com.example.face2info.client.impl;

import com.example.face2info.client.CompreFaceDetectionClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.FaceBoundingBox;
import com.example.face2info.exception.ApiCallException;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CompreFaceDetectionClientImpl implements CompreFaceDetectionClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApiProperties properties;

    public CompreFaceDetectionClientImpl(RestTemplate restTemplate, ObjectMapper objectMapper, ApiProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<DetectedFace> detect(MultipartFile image) {
        String endpoint = buildEndpoint();
        try {
            LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename();
                }
            });

            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(body, createHeaders()),
                    String.class
            );
            return mapFaces(response.getBody());
        } catch (ResourceAccessException ex) {
            log.warn("CompreFace detection 服务不可用 endpoint={} message={}", endpoint, ex.getMessage());
            throw new ApiCallException("CompreFace detection 服务不可用：" + endpoint, ex);
        } catch (IOException | RestClientException ex) {
            log.warn("CompreFace detection 调用失败 endpoint={} message={}", endpoint, ex.getMessage());
            throw new FaceDetectionException("CompreFace detection 请求失败。", ex);
        }
    }

    private String buildEndpoint() {
        ApiProperties.Api api = properties.getApi();
        return UriComponentsBuilder
                .fromHttpUrl(api.getCompreface().getBaseUrl() + api.getCompreface().getDetection().getPath())
                .queryParam("limit", api.getCompreface().getDetection().getLimit())
                .queryParam("det_prob_threshold", api.getCompreface().getDetection().getDetProbThreshold())
                .queryParam("status", api.getCompreface().getDetection().isStatus())
                .toUriString();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String apiKey = properties.getApi().getCompreface().getDetection().getApiKey();
        if (StringUtils.hasText(apiKey)) {
            headers.set("x-api-key", apiKey);
        }
        return headers;
    }

    private List<DetectedFace> mapFaces(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            List<DetectedFace> detectedFaces = new ArrayList<>();
            for (JsonNode resultNode : root.path("result")) {
                JsonNode box = resultNode.path("box");
                int xMin = box.path("x_min").asInt(0);
                int yMin = box.path("y_min").asInt(0);
                int xMax = box.path("x_max").asInt(xMin);
                int yMax = box.path("y_max").asInt(yMin);
                // CompreFace 盒子是左上/右下坐标，这里转换成系统统一的 x/y/width/height。
                detectedFaces.add(new DetectedFace()
                        .setConfidence(resultNode.path("probability").asDouble(0.0D))
                        .setFaceBoundingBox(new FaceBoundingBox()
                                .setX(xMin)
                                .setY(yMin)
                                .setWidth(Math.max(0, xMax - xMin))
                                .setHeight(Math.max(0, yMax - yMin))));
            }
            return detectedFaces;
        } catch (IOException ex) {
            throw new FaceDetectionException("CompreFace detection 响应解析失败。", ex);
        }
    }
}
