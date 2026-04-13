package com.example.face2info.client.impl;

import com.example.face2info.exception.ApiCallException;
import com.example.face2info.service.MinioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TmpfilesClientImplTest {

    @Test
    void shouldPreferMinioForMultipartUpload() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MinioService minioService = mock(MinioService.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(minioService.upload(any(), anyString(), anyString())).thenReturn("http://minio.local/face-bucket/face.jpg");

        TmpfilesClientImpl client = new TmpfilesClientImpl(restTemplate, new ObjectMapper(), minioService);

        String url = client.uploadImage(image);

        assertThat(url).isEqualTo("http://minio.local/face-bucket/face.jpg");
        verify(minioService).upload(image.getBytes(), "face.jpg", "image/jpeg");
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void shouldFallbackToTempfileWhenMinioUploadFails() {
        RestTemplate restTemplate = new RestTemplate();
        MinioService minioService = mock(MinioService.class);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(minioService.upload(any(), anyString(), anyString())).thenThrow(new RuntimeException("minio down"));
        server.expect(requestTo("https://tempfile.org/api/upload/local"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "files": [
                            { "id": "abc123" }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmpfilesClientImpl client = new TmpfilesClientImpl(restTemplate, new ObjectMapper(), minioService);

        String url = client.uploadImage(image);

        assertThat(url).isEqualTo("https://tempfile.org/abc123/preview");
        server.verify();
    }

    @Test
    void shouldPreferMinioForFileUpload() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MinioService minioService = mock(MinioService.class);
        File imageFile = Files.createTempFile("tmpfiles-client", ".jpg").toFile();
        Files.write(imageFile.toPath(), new byte[]{1, 2, 3});
        when(minioService.upload(any(), anyString(), anyString())).thenReturn("http://minio.local/face-bucket/face.jpg");

        TmpfilesClientImpl client = new TmpfilesClientImpl(restTemplate, new ObjectMapper(), minioService);

        String url = client.uploadImage(imageFile);

        assertThat(url).isEqualTo("http://minio.local/face-bucket/face.jpg");
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }
}
