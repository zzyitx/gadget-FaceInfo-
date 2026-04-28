package com.example.face2info.service.impl;

import com.example.face2info.client.CompreFaceVerificationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.service.ImageSimilarityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.OptionalDouble;

@Slf4j
@Service
public class ImageSimilarityServiceImpl implements ImageSimilarityService {

    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 4000;
    private final CompreFaceVerificationClient compreFaceVerificationClient;
    private final ApiProperties properties;

    @Autowired
    public ImageSimilarityServiceImpl(CompreFaceVerificationClient compreFaceVerificationClient,
                                      ApiProperties properties) {
        this.compreFaceVerificationClient = compreFaceVerificationClient;
        this.properties = properties;
    }

    ImageSimilarityServiceImpl(CompreFaceVerificationClient compreFaceVerificationClient) {
        this(compreFaceVerificationClient, new ApiProperties());
    }

    @Override
    public double score(MultipartFile originalImage, String candidateImageUrl, double fallbackScore) {
        if (!StringUtils.hasText(candidateImageUrl) || originalImage == null || originalImage.isEmpty()) {
            return fallbackScore;
        }
        if (!isComprefaceEnabled()) {
            log.debug("CompreFace verification 已禁用，使用回退相似度分数。");
            return fallbackScore;
        }
        try {
            byte[] candidateImage = readFromUrl(candidateImageUrl);
            if (candidateImage.length == 0) {
                return fallbackScore;
            }
            // 统一走 CompreFace 人脸比对，避免本地哈希算法对姿态/清晰度变化过于敏感。
            OptionalDouble similarity = compreFaceVerificationClient.verify(
                    originalImage.getBytes(),
                    candidateImage,
                    resolveContentType(originalImage)
            );
            if (similarity.isEmpty()) {
                return fallbackScore;
            }
            return round(similarity.getAsDouble() * 100.0D);
        } catch (Exception ex) {
            log.debug("图像相似度计算失败，使用回退分 url={} error={}", candidateImageUrl, ex.getMessage());
            return fallbackScore;
        }
    }

    private byte[] readFromUrl(String candidateImageUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(candidateImageUrl).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        try (InputStream inputStream = connection.getInputStream()) {
            return inputStream.readAllBytes();
        } finally {
            connection.disconnect();
        }
    }

    private String resolveContentType(MultipartFile originalImage) {
        return StringUtils.hasText(originalImage.getContentType()) ? originalImage.getContentType() : "image/jpeg";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private boolean isComprefaceEnabled() {
        return properties == null
                || properties.getApi() == null
                || properties.getApi().getCompreface() == null
                || properties.getApi().getCompreface().isEnabled();
    }
}
