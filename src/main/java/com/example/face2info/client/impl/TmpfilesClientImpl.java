package com.example.face2info.client.impl;

import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.service.MinioService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

@Slf4j
@Component
public class TmpfilesClientImpl implements TmpfilesClient {

    private static final String UPLOAD_URL = "https://tempfile.org/api/upload/local";
    private static final String PREVIEW_URL_TEMPLATE = "https://tempfile.org/%s/preview";
    private static final int EXPIRY_HOURS = 24;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MinioService minioService;

    public TmpfilesClientImpl(RestTemplate restTemplate, ObjectMapper objectMapper, MinioService minioService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.minioService = minioService;
    }

    @Override
    public String uploadImage(File image) {
        // 优先走自有 MinIO，失败后回退到外部临时图床，保证流程可用性。
        try {
            return uploadToMinio(image);
        } catch (IOException e) {
            throw new ApiCallException("图片上传失败：" + e.getMessage(), e);
        } catch (RuntimeException ex) {
            log.warn("MinIO 上传失败，回退外部临时图床 fileName={} error={}", image.getName(), ex.getMessage());
            return uploadFileToTempfile(image);
        }
    }

    @Override
    public String uploadImage(MultipartFile image) {
        // MultipartFile 入口与 File 入口保持同样的“主备上传”策略。
        try {
            return uploadToMinio(image);
        } catch (IOException e) {
            throw new ApiCallException("图片上传失败：" + e.getMessage(), e);
        } catch (RuntimeException ex) {
            log.warn("MinIO 上传失败，回退外部临时图床 fileName={} error={}", image.getOriginalFilename(), ex.getMessage());
            return uploadMultipartToTempfile(image);
        }
    }

    private String uploadToMinio(File image) throws IOException {
        log.info("图片上传开始 provider=minio fileName={} fileSize={}", image.getName(), image.length());
        String contentType = Files.probeContentType(image.toPath());
        return minioService.upload(Files.readAllBytes(image.toPath()), image.getName(), contentType);
    }

    private String uploadToMinio(MultipartFile image) throws IOException {
        log.info("图片上传开始 provider=minio fileName={} size={} contentType={}",
                image.getOriginalFilename(), image.getSize(), image.getContentType());
        return minioService.upload(image.getBytes(), resolveFilename(image.getOriginalFilename()), image.getContentType());
    }

    private String uploadFileToTempfile(File image) {
        log.info("图片上传开始 provider=tempfile fileName={} fileSize={}", image.getName(), image.length());
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("files", new FileSystemResource(image));
            body.add("expiryHours", String.valueOf(EXPIRY_HOURS));

            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(UPLOAD_URL, requestEntity, String.class);
            return extractPreviewUrl(response.getBody());
        } catch (IOException e) {
            throw new ApiCallException("图片上传失败：" + e.getMessage(), e);
        }
    }

    private String uploadMultipartToTempfile(MultipartFile image) {
        log.info("图片上传开始 provider=tempfile fileName={} size={} contentType={}",
                image.getOriginalFilename(), image.getSize(), image.getContentType());
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("files", createResource(image));
            body.add("expiryHours", String.valueOf(EXPIRY_HOURS));

            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(UPLOAD_URL, requestEntity, String.class);
            return extractPreviewUrl(response.getBody());
        } catch (IOException e) {
            throw new ApiCallException("图片上传失败：" + e.getMessage(), e);
        }
    }

    private String extractPreviewUrl(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode firstFile = root.path("files").path(0);
        boolean success = root.path("success").asBoolean(false);
        String fileId = firstFile.path("id").asText(null);

        if (!success || fileId == null || fileId.isBlank()) {
            throw new ApiCallException("tempfile.org 返回了非预期响应：" + responseBody);
        }

        String previewUrl = PREVIEW_URL_TEMPLATE.formatted(fileId);
        log.info("临时图床上传完成 previewUrl={}", previewUrl);
        return previewUrl;
    }

    private Resource createResource(MultipartFile image) throws IOException {
        String filename = resolveFilename(image.getOriginalFilename());
        return new ByteArrayResource(image.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private String resolveFilename(String originalFilename) {
        return (originalFilename != null) ? originalFilename : "image-" + UUID.randomUUID() + ".jpg";
    }
}
