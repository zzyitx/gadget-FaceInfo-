package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.VisionModelSearchResult;
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

@DisplayName("测试类：SophnetVisionPersonSearchClientClaudeRequestTest")
class SophnetVisionPersonSearchClientClaudeRequestTest {

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Visual Ground Truth extractor")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"role\":\"system\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"role\":\"user\"")))
                .andRespond(withSuccess(response(), MediaType.APPLICATION_JSON));

        SophnetVisionPersonSearchClient client = new SophnetVisionPersonSearchClient(
                restTemplate, properties, objectMapper);

        List<VisionModelSearchResult> results = client.searchPersonByImageUrl("https://example.com/face.jpg");

        assertThat(results).hasSize(1);
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

    private String response() {
        String content = """
                {
                  "candidateName": "Ada Lovelace",
                  "confidence": 0.91,
                  "summary": "视觉参考已生成。",
                  "company": "",
                  "position": "",
                  "socialAccounts": [],
                  "visualGroundTruth": {"eyewear": "no glasses"},
                  "evidenceUrls": [],
                  "tags": ["visual_ground_truth"],
                  "sourceNotes": ["web"]
                }
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
