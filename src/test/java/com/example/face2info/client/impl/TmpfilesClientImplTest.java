package com.example.face2info.client.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TmpfilesClientImplTest {

    @Test
    void shouldUploadMultipartToTempfileOnly() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
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

        TmpfilesClientImpl client = new TmpfilesClientImpl(restTemplate, new ObjectMapper(), mock(com.example.face2info.service.MinioService.class));

        String url = client.uploadImage(image);

        assertThat(url).isEqualTo("https://tempfile.org/abc123/preview");
        server.verify();
    }

    @Test
    void shouldUploadFileToTempfileOnly() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        File imageFile = Files.createTempFile("tmpfiles-client", ".jpg").toFile();
        Files.write(imageFile.toPath(), new byte[]{1, 2, 3});
        server.expect(requestTo("https://tempfile.org/api/upload/local"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "files": [
                            { "id": "file123" }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        com.example.face2info.service.MinioService minioService = mock(com.example.face2info.service.MinioService.class);
        TmpfilesClientImpl client = new TmpfilesClientImpl(restTemplate, new ObjectMapper(), minioService);

        String url = client.uploadImage(imageFile);

        assertThat(url).isEqualTo("https://tempfile.org/file123/preview");
        verify(minioService, never()).upload(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        server.verify();
    }
}
