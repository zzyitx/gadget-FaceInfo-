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

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KimiSummaryGenerationClientTest {

    @Test
    void shouldParseStructuredPageSummaryFromKimiToolCallArguments() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "tool_calls": [
                                  {
                                    "id": "call_page_summary",
                                    "type": "function",
                                    "function": {
                                      "name": "submit_page_summary",
                                      "arguments": "{\\"resolvedNameCandidate\\":\\"Jay Chou\\",\\"summary\\":\\"Singer\\",\\"keyFacts\\":[\\"Fact A\\"],\\"tags\\":[\\"music\\"],\\"sourceUrl\\":\\"https://example.com/a\\"}"
                                    }
                                  }
                                ]
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
    void shouldParseFaceCandidateNamesWhenKimiWrapsJsonWithExplanation() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "我先给出识别结果：\\n```json\\n{\\\"candidateNames\\\":[\\\"雷军\\\",\\\"Lei Jun\\\"]}\\n```"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        List<String> candidates = client.recognizeFaceCandidateNames(new org.springframework.mock.web.MockMultipartFile(
                "image", "face.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image".getBytes()
        ));

        assertThat(candidates).containsExactly("雷军", "Lei Jun");
    }

    @Test
    void shouldThrowControlledExceptionWhenKimiReturnsPlainTextForFaceCandidates() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "我需要根据这张人脸图像判断人物身份，但现在无法给出候选名称。"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        assertThatThrownBy(() -> client.recognizeFaceCandidateNames(new org.springframework.mock.web.MockMultipartFile(
                "image", "face.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image".getBytes()
        )))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("INVALID_RESPONSE")
                .hasMessageContaining("未返回 JSON");
    }

    @Test
    void shouldSummarizeSectionFromPageSummaries() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"summary\\":\\"周杰伦的教育经历主要集中在台湾求学阶段。\\"}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        String summary = client.summarizeSectionFromPageSummaries(
                "周杰伦",
                "education",
                List.of(new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("A summary"))
        );

        assertThat(summary).isEqualTo("周杰伦的教育经历主要集中在台湾求学阶段。");
    }

    @Test
    void shouldReturnNullWhenSectionSummaryIsBlank() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"summary\\":\\"   \\"}"}}]}
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        String summary = client.summarizeSectionFromPageSummaries(
                "周杰伦",
                "career",
                List.of(new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("A summary"))
        );

        assertThat(summary).isNull();
    }

    @Test
    void shouldThrowWhenSectionSummaryPayloadIsInvalid() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"not-json"}}]}
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        assertThatThrownBy(() -> client.summarizeSectionFromPageSummaries(
                "周杰伦",
                "family",
                List.of(new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("A summary"))
        )).isInstanceOf(ApiCallException.class)
                .hasMessageContaining("INVALID_RESPONSE");
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
    void shouldParsePageSummaryWhenKimiWrapsJsonWithExplanation() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "以下是结构化结果：\\n```json\\n{\\\"resolvedNameCandidate\\\":\\\"雷军\\\",\\\"summary\\\":\\\"小米创始人。\\\",\\\"keyFacts\\\":[\\\"创办小米\\\"],\\\"tags\\\":[\\\"科技\\\"],\\\"sourceUrl\\\":\\\"https://example.com/lei\\\",\\\"title\\\":\\\"Lei Jun\\\"}\\n```"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        PageSummary summary = client.summarizePage("雷军", new PageContent()
                .setUrl("https://example.com/lei")
                .setTitle("Lei Jun")
                .setContent("Page content"));

        assertThat(summary.getResolvedNameCandidate()).isEqualTo("雷军");
        assertThat(summary.getSummary()).isEqualTo("小米创始人。");
        assertThat(summary.getKeyFacts()).containsExactly("创办小米");
        assertThat(summary.getTags()).containsExactly("科技");
        assertThat(summary.getSourceUrl()).isEqualTo("https://example.com/lei");
    }

    @Test
    void shouldThrowControlledExceptionWhenKimiReturnsPlainTextRefusalForPageSummary() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "抱歉，这个页面我暂时无法总结。"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        assertThatThrownBy(() -> client.summarizePage("雷军", new PageContent()
                .setUrl("https://example.com/lei")
                .setTitle("Lei Jun")
                .setContent("Page content")))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("INVALID_RESPONSE")
                .hasMessageContaining("未返回 JSON");
    }

    @Test
    void shouldParseFinalProfileFromKimiToolCallArguments() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "tool_calls": [
                                  {
                                    "id": "call_person_profile",
                                    "type": "function",
                                    "function": {
                                      "name": "submit_person_profile",
                                      "arguments": "{\\"resolvedName\\":\\"Jay Chou\\",\\"summary\\":\\"Jay Chou is a Mandopop singer-songwriter.\\",\\"keyFacts\\":[\\"Fact A\\"],\\"tags\\":[\\"singer\\",\\"producer\\"],\\"evidenceUrls\\":[\\"https://example.com/a\\",\\"https://example.com/b\\"]}"
                                    }
                                  }
                                ]
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
    void shouldParseStructuredBasicInfoFromFinalProfile() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"resolvedName\\":\\"Jay Chou\\",\\"description\\":\\"Short description\\",\\"summary\\":\\"Long summary\\",\\"wikipedia\\":\\"https://example.com/wiki\\",\\"officialWebsite\\":\\"https://example.com\\",\\"basicInfo\\":{\\"birthDate\\":\\"1979-01-18\\",\\"education\\":[\\"Tamkang Senior High School\\"],\\"occupations\\":[\\"Singer\\"],\\"biographies\\":[\\"Taiwanese Mandopop artist\\"]}}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePersonFromPageSummaries("Jay Chou", List.of(
                new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A")
        ));

        assertThat(profile.getDescription()).isEqualTo("Short description");
        assertThat(profile.getWikipedia()).isEqualTo("https://example.com/wiki");
        assertThat(profile.getOfficialWebsite()).isEqualTo("https://example.com");
        assertThat(profile.getBasicInfo().getBirthDate()).isEqualTo("1979-01-18");
        assertThat(profile.getBasicInfo().getEducation()).containsExactly("Tamkang Senior High School");
        assertThat(profile.getBasicInfo().getOccupations()).containsExactly("Singer");
        assertThat(profile.getBasicInfo().getBiographies()).containsExactly("Taiwanese Mandopop artist");
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

    @Test
    void shouldRetryFinalProfileWhenFirstAttemptTimesOut() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\\"resolvedName\\\":\\\"Jay Chou\\\",\\\"summary\\\":\\\"Jay Chou is a Mandopop singer-songwriter.\\\",\\\"keyFacts\\\":[\\\"Fact A\\\"],\\\"tags\\\":[\\\"singer\\\",\\\"producer\\\"],\\\"evidenceUrls\\\":[\\\"https://example.com/a\\\"]}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key", 2, 1), new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePersonFromPageSummaries("Jay Chou", List.of(
                new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A")
        ));

        assertThat(profile.getResolvedName()).isEqualTo("Jay Chou");
        assertThat(profile.getSummary()).isEqualTo("Jay Chou is a Mandopop singer-songwriter.");
        server.verify();
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
        return createProperties(apiKey, 1, 300L);
    }

    private ApiProperties createProperties(String apiKey, int maxRetries, long backoffInitialMs) {
        ApiProperties properties = new ApiProperties();
        properties.getApi().getSummary().setEnabled(true);
        properties.getApi().getSummary().setProvider("kimi");
        properties.getApi().setKimi(new KimiApiProperties());
        properties.getApi().getKimi().setBaseUrl("https://www.sophnet.com/api/open-apis/v1/chat/completions");
        properties.getApi().getKimi().setApiKey(apiKey);
        properties.getApi().getKimi().setModel("Kimi-K2.5");
        properties.getApi().getKimi().setMaxRetries(maxRetries);
        properties.getApi().getKimi().setBackoffInitialMs(backoffInitialMs);
        return properties;
    }
}
