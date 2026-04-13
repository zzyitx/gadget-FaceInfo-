package com.example.face2info.service.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.service.ImageResultCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Slf4j
@Service
public class RedisImageResultCacheService implements ImageResultCacheService {

    private static final String RECOGNITION_NAMESPACE = "recognition";
    private static final String FINAL_RESPONSE_NAMESPACE = "final-response";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;
    private final ApiProperties properties;

    public RedisImageResultCacheService(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                        ObjectMapper objectMapper,
                                        ApiProperties properties) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public RecognitionEvidence getRecognitionEvidence(MultipartFile image) {
        return read(image, RECOGNITION_NAMESPACE, RecognitionEvidence.class);
    }

    @Override
    public void cacheRecognitionEvidence(MultipartFile image, RecognitionEvidence evidence) {
        write(image, RECOGNITION_NAMESPACE, evidence, properties.getApi().getRedisCache().getRecognitionTtlSeconds());
    }

    @Override
    public FaceInfoResponse getFaceInfoResponse(MultipartFile image) {
        return read(image, FINAL_RESPONSE_NAMESPACE, FaceInfoResponse.class);
    }

    @Override
    public void cacheFaceInfoResponse(MultipartFile image, FaceInfoResponse response) {
        write(image, FINAL_RESPONSE_NAMESPACE, response, properties.getApi().getRedisCache().getFinalResponseTtlSeconds());
    }

    private <T> T read(MultipartFile image, String namespace, Class<T> type) {
        // 缓存开关关闭时直接降级，避免引入额外依赖故障。
        if (!isEnabled()) {
            return null;
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return null;
        }
        try {
            String key = buildKey(image, namespace);
            String payload = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(payload)) {
                return null;
            }
            return objectMapper.readValue(payload, type);
        } catch (Exception ex) {
            log.warn("Redis 读取缓存失败 namespace={} error={}", namespace, ex.getMessage());
            return null;
        }
    }

    private void write(MultipartFile image, String namespace, Object value, long ttlSeconds) {
        // 空值不缓存，TTL 至少为 1 秒，避免非法配置导致 Redis 写入失败。
        if (!isEnabled() || value == null) {
            return;
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        try {
            String key = buildKey(image, namespace);
            String payload = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, payload, Duration.ofSeconds(Math.max(ttlSeconds, 1L)));
        } catch (Exception ex) {
            log.warn("Redis 写入缓存失败 namespace={} error={}", namespace, ex.getMessage());
        }
    }

    private boolean isEnabled() {
        return properties.getApi().getRedisCache().isEnabled();
    }

    private String buildKey(MultipartFile image, String namespace) throws IOException {
        // 使用“业务前缀 + 命名空间 + 图片内容哈希”构造稳定键，天然支持幂等复用。
        return properties.getApi().getRedisCache().getKeyPrefix()
                + ":" + namespace
                + ":" + sha256(image);
    }

    private String sha256(MultipartFile image) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(image.getBytes()));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
