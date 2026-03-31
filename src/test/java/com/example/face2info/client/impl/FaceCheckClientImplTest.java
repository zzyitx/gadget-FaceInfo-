package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.FaceCheckApiProperties;
import com.example.face2info.entity.internal.FaceCheckUploadResponse;
import com.example.face2info.exception.ApiCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FaceCheckClientImplTest {

    @Test
    void shouldMapUploadResponseItemsToMatchCandidates() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://facecheck.id/api/upload_pic"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "id_search":"req-1",
                          "output":{
                            "items":[
                              {
                                "score":97.2,
                                "group":1,
                                "base64":"AAA",
                                "url":{"value":"https://www.instagram.com/p/demo"},
                                "index":0,
                                "seen":3
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        FaceCheckClientImpl client = new FaceCheckClientImpl(restTemplate, new ObjectMapper(), createProperties());

        FaceCheckUploadResponse response = client.upload(
                new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getSourceHost()).isEqualTo("instagram.com");
        assertThat(response.getItems().get(0).getImageDataUrl()).isEqualTo("data:image/jpeg;base64,AAA");
        server.verify();
    }

    @Test
    void shouldReturnEmptyItemsWhenFacecheckOutputIsMissing() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://facecheck.id/api/upload_pic"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id_search\":\"req-2\"}", MediaType.APPLICATION_JSON));

        FaceCheckClientImpl client = new FaceCheckClientImpl(restTemplate, new ObjectMapper(), createProperties());

        FaceCheckUploadResponse response = client.upload(
                new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        assertThat(response.getItems()).isEmpty();
        server.verify();
    }

    @Test
    void shouldWrapRemoteErrorsAsApiCallException() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://facecheck.id/api/upload_pic"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        FaceCheckClientImpl client = new FaceCheckClientImpl(restTemplate, new ObjectMapper(), createProperties());

        assertThatThrownBy(() -> client.upload(
                new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3})))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("facecheck");

        server.verify();
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().setFacecheck(new FaceCheckApiProperties());
        properties.getApi().getFacecheck().setBaseUrl("https://facecheck.id");
        properties.getApi().getFacecheck().setUploadPath("/api/upload_pic");
        properties.getApi().getFacecheck().setApiKey("test-key");
        properties.getApi().getFacecheck().setResetPrevImages(true);
        return properties;
    }
}
