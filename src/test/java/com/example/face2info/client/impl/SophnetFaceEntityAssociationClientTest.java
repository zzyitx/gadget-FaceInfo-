package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.FaceEntityAssociation;
import com.example.face2info.entity.internal.NamedEntity;
import com.example.face2info.entity.internal.PageSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("测试类：SophnetFaceEntityAssociationClientTest")
class SophnetFaceEntityAssociationClientTest {

    private static final String TEST_CHAT_COMPLETIONS_URL = "https://model-gateway.example.invalid/v1/chat/completions";

    @DisplayName("功能测试：shouldSendClaudeSystemPromptAsTopLevelSystemParameter")
    @Test
    void shouldSendClaudeSystemPromptAsTopLevelSystemParameter() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ApiProperties properties = createProperties();
        ObjectMapper objectMapper = new ObjectMapper();

        server.expect(requestTo(TEST_CHAT_COMPLETIONS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"system\":\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("You align one target face image")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"role\":\"system\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"role\":\"user\"")))
                .andRespond(withSuccess(response(), MediaType.APPLICATION_JSON));

        SophnetFaceEntityAssociationClient client = new SophnetFaceEntityAssociationClient(
                restTemplate, properties, objectMapper);

        List<FaceEntityAssociation> associations = client.associate(
                "https://example.com/face.jpg",
                pageSummary());

        assertThat(associations).hasSize(1);
        server.verify();
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().getSophnetVision().setEnabled(true);
        properties.getApi().getSophnetVision().setBaseUrl(TEST_CHAT_COMPLETIONS_URL);
        properties.getApi().getSophnetVision().setApiKey("test-key");
        properties.getApi().getSophnetVision().setMaxRetries(1);
        properties.getApi().getSophnetVision().setModels(List.of("claude-opus-4-7"));
        return properties;
    }

    private PageSummary pageSummary() {
        return new PageSummary()
                .setSourceUrl("https://example.com/article")
                .setTitle("Ada profile")
                .setSummary("Ada Lovelace profile")
                .setNamedEntities(List.of(new NamedEntity()
                        .setType("PERSON")
                        .setText("Ada Lovelace")
                        .setMentions(2)
                        .setContexts(List.of("Ada Lovelace profile"))));
    }

    private String response() {
        String content = """
                {"associations":[{"entityText":"Ada Lovelace","entityType":"PERSON","confidenceScore":0.92,"reason":"face and article match"}]}
                """
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "");
        return """
                {
                  "choices": [{
                    "message": {
                      "content": "%s"
                    }
                  }]
                }
                """.formatted(content);
    }
}
