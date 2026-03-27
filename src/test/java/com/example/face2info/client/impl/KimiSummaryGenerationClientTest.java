package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.KimiApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.exception.ApiCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KimiSummaryGenerationClientTest {

    @Test
    void shouldParseSummaryAndTagsFromKimiResponse() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().getSummary().setEnabled(true);
        properties.getApi().getSummary().setProvider("kimi");
        properties.getApi().setKimi(new KimiApiProperties());
        properties.getApi().getKimi().setBaseUrl("https://api.moonshot.cn/v1/chat/completions");
        properties.getApi().getKimi().setApiKey("test-key");
        properties.getApi().getKimi().setModel("moonshot-v1-8k");
        properties.getApi().getKimi().setMaxRetries(1);

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://api.moonshot.cn/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"resolvedName\\":\\"周杰伦\\",\\"summary\\":\\"周杰伦是华语流行乐代表人物。\\",\\"tags\\":[\\"歌手\\",\\"音乐制作人\\"],\\"evidenceUrls\\":[\\"https://example.com/a\\"]}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client = new KimiSummaryGenerationClient(restTemplate, properties, new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePerson("周杰伦", List.of(
                new PageContent().setUrl("https://example.com/a").setContent("正文A")
        ));

        assertThat(profile.getResolvedName()).isEqualTo("周杰伦");
        assertThat(profile.getSummary()).isEqualTo("周杰伦是华语流行乐代表人物。");
        assertThat(profile.getTags()).containsExactly("歌手", "音乐制作人");
        server.verify();
    }

    @Test
    void shouldThrowControlledExceptionWhenKimiReturnsInvalidJson() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().getSummary().setEnabled(true);
        properties.getApi().getSummary().setProvider("kimi");
        properties.getApi().setKimi(new KimiApiProperties());
        properties.getApi().getKimi().setBaseUrl("https://api.moonshot.cn/v1/chat/completions");
        properties.getApi().getKimi().setApiKey("test-key");
        properties.getApi().getKimi().setModel("moonshot-v1-8k");
        properties.getApi().getKimi().setMaxRetries(1);

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://api.moonshot.cn/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}", MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client = new KimiSummaryGenerationClient(restTemplate, properties, new ObjectMapper());

        assertThatThrownBy(() -> client.summarizePerson("周杰伦", List.of(
                new PageContent().setUrl("https://example.com/a").setContent("正文A")
        )))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("INVALID_RESPONSE");
    }
}
