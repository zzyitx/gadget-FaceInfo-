package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.GoogleSearchProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleSearchClientImplTest {

    @Test
    void shouldPostGoogleSearchRequestToSerper() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://google.serper.dev/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-KEY", "test-key"))
                .andExpect(content().json("""
                        {"q":"Lei Jun 抖音","hl":"zh-cn"}
                        """))
                .andRespond(withSuccess("{\"organic_results\":[]}", MediaType.APPLICATION_JSON));

        GoogleSearchClientImpl client = new GoogleSearchClientImpl(restTemplate, new ObjectMapper(), createProperties());

        client.googleSearch("Lei%20Jun%20%E6%8A%96%E9%9F%B3");

        server.verify();
    }

    @Test
    void shouldPostGoogleLensRequestToSerper() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://google.serper.dev/lens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-KEY", "test-key"))
                .andExpect(content().json("""
                        {"url":"https://example.com/image.png","hl":"zh-cn"}
                        """))
                .andRespond(withSuccess("{\"visual_matches\":[]}", MediaType.APPLICATION_JSON));

        GoogleSearchClientImpl client = new GoogleSearchClientImpl(restTemplate, new ObjectMapper(), createProperties());

        client.reverseImageSearchByUrl("https://example.com/image.png");

        server.verify();
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().setGoogle(new GoogleSearchProperties());
        properties.getApi().getGoogle().setSearchUrl("https://google.serper.dev/search");
        properties.getApi().getGoogle().setLensUrl("https://google.serper.dev/lens");
        properties.getApi().getGoogle().setApiKey("test-key");
        properties.getApi().getGoogle().setHl("zh-cn");
        properties.getApi().getGoogle().setMaxRetries(1);
        return properties;
    }
}
