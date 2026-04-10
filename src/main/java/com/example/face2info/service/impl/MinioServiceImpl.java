package com.example.face2info.service.impl;

import com.example.face2info.client.MinioClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.MinioProperties;
import com.example.face2info.service.MinioService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

@Service
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClientWrapper;
    private final ApiProperties properties;

    public MinioServiceImpl(MinioClient minioClientWrapper, ApiProperties properties) {
        this.minioClientWrapper = minioClientWrapper;
        this.properties = properties;
    }

    @Override
    public String upload(byte[] bytes, String filename, String contentType) {
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            MinioProperties minio = properties.getApi().getMinio();
            String bucket = minio.getBucket();
            minioClientWrapper.createBucketIfNotExists(bucket);

            String objectName = "face-enhance/" + UUID.randomUUID() + "-" + filename;
            minioClientWrapper.putObject(bucket, objectName, is, bytes.length, contentType);

            String baseUrl = StringUtils.hasText(minio.getPublicEndpoint())
                    ? minio.getPublicEndpoint()
                    : minio.getEndpoint();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            return baseUrl + "/" + bucket + "/" + objectName;
        } catch (Exception e) {
            throw new RuntimeException("MinIO上传失败", e);
        }
    }
}
