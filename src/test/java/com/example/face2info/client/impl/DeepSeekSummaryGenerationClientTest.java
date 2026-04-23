package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.DeepSeekApiProperties;
import com.example.face2info.entity.internal.ArticleCitation;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.PageSummary;
import com.example.face2info.entity.internal.ParagraphSource;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.entity.internal.SearchLanguageInferenceResult;
import com.example.face2info.entity.internal.SectionedSummary;
import com.example.face2info.entity.internal.TopicExpansionDecision;
import com.example.face2info.entity.internal.TopicExpansionQuery;
import com.example.face2info.exception.ApiCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.client.MockClientHttpRequest;
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
    void shouldParseSearchLanguageInferenceResult() {
        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(new RestTemplate(), createProperties("test-key"), new ObjectMapper());

        SearchLanguageInferenceResult result = client.parseSearchLanguageInferenceResult("""
                {"primaryNationality":"JP","recommendedLanguages":["zh","en","ja"],"localizedNames":{"zh":"宫崎骏","en":"Hayao Miyazaki","ja":"宮崎 駿"},"reason":"biography mentions Japanese animator","confidence":0.88}
                """);

        assertThat(result.getPrimaryNationality()).isEqualTo("JP");
        assertThat(result.getRecommendedLanguages()).containsExactly("zh", "en", "ja");
        assertThat(result.getLocalizedNames().get("ja")).isEqualTo("宮崎 駿");
        assertThat(result.getConfidence()).isEqualTo(0.88);
    }

    @Test
    void shouldParsePageSummaryFromDeepSeekResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"summary\\":\\"Singer\\",\\"keyFacts\\":[\\"Fact A\\"],\\"tags\\":[\\"music\\"],\\"sourceUrl\\":\\"https://example.com/a\\",\\"title\\":\\"A\\"}"}}]}
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        PageSummary summary = client.summarizePage("Jay Chou", new PageContent()
                .setUrl("https://example.com/a")
                .setTitle("A")
                .setContent("body"));

        assertThat(summary.getSummary()).isEqualTo("Singer");
        assertThat(summary.getKeyFacts()).containsExactly("Fact A");
        assertThat(summary.getTags()).containsExactly("music");
    }

    @Test
    void shouldTruncateLongPageContentBeforeSendingPageSummaryRequest() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString().toString();
                    assertThat(body).contains("1234567890");
                    assertThat(body).contains("正文过长，以下内容已按长度截断");
                    assertThat(body).doesNotContain("ABCDEFGHIJ");
                })
                .andRespond(withSuccess("""
                        {\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"Singer\\\",\\\"keyFacts\\\":[\\\"Fact A\\\"],\\\"tags\\\":[\\\"music\\\"],\\\"sourceUrl\\\":\\\"https://example.com/a\\\",\\\"title\\\":\\\"A\\\"}\"}}]}
                        """, MediaType.APPLICATION_JSON));

        ApiProperties properties = createProperties("test-key");
        properties.getApi().getSummary().setPageContentMaxLength(10);
        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, properties, new ObjectMapper());

        client.summarizePage("Jay Chou", new PageContent()
                .setUrl("https://example.com/a")
                .setTitle("A")
                .setContent("1234567890ABCDEFGHIJ"));

        server.verify();
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
    void shouldInstructDeepSeekToGenerateInlineCitationsWithoutSourceList() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).contains("格式为 [n]");
                    assertThat(body).contains("不要生成引用来源列表");
                    assertThat(body).contains("文章编号表");
                    assertThat(body).contains("sourceIds");
                    assertThat(body).contains("sources");
                })
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"resolvedName\\":\\"Jay Chou\\",\\"summary\\":\\"主体摘要\\",\\"summaryParagraphs\\":[{\\"text\\":\\"主体段落[1]\\",\\"sources\\":[{\\"title\\":\\"文章 A\\",\\"url\\":\\"https://example.com/a\\",\\"source\\":\\"Example\\"}]}]}"}}]}
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePersonFromPageSummaries("Jay Chou", List.of(
                new PageSummary().setSourceUrl("https://example.com/a").setTitle("A").setSummary("Summary A")
        ));

        assertThat(profile.getSummaryParagraphs()).hasSize(1);
        assertThat(profile.getSummaryParagraphs().get(0).getText()).isEqualTo("主体段落[1]");
    }

    @Test
    void shouldParseSourceIdsAndArticleSourcesFromDeepSeekFinalProfile() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"resolvedName\\":\\"Lei Jun\\",\\"summary\\":\\"主体摘要\\",\\"summaryParagraphs\\":[{\\"text\\":\\"主体信息[1]\\",\\"sourceIds\\":[1],\\"sourceUrls\\":[\\"https://example.com/a\\"]}],\\"articleSources\\":[{\\"id\\":1,\\"title\\":\\"文章 A\\",\\"url\\":\\"https://example.com/a\\",\\"source\\":\\"Example\\"}]}"}}]}
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePersonFromPageSummaries("Lei Jun", List.of(
                new PageSummary().setSourceId(1).setSourceUrl("https://example.com/a").setTitle("A").setSummary("Summary A")
        ));

        assertThat(profile.getSummaryParagraphs()).hasSize(1);
        assertThat(profile.getSummaryParagraphs().get(0).getSourceIds()).containsExactly(1);
        assertThat(profile.getArticleSources()).hasSize(1);
        ArticleCitation source = profile.getArticleSources().get(0);
        assertThat(source.getId()).isEqualTo(1);
        assertThat(source.getUrl()).isEqualTo("https://example.com/a");
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
    void shouldExpandQueriesFromDeepSeekResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"shouldExpand\\":true,\\"expansionQueries\\":[{\\"term\\":\\"监管通报\\",\\"section\\":\\"misconduct\\",\\"reason\\":\\"存在监管线索\\"}]}"}}]}
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        TopicExpansionDecision decision = client.expandTopicQueriesFromPageSummaries(
                "雷军",
                "misconduct",
                List.of(new PageSummary().setSourceUrl("https://example.com/a").setSummary("存在监管线索"))
        );

        assertThat(decision.getShouldExpand()).isTrue();
        assertThat(decision.getExpansionQueries()).extracting(TopicExpansionQuery::getTerm)
                .containsExactly("监管通报");
    }

    @Test
    void shouldParseSectionedFamilySummaryFromDeepSeekResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"sections\\":[{\\"section\\":\\"家庭成员\\",\\"summary\\":\\"公开资料显示其已婚。\\",\\"sourceUrls\\":[\\"https://example.com/a\\"]}]}"}}]}
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        SectionedSummary summary = client.summarizeSectionedSectionFromPageSummaries(
                "雷军",
                "family_member_situation",
                List.of(new PageSummary().setSourceUrl("https://example.com/a").setSummary("公开资料显示其已婚"))
        );

        assertThat(summary.getSections()).hasSize(1);
        assertThat(summary.getSections().get(0).getSummary()).isEqualTo("公开资料显示其已婚。");
        assertThat(summary.getSections().get(0).getSourceUrls()).containsExactly("https://example.com/a");
    }

    @Test
    void shouldParseParagraphSourcesFromDeepSeekFinalProfile() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"resolvedName\\":\\"Lei Jun\\",\\"summary\\":\\"主体摘要\\",\\"summaryParagraphs\\":[{\\"text\\":\\"第一段主体信息。\\",\\"sourceUrls\\":[\\"https://example.com/a\\"]}],\\"careerSummaryParagraphs\\":[{\\"text\\":\\"第一段职业经历。\\",\\"sourceUrls\\":[\\"https://example.com/b\\"]}]}"} }]}
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePersonFromPageSummaries("Lei Jun", List.of(
                new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A"),
                new PageSummary().setSourceUrl("https://example.com/b").setSummary("Summary B")
        ));

        assertThat(profile.getSummaryParagraphs()).hasSize(1);
        assertThat(profile.getSummaryParagraphs().get(0).getText()).isEqualTo("第一段主体信息。");
        assertThat(profile.getSummaryParagraphs().get(0).getSourceUrls()).containsExactly("https://example.com/a");
        assertThat(profile.getCareerSummaryParagraphs()).hasSize(1);
        assertThat(profile.getCareerSummaryParagraphs().get(0).getSourceUrls()).containsExactly("https://example.com/b");
    }

    @Test
    void shouldParseStructuredSourcesObjectsFromDeepSeekFinalProfile() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\\"resolvedName\\\":\\\"Lei Jun\\\",\\\"summary\\\":\\\"主体摘要\\\",\\\"summaryParagraphs\\\":[{\\\"text\\\":\\\"第一段主体信息。\\\",\\\"sources\\\":[{\\\"title\\\":\\\"文章 A\\\",\\\"url\\\":\\\"https://example.com/a\\\",\\\"source\\\":\\\"Example\\\",\\\"publishedAt\\\":\\\"2025-01-02\\\"}]}]}"}}]}
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePersonFromPageSummaries("Lei Jun", List.of(
                new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A")
        ));

        assertThat(profile.getSummaryParagraphs()).hasSize(1);
        assertThat(profile.getSummaryParagraphs().get(0).getText()).isEqualTo("第一段主体信息。");
        assertThat(profile.getSummaryParagraphs().get(0).getSources()).hasSize(1);
        ParagraphSource source = profile.getSummaryParagraphs().get(0).getSources().get(0);
        assertThat(source.getTitle()).isEqualTo("文章 A");
        assertThat(source.getUrl()).isEqualTo("https://example.com/a");
        assertThat(source.getSource()).isEqualTo("Example");
        assertThat(source.getPublishedAt()).isEqualTo("2025-01-02");
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
    void shouldParsePageSummaryWhenDeepSeekWrapsJsonWithExplanation() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "我先给出结果：\\n```json\\n{\\\"summary\\\":\\\"企业家。\\\",\\\"keyFacts\\\":[\\\"小米集团创始人\\\"],\\\"tags\\\":[\\\"商业\\\"],\\\"sourceUrl\\\":\\\"https://example.com/lei\\\",\\\"title\\\":\\\"Lei Jun\\\"}\\n```"
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

        assertThat(summary.getSummary()).isEqualTo("企业家。");
        assertThat(summary.getKeyFacts()).containsExactly("小米集团创始人");
        assertThat(summary.getTags()).containsExactly("商业");
        assertThat(summary.getSourceUrl()).isEqualTo("https://example.com/lei");
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

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

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

    @Test
    void shouldFallbackToPlainTextSummaryWhenDeepSeekReturnsMeaningfulNarrativeContent() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "\\n特朗普主义是第45任、47任（现任）美国总统唐纳德·特朗普的意识形态，是民粹主义和民族主义结合的政治风格。特朗普主义包括民粹主义思想策略、注重情感、右翼威权民粹主义、基督教特朗普主义、经济民族主义、贸易保护主义等多种元素。"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        PageSummary summary = client.summarizePage("Steve Martin", new PageContent()
                .setUrl("https://zh.wikipedia.org/zh-cn/%E7%89%B9%E6%9C%97%E6%99%AE%E4%B8%BB%E4%B9%89")
                .setTitle("特朗普主义")
                .setContent("body"));

        assertThat(summary.getSummary()).isEqualTo("特朗普主义是第45任、47任（现任）美国总统唐纳德·特朗普的意识形态，是民粹主义和民族主义结合的政治风格。特朗普主义包括民粹主义思想策略、注重情感、右翼威权民粹主义、基督教特朗普主义、经济民族主义、贸易保护主义等多种元素。");
        assertThat(summary.getSourceUrl()).isEqualTo("https://zh.wikipedia.org/zh-cn/%E7%89%B9%E6%9C%97%E6%99%AE%E4%B8%BB%E4%B9%89");
        assertThat(summary.getTitle()).isEqualTo("特朗普主义");
        assertThat(summary.getKeyFacts()).isEmpty();
        assertThat(summary.getTags()).isEmpty();
    }

    @Test
    void shouldNotRetrySectionedSummaryWhenDeepSeekReturnsInvalidResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "我需要完整的正文才能继续提供帮助。"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key", 2, 1L), new ObjectMapper());

        assertThatThrownBy(() -> client.summarizeSectionedSectionFromPageSummaries(
                "黄仁勋",
                "misconduct",
                List.of(new PageSummary()
                        .setSourceUrl("https://example.com/a")
                        .setTitle("A")
                        .setSummary("根据公开报道整理出的负面事件摘要"))
        ))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("DeepSeek 主题分段摘要 调用失败")
                .hasMessageContaining("INVALID_RESPONSE")
                .hasMessageContaining("未返回 JSON");

        server.verify();
    }

    @Test
    void shouldRetrySectionedSummaryWhenDeepSeekReturnsDegeneratedRepeatedContent() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据根据"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"sections\\":[{\\"section\\":\\"争议与纠纷\\",\\"summary\\":\\"公开资料未见其家庭相关争议纠纷。\\",\\"sourceUrls\\":[\\"https://example.com/a\\"]}]}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        DeepSeekSummaryGenerationClient client =
                new DeepSeekSummaryGenerationClient(restTemplate, createProperties("test-key", 2, 1L), new ObjectMapper());

        SectionedSummary summary = client.summarizeSectionedSectionFromPageSummaries(
                "黄仁勋",
                "family_member_situation",
                List.of(new PageSummary()
                        .setSourceUrl("https://example.com/a")
                        .setTitle("A")
                        .setSummary("根据公开报道整理出的家庭与亲属信息摘要"))
        );

        assertThat(summary.getSections()).hasSize(1);
        assertThat(summary.getSections().get(0).getSection()).isEqualTo("争议与纠纷");
        assertThat(summary.getSections().get(0).getSummary()).isEqualTo("公开资料未见其家庭相关争议纠纷。");

        server.verify();
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
