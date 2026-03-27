package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.JinaApiProperties;
import com.example.face2info.entity.internal.PageContent;
import com.example.face2info.exception.ApiCallException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JinaReaderClientImplTest {

    @Test
    void shouldNormalizePlaceholderBaseUrlAndReadPageViaGet() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://r.jina.ai/https://baike.baidu.com/item/Jay_Chou"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess("Jay Chou profile", MediaType.TEXT_PLAIN));

        JinaReaderClientImpl client = new JinaReaderClientImpl(restTemplate,
                createProperties("https://r.jina.ai/https://www.example.com", "test-key"));

        List<PageContent> pages = client.readPages(List.of("https://baike.baidu.com/item/Jay_Chou", " "));

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).getUrl()).isEqualTo("https://baike.baidu.com/item/Jay_Chou");
        assertThat(pages.get(0).getContent()).isEqualTo("Jay Chou profile");
        assertThat(pages.get(0).getSourceEngine()).isEqualTo("jina");
        server.verify();
    }

    @Test
    void shouldWrapClientErrorIntoApiCallException() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://r.jina.ai/https://baike.baidu.com/item/Jay_Chou"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withBadRequest().body("{\"message\":\"bad url\"}").contentType(MediaType.APPLICATION_JSON));

        JinaReaderClientImpl client = new JinaReaderClientImpl(restTemplate,
                createProperties("https://r.jina.ai/https://", "test-key"));

        assertThatThrownBy(() -> client.readPages(List.of("https://baike.baidu.com/item/Jay_Chou")))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("400 Bad Request");
    }

    @Test
    void shouldRemoveWhitespaceFromOriginalUrlBeforeCallingJina() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://r.jina.ai/https://baike.baidu.com/en/item/LeiJun/985856"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("Lei Jun profile", MediaType.TEXT_PLAIN));

        JinaReaderClientImpl client = new JinaReaderClientImpl(restTemplate,
                createProperties("https://r.jina.ai/https://", "test-key"));

        List<PageContent> pages = client.readPages(List.of("https://baike.baidu.com/en/item/Lei Jun/985856"));

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).getUrl()).isEqualTo("https://baike.baidu.com/en/item/LeiJun/985856");
        assertThat(pages.get(0).getContent()).isEqualTo("Lei Jun profile");
        server.verify();
    }

    private ApiProperties createProperties(String baseUrl, String apiKey) {
        ApiProperties properties = new ApiProperties();
        properties.getApi().setJina(new JinaApiProperties());
        properties.getApi().getJina().setBaseUrl(baseUrl);
        properties.getApi().getJina().setApiKey(apiKey);
        properties.getApi().getJina().setMaxRetries(1);
        return properties;
    }
}
