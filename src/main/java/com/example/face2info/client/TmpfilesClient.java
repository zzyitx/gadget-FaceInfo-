package com.example.face2info.client;

import com.example.face2info.exception.ApiCallException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 临时文件上传客户端。
 * 用于把本地上传图片转换成外部可访问的临时 URL，供反向搜图接口使用。
 */
@Component
public class TmpfilesClient {

    @Autowired
    Logger log = LoggerFactory.getLogger(TmpfilesClient.class);

    @Autowired
    String UPLOAD_URL = "https://tempfile.org/api/upload/local";

    @Autowired
    String PREVIEW_URL_TEMPLATE = "https://tempfile.org/%s/preview";

    @Autowired
    int EXPIRY_HOURS = 24;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    public TmpfilesClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 上传本地文件对象并返回可访问的预览地址。
     */
    public String uploadImage(java.io.File image) {
        log.info("Uploading image to tempfile.org...");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("files", new org.springframework.core.io.FileSystemResource(image));
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
     * 上传 MultipartFile 并返回可访问的预览地址。
     */
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
     * 把 MultipartFile 转为可供 RestTemplate 提交的资源对象。
     */
    private org.springframework.core.io.Resource createResource(MultipartFile image) throws IOException {
        String originalFilename = image.getOriginalFilename();
        String filename = (originalFilename != null) ? originalFilename : "image-" + UUID.randomUUID() + ".jpg";
        return new org.springframework.core.io.ByteArrayResource(image.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
