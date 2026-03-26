package com.example.face2info.client.impl;

import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.exception.ApiCallException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * tempfile.org 临时文件上传客户端实现。
 * 负责上传图片并将返回结果转换为可访问的预览 URL。
 */
@Slf4j
@Component
public class TmpfilesClientImpl implements TmpfilesClient {

    private static final String UPLOAD_URL = "https://tempfile.org/api/upload/local";
    private static final String PREVIEW_URL_TEMPLATE = "https://tempfile.org/%s/preview";
    private static final int EXPIRY_HOURS = 24;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 上传本地文件对象并返回预览地址。
     */
    @Override
    public String uploadImage(File image) {
        log.info("Uploading image to tempfile.org...");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("files", new FileSystemResource(image));
            body.add("expiryHours", String.valueOf(EXPIRY_HOURS));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(UPLOAD_URL, requestEntity, String.class);
            log.info("tempfile.org response: {}", response.getBody());
            return extractPreviewUrl(response.getBody());
        } catch (IOException e) {
            throw new ApiCallException("Image upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * 上传 MultipartFile 并返回预览地址。
     */
    @Override
    public String uploadImage(MultipartFile image) {
        log.info("Uploading image to tempfile.org...");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("files", createResource(image));
            body.add("expiryHours", String.valueOf(EXPIRY_HOURS));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(UPLOAD_URL, requestEntity, String.class);
            log.info("tempfile.org response: {}", response.getBody());
            return extractPreviewUrl(response.getBody());
        } catch (IOException e) {
            throw new ApiCallException("Image upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * 从上传响应中提取预览地址。
     */
    private String extractPreviewUrl(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode firstFile = root.path("files").path(0);
        boolean success = root.path("success").asBoolean(false);
        String fileId = firstFile.path("id").asText(null);

        if (!success || fileId == null || fileId.isBlank()) {
            throw new ApiCallException("tempfile.org returned unexpected payload: " + responseBody);
        }

        String previewUrl = PREVIEW_URL_TEMPLATE.formatted(fileId);
        log.info("Image uploaded successfully, preview URL: {}", previewUrl);
        return previewUrl;
    }

    /**
     * 将 MultipartFile 转为 RestTemplate 可上传资源。
     */
    private Resource createResource(MultipartFile image) throws IOException {
        String originalFilename = image.getOriginalFilename();
        String filename = (originalFilename != null) ? originalFilename : "image-" + UUID.randomUUID() + ".jpg";
        return new ByteArrayResource(image.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
