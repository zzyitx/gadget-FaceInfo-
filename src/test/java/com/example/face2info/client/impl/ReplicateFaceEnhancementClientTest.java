package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.FaceEnhanceProperties;
import com.example.face2info.exception.ApiCallException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ReplicateFaceEnhancementClientTest {

    @Test
    void shouldCreatePredictionAndDownloadEnhancedImage() throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ApiProperties properties = createProperties();
        ReplicateFaceEnhancementClient client = new ReplicateFaceEnhancementClient(restTemplate, properties);

        server.expect(once(), requestTo("https://api.replicate.com/v1/predictions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer replicate-token"))
                .andRespond(withSuccess("""
                        {"id":"pred-1","status":"succeeded","output":"https://cdn.example.com/enhanced.png"}
                        """, MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://cdn.example.com/enhanced.png"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[]{11, 22, 33}, MediaType.IMAGE_PNG));

        MultipartFile output = client.enhanceFaceImageByUrl(
                "https://tmpfiles.org/face.jpg",
                new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        assertThat(output).isNotNull();
        assertThat(output.getOriginalFilename()).isEqualTo("face-enhanced.jpg");
        assertThat(output.getContentType()).isEqualTo("image/png");
        assertThat(output.getBytes()).containsExactly(11, 22, 33);
        server.verify();
    }

    @Test
    void shouldPollPredictionUntilSucceeded() throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ApiProperties properties = createProperties();
        properties.getApi().getFaceEnhance().getReplicate().setPollIntervalMs(1);
        properties.getApi().getFaceEnhance().getReplicate().setPollTimeoutMs(2000);
        ReplicateFaceEnhancementClient client = new ReplicateFaceEnhancementClient(restTemplate, properties);

        server.expect(once(), requestTo("https://api.replicate.com/v1/predictions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"id":"pred-1","status":"processing"}
                        """, MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://api.replicate.com/v1/predictions/pred-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertAuthHeader(request, "Bearer replicate-token"))
                .andRespond(withSuccess("""
                        {"id":"pred-1","status":"succeeded","output":["https://cdn.example.com/enhanced-2.png"]}
                        """, MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://cdn.example.com/enhanced-2.png"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("abc".getBytes(StandardCharsets.UTF_8), MediaType.IMAGE_PNG));

        MultipartFile output = client.enhanceFaceImageByUrl(
                "https://tmpfiles.org/face.jpg",
                new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        assertThat(output.getOriginalFilename()).isEqualTo("face-enhanced.jpg");
        assertThat(output.getBytes()).containsExactly("abc".getBytes(StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void shouldFailFastWhenModelVersionMissing() {
        RestTemplate restTemplate = new RestTemplate();
        ApiProperties properties = createProperties();
        properties.getApi().getFaceEnhance().getReplicate().setModelVersion("");
        ReplicateFaceEnhancementClient client = new ReplicateFaceEnhancementClient(restTemplate, properties);

        assertThatThrownBy(() -> client.enhanceFaceImageByUrl(
                "https://tmpfiles.org/face.jpg",
                new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3})))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("modelVersion");
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        FaceEnhanceProperties faceEnhance = properties.getApi().getFaceEnhance();
        faceEnhance.setEnabled(true);
        faceEnhance.setProvider("replicate");
        FaceEnhanceProperties.Replicate replicate = faceEnhance.getReplicate();
        replicate.setApiKey("replicate-token");
        replicate.setModelVersion("1a2b3c4d");
        replicate.setPreferWaitSeconds(10);
        return properties;
    }

    private void assertAuthHeader(ClientHttpRequest request, String expectedHeader) {
        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(expectedHeader);
    }
}
