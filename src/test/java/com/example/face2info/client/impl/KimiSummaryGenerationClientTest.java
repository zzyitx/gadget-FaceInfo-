package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.KimiApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.entity.internal.ResolvedPersonProfile;
import com.example.face2info.exception.ApiCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.util.List;
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
                                "content": "{\\"resolvedName\\":\\"Jay Chou\\",\\"summary\\":\\"Jay Chou is a Mandopop singer-songwriter.\\",\\"tags\\":[\\"singer\\",\\"producer\\"],\\"evidenceUrls\\":[\\"https://example.com/a\\"]}"
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
