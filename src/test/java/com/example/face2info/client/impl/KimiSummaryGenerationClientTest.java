package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.KimiApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.exception.ApiCallException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KimiSummaryGenerationClientTest {

    @Test
    void shouldParseSummaryAndTagsFromKimiResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"resolvedName\\":\\"Jay Chou\\",\\"summary\\":\\"Jay Chou is a Mandopop singer-songwriter.\\",\\"keyFacts\\":[\\"2000年发行专辑《Jay》正式出道\\",\\"2007年执导电影《不能说的秘密》\\"],\\"tags\\":[\\"singer\\",\\"producer\\"],\\"evidenceUrls\\":[\\"https://example.com/a\\"]}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePerson("Jay Chou", List.of(
                new PageContent().setUrl("https://example.com/a").setContent("Page content A")
        ));

        assertThat(profile.getResolvedName()).isEqualTo("Jay Chou");
        assertThat(profile.getSummary()).isEqualTo("Jay Chou is a Mandopop singer-songwriter.");
        assertThat(profile.getKeyFacts()).containsExactly(
                "2000年发行专辑《Jay》正式出道",
                "2007年执导电影《不能说的秘密》"
        );
        assertThat(profile.getTags()).containsExactly("singer", "producer");
        assertThat(profile.getEvidenceUrls()).containsExactly("https://example.com/a");
        server.verify();
    }

    @Test
    void shouldThrowControlledExceptionWhenKimiReturnsInvalidJson() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}", MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        assertThatThrownBy(() -> client.summarizePerson("Jay Chou", List.of(
                new PageContent().setUrl("https://example.com/a").setContent("Page content A")
        )))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("INVALID_RESPONSE");
    }

    @Test
    void shouldParseJsonWrappedInMarkdownCodeFence() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "```json\\n{\\"resolvedName\\":\\"Jay Chou\\",\\"summary\\":\\"Jay Chou is a singer-songwriter.\\",\\"tags\\":[\\"singer\\"],\\"evidenceUrls\\":[\\"https://example.com/a\\"]}\\n```"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(restTemplate, createProperties("test-key"), new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePerson("Jay Chou", List.of(
                new PageContent().setUrl("https://example.com/a").setContent("Page content A")
        ));

        assertThat(profile.getResolvedName()).isEqualTo("Jay Chou");
        assertThat(profile.getSummary()).isEqualTo("Jay Chou is a singer-songwriter.");
        assertThat(profile.getTags()).containsExactly("singer");
        assertThat(profile.getEvidenceUrls()).containsExactly("https://example.com/a");
    }

    /**
     * 手动实测测试：
     * 1.确保本地 Kimi 配置在环境变量或 application.yml 中可用
     * 2.暂时移除@Disabled
     * 3.Run： mvn “-dtest=KimiSummaryGenerationClientTest#shouldPrintRealKimiResponse” test
     */
//    @Disabled("手动测试：移除该注释以调用真实的 Kimi API")
    @Test
    void shouldPrintRealKimiResponse() {
        Properties localConfig = loadLocalApplicationProperties();
        String apiKey = firstNonBlank(
                System.getenv("KIMI_API_KEY"),
                resolveConfigValue(localConfig.getProperty("face2info.api.kimi.api-key"))
        );

        assertThat(apiKey)
                .as("Running this test requires KIMI_API_KEY or face2info.api.kimi.api-key in application.yml")
                .isNotBlank();

        ApiProperties properties = createProperties(apiKey);
        properties.getApi().getKimi().setBaseUrl(firstNonBlank(
                System.getenv("KIMI_API_BASE_URL"),
                resolveConfigValue(localConfig.getProperty("face2info.api.kimi.base-url")),
                properties.getApi().getKimi().getBaseUrl()
        ));
        properties.getApi().getKimi().setModel(firstNonBlank(
                System.getenv("KIMI_MODEL"),
                resolveConfigValue(localConfig.getProperty("face2info.api.kimi.model")),
                properties.getApi().getKimi().getModel()
        ));

        KimiSummaryGenerationClient client =
                new KimiSummaryGenerationClient(new RestTemplate(), properties, new ObjectMapper());

        ResolvedPersonProfile profile = client.summarizePerson("Jay Chou", List.of(
                new PageContent()
                        .setUrl("https://example.com/profile")
                        .setContent("Jay Chou is a Mandopop singer-songwriter, producer, actor, and director.")
        ));

        System.out.println("resolvedName=" + profile.getResolvedName());
        System.out.println("summary=" + profile.getSummary());
        System.out.println("tags=" + profile.getTags());
        System.out.println("evidenceUrls=" + profile.getEvidenceUrls());

        assertThat(profile.getResolvedName()).isNotBlank();
        assertThat(profile.getSummary()).isNotBlank();
    }

//    @Disabled("手动测试：需要有效的 Kimi 真实配置后再执行")
    @Test
    void shouldPrintRawLeiJunArticleResponseWithoutPrompt() {
        Properties localConfig = loadLocalApplicationProperties();
        String apiKey = firstNonBlank(
                System.getenv("KIMI_API_KEY"),
                resolveConfigValue(localConfig.getProperty("face2info.api.kimi.api-key"))
        );

        assertThat(apiKey)
                .as("Running this test requires KIMI_API_KEY or face2info.api.kimi.api-key in application.yml")
                .isNotBlank();

        ApiProperties properties = createProperties(apiKey);
        properties.getApi().getKimi().setBaseUrl(firstNonBlank(
                System.getenv("KIMI_API_BASE_URL"),
                resolveConfigValue(localConfig.getProperty("face2info.api.kimi.base-url")),
                properties.getApi().getKimi().getBaseUrl()
        ));
        properties.getApi().getKimi().setModel(firstNonBlank(
                System.getenv("KIMI_MODEL"),
                resolveConfigValue(localConfig.getProperty("face2info.api.kimi.model")),
                properties.getApi().getKimi().getModel()
        ));

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String articleContent = """
                https://r.jina.ai/https://baike.baidu.com/en/item/Lei%20Jun/985856

                Lei Jun, born on December 16, 1969 in Xiantao, Hubei Province, is a Chinese entrepreneur and investor.
                He graduated from Wuhan University with a bachelor's degree in computer science in 1991.
                After graduation, he joined Kingsoft and became general manager of Beijing Kingsoft Software in 1998.
                He later served as president and chief executive officer of Kingsoft, leading the company to list in Hong Kong in 2007.
                In 2010, Lei Jun founded Xiaomi Technology and focused on smartphones, IoT products, and consumer electronics.
                Xiaomi released its first mobile phone in 2011 and rapidly grew into one of China's most influential technology brands.
                The article highlights his path from Wuhan University to Kingsoft and then to founding Xiaomi, with milestone events including Kingsoft's listing and Xiaomi's expansion.
                """;

        Map<String, Object> requestBody = Map.of(
                "model", properties.getApi().getKimi().getModel(),
                "messages", List.of(
                        Map.of("role", "user", "content", articleContent)
                )
        );

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                properties.getApi().getKimi().getBaseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                JsonNode.class
        );

        String rawContent = response.getBody()
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText();

        System.out.println("rawResponse=" + rawContent);

        assertThat(rawContent).isNotBlank();
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

    private Properties loadLocalApplicationProperties() {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new FileSystemResource(Path.of("src", "main", "resources", "application.yml")));
        Properties properties = factoryBean.getObject();
        return properties == null ? new Properties() : properties;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String resolveConfigValue(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (!value.startsWith("${") || !value.endsWith("}")) {
            return value;
        }

        String expression = value.substring(2, value.length() - 1);
        int separatorIndex = expression.indexOf(':');
        if (separatorIndex < 0) {
            return value;
        }

        String envName = expression.substring(0, separatorIndex);
        String defaultValue = expression.substring(separatorIndex + 1);
        return firstNonBlank(System.getenv(envName), defaultValue);
    }
}
