package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.FaceCheckApiProperties;
import com.example.face2info.entity.internal.FaceCheckMatchCandidate;
import com.example.face2info.entity.internal.FaceCheckSearchResponse;
import com.example.face2info.exception.ApiCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.ExpectedCount;
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
    void shouldExposeSearchResultContractForFinalMatches() {
        FaceCheckSearchResponse response = new FaceCheckSearchResponse()
                .setItems(java.util.List.of(new FaceCheckMatchCandidate().setSourceHost("instagram.com")))
                .setTimedOut(true);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.isTimedOut()).isTrue();
    }

    @Test
    void shouldUploadThenPollSearchUntilItemsAppear() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://facecheck.id/api/upload_pic"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "id_search":"req-1",
                          "message":"uploaded"
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://facecheck.id/api/get_results"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "message":"searching",
                          "progress":35
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://facecheck.id/api/get_results"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "output":{
                            "items":[
                              {
                                "score":97,
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

        FaceCheckSearchResponse response = client.search(
                new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        assertThat(response.isTimedOut()).isFalse();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getSourceHost()).isEqualTo("instagram.com");
        server.verify();
    }

    @Test
    void shouldSupportGetResultsContractWithOutputArray() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://facecheck.id/api/upload_pic"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "id_search":"req-demo",
                          "message":"uploaded"
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://facecheck.id/api/get_results"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "message":"searching",
                          "progress":30
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://facecheck.id/api/get_results"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "output":[
                            {
                              "score":98.4,
                              "thumbnail":"https://cdn.example.com/thumb.jpg",
                              "link":"https://www.instagram.com/p/demo",
                              "source":"instagram.com",
                              "index":2,
                              "seen":7
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ApiProperties properties = createProperties();
        properties.getApi().getFacecheck().setSearchPath("/api/get_results");
        FaceCheckClientImpl client = new FaceCheckClientImpl(restTemplate, new ObjectMapper(), properties);

        FaceCheckSearchResponse response = client.search(
                new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        assertThat(response.isTimedOut()).isFalse();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getImageDataUrl()).isEqualTo("https://cdn.example.com/thumb.jpg");
        assertThat(response.getItems().get(0).getSimilarityScore()).isEqualTo(98.4);
        assertThat(response.getItems().get(0).getSourceHost()).isEqualTo("instagram.com");
        assertThat(response.getItems().get(0).getSourceUrl()).isEqualTo("https://www.instagram.com/p/demo");
        server.verify();
    }

    @Test
    void shouldThrowWhenSearchReturnsRemoteError() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://facecheck.id/api/upload_pic"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "id_search":"req-err"
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://facecheck.id/api/get_results"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "error":"quota exceeded",
                          "code":"403"
                        }
                        """, MediaType.APPLICATION_JSON));

        FaceCheckClientImpl client = new FaceCheckClientImpl(restTemplate, new ObjectMapper(), createProperties());

        assertThatThrownBy(() -> client.search(
                new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3})))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("facecheck");
        server.verify();
    }

    @Test
    void shouldReturnTimedOutResponseWhenSearchDoesNotFinishInTime() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://facecheck.id/api/upload_pic"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "id_search":"req-timeout"
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.manyTimes(), requestTo("https://facecheck.id/api/get_results"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "message":"still searching",
                          "progress":10
                        }
                        """, MediaType.APPLICATION_JSON));

        ApiProperties properties = createProperties();
        properties.getApi().getFacecheck().setSearchTimeoutMillis(1);
        properties.getApi().getFacecheck().setPollIntervalMillis(0);

        FaceCheckClientImpl client = new FaceCheckClientImpl(restTemplate, new ObjectMapper(), properties);

        FaceCheckSearchResponse response = client.search(
                new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        assertThat(response.isTimedOut()).isTrue();
        assertThat(response.getItems()).isEmpty();
        server.verify();
    }

    @Test
    void shouldWrapUploadErrorsAsApiCallException() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://facecheck.id/api/upload_pic"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        FaceCheckClientImpl client = new FaceCheckClientImpl(restTemplate, new ObjectMapper(), createProperties());

        assertThatThrownBy(() -> client.search(
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
        properties.getApi().getFacecheck().setSearchPath("/api/get_results");
        properties.getApi().getFacecheck().setApiKey("test-key");
        properties.getApi().getFacecheck().setResetPrevImages(true);
        return properties;
    }
}
