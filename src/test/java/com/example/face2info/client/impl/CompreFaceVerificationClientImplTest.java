package com.example.face2info.client.impl;

import com.example.face2info.client.CompreFaceVerificationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.CompreFaceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CompreFaceVerificationClientImplTest {

    @Test
    void shouldReturnHighestSimilarity() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:8000/api/v1/verify/verify?limit=1&det_prob_threshold=0.8&prediction_count=1&status=false"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "verify-key"))
                .andRespond(withSuccess("""
                        {
                          "result": [
                            {
                              "face_matches": [
                                {"similarity": 0.9732}
                              ]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        CompreFaceVerificationClient client =
                new CompreFaceVerificationClientImpl(restTemplate, new ObjectMapper(), createProperties());

        OptionalDouble similarity = client.verify(new byte[]{1, 2, 3}, new byte[]{4, 5, 6}, "image/jpeg");

        assertThat(similarity).isPresent();
        assertThat(similarity.getAsDouble()).isEqualTo(0.9732D);
        server.verify();
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().setCompreface(new CompreFaceProperties());
        properties.getApi().getCompreface().setBaseUrl("http://127.0.0.1:8000");
        properties.getApi().getCompreface().getVerification().setApiKey("verify-key");
        properties.getApi().getCompreface().getVerification().setPath("/api/v1/verify/verify");
        properties.getApi().getCompreface().getVerification().setLimit(1);
        properties.getApi().getCompreface().getVerification().setDetProbThreshold(0.8D);
        properties.getApi().getCompreface().getVerification().setPredictionCount(1);
        properties.getApi().getCompreface().getVerification().setStatus(false);
        return properties;
    }
}
