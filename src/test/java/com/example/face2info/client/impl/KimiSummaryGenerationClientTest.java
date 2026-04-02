package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.KimiApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.exception.ApiCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
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
    void shouldParseStructuredPageSummaryFromKimiResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"resolvedNameCandidate\\":\\"Jay Chou\\",\\"summary\\":\\"Singer\\",\\"keyFacts\\":[\\"Fact A\\"],\\"tags\\":[\\"music\\"],\\"sourceUrl\\":\\"https://example.com/a\\"}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        PageSummary summary = client.summarizePage("Jay Chou", new PageContent()
                .setUrl("https://example.com/a")
                .setTitle("Article A")
                .setContent("Page content A"));

        assertThat(summary.getResolvedNameCandidate()).isEqualTo("Jay Chou");
        assertThat(summary.getSummary()).isEqualTo("Singer");
        assertThat(summary.getKeyFacts()).containsExactly("Fact A");
        assertThat(summary.getTags()).containsExactly("music");
        assertThat(summary.getSourceUrl()).isEqualTo("https://example.com/a");
    }

    @Test
    void shouldThrowControlledExceptionWhenPageSummaryIsBlank() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"summary\\":\\"   \\"}"}}]}
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        assertThatThrownBy(() -> client.summarizePage("Jay Chou", new PageContent()
                .setUrl("https://example.com/a")
                .setTitle("Article A")
                .setContent("Page content A")))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("EMPTY_RESPONSE");
    }

    @Test
    void shouldParseFinalProfileFromPageSummaries() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"resolvedName\\":\\"Jay Chou\\",\\"summary\\":\\"Jay Chou is a Mandopop singer-songwriter.\\",\\"keyFacts\\":[\\"Fact A\\"],\\"tags\\":[\\"singer\\",\\"producer\\"],\\"evidenceUrls\\":[\\"https://example.com/a\\",\\"https://example.com/b\\"]}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePersonFromPageSummaries("Jay Chou", List.of(
                new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A"),
                new PageSummary().setSourceUrl("https://example.com/b").setSummary("Summary B")
        ));

        assertThat(profile.getResolvedName()).isEqualTo("Jay Chou");
        assertThat(profile.getSummary()).isEqualTo("Jay Chou is a Mandopop singer-songwriter.");
        assertThat(profile.getKeyFacts()).containsExactly("Fact A");
        assertThat(profile.getTags()).containsExactly("singer", "producer");
        assertThat(profile.getEvidenceUrls()).containsExactly("https://example.com/a", "https://example.com/b");
    }

    @Test
    void shouldThrowControlledExceptionWhenFinalSummaryReturnsInvalidJson() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}", MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        assertThatThrownBy(() -> client.summarizePersonFromPageSummaries("Jay Chou", List.of(
                new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A")
        )))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("INVALID_RESPONSE");
    }

    @Disabled("手工调试 Kimi 真调用时再启用")
    @Test
    void shouldPrintRealKimiResponse() {
    }

    @Disabled("手工调试原始响应时再启用")
    @Test
    void shouldPrintRawLeiJunArticleResponseWithoutPrompt() {
    }

    private ApiProperties createProperties(String apiKey) {
        ApiProperties properties = new ApiProperties();
        properties.getApi().getSummary().setEnabled(true);
        properties.getApi().getSummary().setProvider("kimi");
        properties.getApi().setKimi(new KimiApiProperties());
        properties.getApi().getKimi().setBaseUrl("https://www.sophnet.com/api/open-apis/v1/chat/completions");
        properties.getApi().getKimi().setApiKey(apiKey);
        properties.getApi().getKimi().setModel("Kimi-K2.5");
        properties.getApi().getKimi().setMaxRetries(1);
        return properties;
    }
}
