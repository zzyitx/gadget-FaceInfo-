package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.KimiApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SectionSummaryItem;
import com.example.face2info.entity.internal.SectionedSummary;
import com.example.face2info.entity.internal.TopicExpansionDecision;
import com.example.face2info.entity.internal.TopicExpansionQuery;
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
    void shouldParseTopicExpansionDecision() {
        TopicExpansionDecision decision = new TopicExpansionDecision()
                .setShouldExpand(true)
                .setExpansionQueries(List.of(
                        new TopicExpansionQuery().setTerm("官网邮箱").setSection("contact_information").setReason("资料提到官网联系页")
                ));

        assertThat(decision.getShouldExpand()).isTrue();
        assertThat(decision.getExpansionQueries()).hasSize(1);
        assertThat(decision.getExpansionQueries().get(0).getTerm()).isEqualTo("官网邮箱");
    }

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
                                      "arguments": "{\\"summary\\":\\"Singer\\",\\"keyFacts\\":[\\"Fact A\\"],\\"tags\\":[\\"music\\"],\\"sourceUrl\\":\\"https://example.com/a\\"}"
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
                                "content": "{\\"summary\\":\\"Singer\\",\\"keyFacts\\":[\\"Fact A\\"],\\"tags\\":[\\"music\\"],\\"sourceUrl\\":\\"https://example.com/a\\"}"
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

        assertThat(summary.getSummary()).isEqualTo("Singer");
        assertThat(summary.getKeyFacts()).containsExactly("Fact A");
        assertThat(summary.getTags()).containsExactly("music");
        assertThat(summary.getSourceUrl()).isEqualTo("https://example.com/a");
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
    void shouldRecoverContaminatedFinalProfileFieldsFromSummaryPayload() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"resolvedName\\":\\"黄仁勋\\",\\"summary\\":\\"黄仁勋作为NVIDIA公司的创始人、总裁兼首席执行官，自1993年创立公司以来一直担任领导职务。</｜DSML｜parameter> <｜DSML｜parameter name=\\\\\\"educationSummary\\\\\\" string=\\\\\\"true\\\\\\">1983年毕业于俄勒冈州立大学电气工程专业，获得学士学位；1990年获斯坦福大学电气工程硕士学位。</｜DSML｜parameter> <｜DSML｜parameter name=\\\\\\"officialWebsite\\\\\\" string=\\\\\\"true\\\\\\">https://www.nvidia.cn/newsroom/bios/jensen-huang/</｜DSML｜parameter> <｜DSML｜parameter name=\\\\\\"keyFacts\\\\\\" string=\\\\\\"false\\\\\\">[\\\\\\"1993年创立NVIDIA公司\\\\\\",\\\\\\"2006年推出CUDA并行计算平台\\\\\\"]</｜DSML｜parameter> <｜DSML｜parameter name=\\\\\\"basicInfo\\\\\\" string=\\\\\\"false\\\\\\">{\\\\\\"birthDate\\\\\\": \\\\\\"1963年2月17日\\\\\\", \\\\\\"education\\\\\\": [\\\\\\"俄勒冈州立大学电气工程学士\\\\\\"], \\\\\\"occupations\\\\\\": [\\\\\\"NVIDIA创始人、董事长兼首席执行官\\\\\\"], \\\\\\"biographies\\\\\\": [\\\\\\"台湾裔美国籍企业家\\\\\\"]}</｜DSML｜parameter>\\"}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePersonFromPageSummaries("黄仁勋", List.of(
                new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A")
        ));

        assertThat(profile.getResolvedName()).isEqualTo("黄仁勋");
        assertThat(profile.getSummary()).isEqualTo("黄仁勋作为NVIDIA公司的创始人、总裁兼首席执行官，自1993年创立公司以来一直担任领导职务。");
        assertThat(profile.getEducationSummary()).isEqualTo("1983年毕业于俄勒冈州立大学电气工程专业，获得学士学位；1990年获斯坦福大学电气工程硕士学位。");
        assertThat(profile.getOfficialWebsite()).isEqualTo("https://www.nvidia.cn/newsroom/bios/jensen-huang/");
        assertThat(profile.getKeyFacts()).containsExactly("1993年创立NVIDIA公司", "2006年推出CUDA并行计算平台");
        assertThat(profile.getBasicInfo().getBirthDate()).isEqualTo("1963年2月17日");
        assertThat(profile.getBasicInfo().getEducation()).containsExactly("俄勒冈州立大学电气工程学士");
        assertThat(profile.getBasicInfo().getOccupations()).containsExactly("NVIDIA创始人、董事长兼首席执行官");
        assertThat(profile.getBasicInfo().getBiographies()).containsExactly("台湾裔美国籍企业家");
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
    void shouldExpandQueriesFromPageSummaries() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"shouldExpand\\":true,\\"expansionQueries\\":[{\\"term\\":\\"官网邮箱\\",\\"section\\":\\"contact_information\\",\\"reason\\":\\"资料提到官网联系页\\"}]}"}}]}
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        TopicExpansionDecision decision = client.expandTopicQueriesFromPageSummaries(
                "黄仁勋",
                "contact_information",
                List.of(new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("官网联系页"))
        );

        assertThat(decision.getShouldExpand()).isTrue();
        assertThat(decision.getExpansionQueries()).extracting(TopicExpansionQuery::getTerm)
                .containsExactly("官网邮箱");
    }

    @Test
    void shouldSummarizeFamilyMemberSectionedSummary() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"sections\\":[{\\"section\\":\\"家庭成员\\",\\"summary\\":\\"公开资料显示其已婚并育有子女。\\"}]}"}}]}
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        SectionedSummary summary = client.summarizeSectionedSectionFromPageSummaries(
                "黄仁勋",
                "family_member_situation",
                List.of(new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("已婚并育有子女"))
        );

        assertThat(summary.getSections()).hasSize(1);
        assertThat(summary.getSections().get(0).getSection()).isEqualTo("家庭成员");
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
                                "content": "{\\"resolvedName\\":\\"Jay Chou\\",\\"summary\\":\\"Jay Chou is a Mandopop singer-songwriter.\\",\\"keyFacts\\":[\\"Fact A\\"],\\"tags\\":[\\"singer\\",\\"producer\\"],\\"evidenceUrls\\":[\\"https://example.com/a\\"]}"
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
