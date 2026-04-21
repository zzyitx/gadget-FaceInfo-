package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekRealtimeTranslationClientTest {

    @Test
    void shouldTranslateQueryViaAnthropicMessagesApi() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/anthropic/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"content":[{"type":"text","text":"Jensen Huang a tenu des propos liés à la Chine"}]}
                        """, MediaType.APPLICATION_JSON));

        DeepSeekRealtimeTranslationClient client =
                new DeepSeekRealtimeTranslationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        String translated = client.translateQuery("Jensen Huang china-related statements", "fr");

        assertThat(translated).isEqualTo("Jensen Huang a tenu des propos liés à la Chine");
        server.verify();
    }

    private ApiProperties createProperties(String apiKey) {
        ApiProperties properties = new ApiProperties();
        properties.getApi().getRealtimeTranslation().setApiKey(apiKey);
        properties.getApi().getRealtimeTranslation().setBaseUrl("https://www.sophnet.com/api/open-apis/anthropic/v1/messages");
        properties.getApi().getRealtimeTranslation().setModel("DeepSeek-R1-Distill-Qwen-7B");
        properties.getApi().getRealtimeTranslation().setMaxRetries(2);
        properties.getApi().getRealtimeTranslation().setBackoffInitialMs(1L);
        return properties;
    }
}
