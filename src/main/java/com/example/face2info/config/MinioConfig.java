package com.example.face2info.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    private final ApiProperties properties;

    public MinioConfig(ApiProperties properties) {
        this.properties = properties;
    }

    @Bean
    public MinioClient minioClient() {
        MinioProperties minio = properties.getApi().getMinio();
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }
}
