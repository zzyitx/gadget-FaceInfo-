package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.DeepSeekApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.exception.ApiCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class DeepSeekSummaryGenerationClientTest {

    @Test
    void shouldParsePageSummaryFromDeepSeekResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"resolvedNameCandidate\\":\\"Jay Chou\\",\\"summary\\":\\"Singer\\",\\"keyFacts\\":[\\"Fact A\\"],\\"tags\\":[\\"music\\"],\\"sourceUrl\\":\\"https://example.com/a\\",\\"title\\":\\"A\\"}"}}]}
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        PageSummary summary = client.summarizePage("Jay Chou", new PageContent()
                .setUrl("https://example.com/a")
                .setTitle("A")
                .setContent("body"));

        assertThat(summary.getResolvedNameCandidate()).isEqualTo("Jay Chou");
        assertThat(summary.getSummary()).isEqualTo("Singer");
        assertThat(summary.getKeyFacts()).containsExactly("Fact A");
        assertThat(summary.getTags()).containsExactly("music");
    }

    @Test
    void shouldParseFinalProfileFromDeepSeekResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"resolvedName\\":\\"Jay Chou\\",\\"summary\\":\\"Mandopop singer-songwriter\\",\\"keyFacts\\":[\\"Fact A\\"],\\"tags\\":[\\"singer\\"],\\"evidenceUrls\\":[\\"https://example.com/a\\"]}"}}]}
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePersonFromPageSummaries("Jay Chou", List.of(
                new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A")
        ));

        assertThat(profile.getResolvedName()).isEqualTo("Jay Chou");
        assertThat(profile.getSummary()).isEqualTo("Mandopop singer-songwriter");
        assertThat(profile.getKeyFacts()).containsExactly("Fact A");
        assertThat(profile.getTags()).containsExactly("singer");
        assertThat(profile.getEvidenceUrls()).containsExactly("https://example.com/a");
    }

    @Test
    void shouldParseFinalProfileWhenDeepSeekWrapsJsonWithExplanation() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "我先整理如下：\\n```json\\n{\\\"resolvedName\\\":\\\"雷军\\\",\\\"summary\\\":\\\"企业家与投资人。\\\",\\\"keyFacts\\\":[\\\"小米集团创始人\\\"],\\\"tags\\\":[\\\"科技\\\"],\\\"evidenceUrls\\\":[\\\"https://example.com/lei\\\"]}\\n```"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePersonFromPageSummaries("雷军", List.of(
                new PageSummary().setSourceUrl("https://example.com/lei").setSummary("Summary A")
        ));

        assertThat(profile.getResolvedName()).isEqualTo("雷军");
        assertThat(profile.getSummary()).isEqualTo("企业家与投资人。");
        assertThat(profile.getKeyFacts()).containsExactly("小米集团创始人");
        assertThat(profile.getTags()).containsExactly("科技");
        assertThat(profile.getEvidenceUrls()).containsExactly("https://example.com/lei");
    }

    @Test
    void shouldParseSectionSummaryFromDeepSeekXmlFunctionCalls() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "<function_calls><invoke name=\\"submit_section_summary\\"><parameter name=\\"summary\\" string=\\"true\\">雷军的职业发展集中在金山、小米及投资布局。</parameter></invoke></function_calls>"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        String summary = client.summarizeSectionFromPageSummaries("雷军", "career", List.of(
                new PageSummary().setSourceUrl("https://example.com/lei").setSummary("Summary A")
        ));

        assertThat(summary).isEqualTo("雷军的职业发展集中在金山、小米及投资布局。");
    }

    @Test
    void shouldRetryWhenDeepSeekTimesOut() {
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
                        {"choices":[{"message":{"content":"{\\"resolvedName\\":\\"Jay Chou\\",\\"summary\\":\\"Mandopop singer-songwriter\\",\\"evidenceUrls\\":[\\"https://example.com/a\\"]}"}}]}
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key", 2, 1L), new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePersonFromPageSummaries("Jay Chou", List.of(
                new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A")
        ));

        assertThat(profile.getResolvedName()).isEqualTo("Jay Chou");
        assertThat(profile.getSummary()).isEqualTo("Mandopop singer-songwriter");
        server.verify();
    }

    @Test
    void shouldThrowControlledExceptionWhenFinalProfileResponseIsInvalid() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"not-json"}}]}
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        assertThatThrownBy(() -> client.summarizePersonFromPageSummaries("Jay Chou", List.of(
                new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A")
        )))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("INVALID_RESPONSE");
    }

    @Test
    void shouldThrowControlledExceptionWhenDeepSeekReturnsPlainTextForFinalProfile() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "我需要从提供的篇级摘要中提取关于雷军的完整信息，但现在无法稳定输出。"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        assertThatThrownBy(() -> client.summarizePersonFromPageSummaries("雷军", List.of(
                new PageSummary().setSourceUrl("https://example.com/lei").setSummary("Summary A")
        )))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("INVALID_RESPONSE")
                .hasMessageContaining("未返回 JSON");
    }

    @Test
    void shouldParsePageSummaryWhenDeepSeekWrapsJsonWithExplanation() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "我先给出结果：\\n```json\\n{\\\"resolvedNameCandidate\\\":\\\"雷军\\\",\\\"summary\\\":\\\"企业家。\\\",\\\"keyFacts\\\":[\\\"小米集团创始人\\\"],\\\"tags\\\":[\\\"商业\\\"],\\\"sourceUrl\\\":\\\"https://example.com/lei\\\",\\\"title\\\":\\\"Lei Jun\\\"}\\n```"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        PageSummary summary = client.summarizePage("雷军", new PageContent()
                .setUrl("https://example.com/lei")
                .setTitle("Lei Jun")
                .setContent("body"));

        assertThat(summary.getResolvedNameCandidate()).isEqualTo("雷军");
        assertThat(summary.getSummary()).isEqualTo("企业家。");
        assertThat(summary.getKeyFacts()).containsExactly("小米集团创始人");
        assertThat(summary.getTags()).containsExactly("商业");
        assertThat(summary.getSourceUrl()).isEqualTo("https://example.com/lei");
    }

    @Test
    void shouldThrowControlledExceptionWhenDeepSeekReturnsPlainTextRefusalForPageSummary() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "我需要去思考，暂时无法直接给出结构化摘要。"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        assertThatThrownBy(() -> client.summarizePage("雷军", new PageContent()
                .setUrl("https://example.com/lei")
                .setTitle("Lei Jun")
                .setContent("body")))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("INVALID_RESPONSE")
                .hasMessageContaining("未返回 JSON");
    }

    private ApiProperties createProperties(String apiKey) {
        return createProperties(apiKey, 1, 300L);
    }

    private ApiProperties createProperties(String apiKey, int maxRetries, long backoffInitialMs) {
        ApiProperties properties = new ApiProperties();
        properties.getApi().setDeepseek(new DeepSeekApiProperties());
        properties.getApi().getDeepseek().setBaseUrl("https://www.sophnet.com/api/open-apis/v1/chat/completions");
        properties.getApi().getDeepseek().setApiKey(apiKey);
        properties.getApi().getDeepseek().setModel("DeepSeek-V3.2-Fast");
        properties.getApi().getDeepseek().setMaxRetries(maxRetries);
        properties.getApi().getDeepseek().setBackoffInitialMs(backoffInitialMs);
        return properties;
    }
}
