package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.entity.internal.VisionModelSearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SophnetVisionPersonSearchClientTest {

    @Test
    void shouldCallEachConfiguredVisionModelWithImageUrlAndParseJsonContent() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ApiProperties properties = createProperties();

        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("vision-model-a")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://example.com/face.jpg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Visual Ground Truth")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("visualGroundTruth")))
                .andRespond(withSuccess(response("Ada Lovelace", "https://example.com/ada"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("vision-model-b")))
                .andRespond(withSuccess(response("Ada Lovelace", "https://example.com/wiki"), MediaType.APPLICATION_JSON));

        SophnetVisionPersonSearchClient client = new SophnetVisionPersonSearchClient(
                restTemplate, properties, new ObjectMapper());

        List<VisionModelSearchResult> results = client.searchPersonByImageUrl("https://example.com/face.jpg");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(VisionModelSearchResult::getCandidateName)
                .containsExactly("Ada Lovelace", "Ada Lovelace");
        assertThat(results.get(0).getEvidenceUrls()).containsExactly("https://example.com/ada");
        assertThat(results.get(0).getCompany()).isEqualTo("Analytical Engine");
        assertThat(results.get(0).getPosition()).isEqualTo("Mathematician");
        assertThat(results.get(0).getSocialAccounts()).hasSize(1);
        assertThat(results.get(0).getSocialAccounts().get(0).getPlatform()).isEqualTo("X");
        assertThat(results.get(0).getSocialAccounts().get(0).getSource()).isEqualTo("sophnet_vision");
        assertThat(results.get(0).getVisualGroundTruth())
                .containsEntry("ageRange", "30-40")
                .containsEntry("eyewear", "no glasses")
                .containsEntry("visibleTextLogoBadge", "none visible");
        server.verify();
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().getSophnetVision().setEnabled(true);
        properties.getApi().getSophnetVision().setApiKey("test-key");
        properties.getApi().getSophnetVision().setMaxRetries(1);
        properties.getApi().getSophnetVision().setModels(List.of(
                "vision-model-a",
                "vision-model-b"
        ));
        return properties;
    }

    private String response(String name, String url) {
        String content = """
                {
                  "candidateName": "%s",
                  "confidence": 0.91,
                  "summary": "%s 是公开资料中的人物。",
                  "company": "Analytical Engine",
                  "position": "Mathematician",
                  "socialAccounts": [{
                    "platform": "X",
                    "username": "ada",
                    "url": "https://x.com/ada",
                    "confidence": "suspected"
                  }],
                  "visualGroundTruth": {
                    "ageRange": "30-40",
                    "skinToneOrEthnicity": "light skin tone",
                    "hairStyleAndColor": "dark tied hair",
                    "eyewear": "no glasses",
                    "clothingStyle": "formal",
                    "environmentClues": "indoor office",
                    "visibleTextLogoBadge": "none visible"
                  },
                  "evidenceUrls": ["%s"],
                  "tags": ["public"],
                  "sourceNotes": ["web"]
                }
                """.formatted(name, name, url)
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
