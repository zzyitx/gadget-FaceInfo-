package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.FaceDetectionProperties;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FaceDetectionClientImplTest {

    @Test
    void shouldCallLocalDetectorAndParseResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://localhost:8091/detect"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "detection_id":"det-1",
                          "preview_image":"data:image/jpeg;base64,AAA",
                          "faces":[
                            {
                              "face_id":"face-1",
                              "confidence":0.98,
                              "bbox":{"x":12,"y":24,"width":100,"height":120},
                              "crop_preview":"data:image/png;base64,QUJD",
                              "content_type":"image/png"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        FaceDetectionClientImpl client = new FaceDetectionClientImpl(restTemplate, new ObjectMapper(), createProperties());
        DetectionSession session = client.detect(new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        assertThat(session.getDetectionId()).isEqualTo("det-1");
        assertThat(session.getPreviewImage()).isEqualTo("data:image/jpeg;base64,AAA");
        assertThat(session.getFaces()).hasSize(1);
        assertThat(session.getFaces().get(0).getFaceId()).isEqualTo("face-1");
        SelectedFaceCrop crop = session.getFaces().get(0).getSelectedFaceCrop();
        assertThat(crop.getFilename()).isEqualTo("face-1.png");
        assertThat(crop.getContentType()).isEqualTo("image/png");
        assertThat(new String(crop.getBytes())).isEqualTo("ABC");
        server.verify();
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().setFaceDetection(new FaceDetectionProperties());
        properties.getApi().getFaceDetection().setBaseUrl("http://localhost:8091");
        properties.getApi().getFaceDetection().setDetectPath("/detect");
        return properties;
    }
}
