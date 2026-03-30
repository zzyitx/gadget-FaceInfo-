package com.example.face2info.client.impl;

import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.exception.ApiCallException;
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
import java.util.UUID;

@Slf4j
@Component
public class TmpfilesClientImpl implements TmpfilesClient {

    private static final String UPLOAD_URL = "https://tempfile.org/api/upload/local";
    private static final String PREVIEW_URL_TEMPLATE = "https://tempfile.org/%s/preview";
    private static final int EXPIRY_HOURS = 24;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TmpfilesClientImpl(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String uploadImage(File image) {
        log.info("临时图床上传开始 fileName={} fileSize={}", image.getName(), image.length());
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
            throw new ApiCallException("Image upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadImage(MultipartFile image) {
        log.info("临时图床上传开始 fileName={} size={} contentType={}",
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
            throw new ApiCallException("Image upload failed: " + e.getMessage(), e);
        }
    }

    private String extractPreviewUrl(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode firstFile = root.path("files").path(0);
        boolean success = root.path("success").asBoolean(false);
        String fileId = firstFile.path("id").asText(null);

        if (!success || fileId == null || fileId.isBlank()) {
            throw new ApiCallException("tempfile.org returned unexpected payload: " + responseBody);
        }

        String previewUrl = PREVIEW_URL_TEMPLATE.formatted(fileId);
        log.info("临时图床上传完成 previewUrl={}", previewUrl);
        return previewUrl;
    }

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
