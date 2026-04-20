package com.example.face2info.client.impl;

import com.example.face2info.client.CompreFaceDetectionClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.CompreFaceProperties;
import com.example.face2info.entity.internal.DetectedFace;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CompreFaceDetectionClientImplTest {

    @Test
    void shouldParseDetectedFaces() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:8000/api/v1/detection/detect?limit=0&det_prob_threshold=0.8&status=false"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "det-key"))
                .andRespond(withSuccess("""
                        {
                          "result": [
                            {
                              "box": {"x_min": 10, "y_min": 20, "x_max": 70, "y_max": 90},
                              "probability": 0.98
                            },
                            {
                              "box": {"x_min": 100, "y_min": 120, "x_max": 160, "y_max": 220},
                              "probability": 0.91
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        CompreFaceDetectionClient client =
                new CompreFaceDetectionClientImpl(restTemplate, new ObjectMapper(), createProperties());

        List<DetectedFace> detectedFaces =
                client.detect(new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        assertThat(detectedFaces).hasSize(2);
        assertThat(detectedFaces.get(0).getConfidence()).isEqualTo(0.98D);
        assertThat(detectedFaces.get(0).getFaceBoundingBox().getX()).isEqualTo(10);
        assertThat(detectedFaces.get(0).getFaceBoundingBox().getY()).isEqualTo(20);
        assertThat(detectedFaces.get(0).getFaceBoundingBox().getWidth()).isEqualTo(60);
        assertThat(detectedFaces.get(0).getFaceBoundingBox().getHeight()).isEqualTo(70);
        server.verify();
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().setCompreface(new CompreFaceProperties());
        properties.getApi().getCompreface().setBaseUrl("http://127.0.0.1:8000");
        properties.getApi().getCompreface().getDetection().setApiKey("det-key");
        properties.getApi().getCompreface().getDetection().setPath("/api/v1/detection/detect");
        properties.getApi().getCompreface().getDetection().setLimit(0);
        properties.getApi().getCompreface().getDetection().setDetProbThreshold(0.8D);
        properties.getApi().getCompreface().getDetection().setStatus(false);
        return properties;
    }
}
