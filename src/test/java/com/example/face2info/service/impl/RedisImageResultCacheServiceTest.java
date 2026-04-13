package com.example.face2info.service.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisImageResultCacheServiceTest {

    @Test
    void shouldSkipReadAndWriteWhenRedisCacheDisabled() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        ApiProperties properties = new ApiProperties();
        properties.getApi().getRedisCache().setEnabled(false);
        RedisImageResultCacheService cacheService = new RedisImageResultCacheService(provider, new ObjectMapper(), properties);

        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThat(cacheService.getRecognitionEvidence(image)).isNull();
        cacheService.cacheFaceInfoResponse(image, new FaceInfoResponse().setStatus("success"));

        verify(provider, never()).getIfAvailable();
    }

    @Test
    void shouldReadAndWriteCachePayloadThroughRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ApiProperties properties = new ApiProperties();
        properties.getApi().getRedisCache().setEnabled(true);
        properties.getApi().getRedisCache().setKeyPrefix("face2info-test");
        RedisImageResultCacheService cacheService = new RedisImageResultCacheService(provider, new ObjectMapper(), properties);

        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence().setSeedQueries(java.util.List.of("Lei Jun"));
        when(valueOperations.get(anyString())).thenReturn("{\"seedQueries\":[\"Lei Jun\"],\"imageMatches\":[],\"webEvidences\":[],\"errors\":[]}");

        cacheService.cacheRecognitionEvidence(image, evidence);
        RecognitionEvidence cached = cacheService.getRecognitionEvidence(image);

        assertThat(cached).isNotNull();
        assertThat(cached.getSeedQueries()).containsExactly("Lei Jun");
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(86400)));
        verify(valueOperations).get(anyString());
    }
}
