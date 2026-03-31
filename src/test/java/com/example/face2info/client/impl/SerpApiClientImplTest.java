package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.SerpApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SerpApiClientImplTest {

    @Test
    void shouldUseYandexReverseImageEngineAndTabParameter() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://serpapi.com/search.json?engine=yandex_images&url=https%3A%2F%2Fexample.com%2Fimage.png&tab=about&api_key=test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"image_results\":[]}", MediaType.APPLICATION_JSON));

        SerpApiClientImpl client = new SerpApiClientImpl(restTemplate, new ObjectMapper(), createProperties());

        client.reverseImageSearchByUrlYandex("https://example.com/image.png", "about");

        server.verify();
    }

    @Test
    void shouldUseBingReverseImageEngineAndImageUrlParameter() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://serpapi.com/search.json?engine=bing_reverse_image&image_url=https%3A%2F%2Fi.imgur.com%2FHBrB8p0.png&mkt=en-US&api_key=test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"image_results\":[]}", MediaType.APPLICATION_JSON));

        SerpApiClientImpl client = new SerpApiClientImpl(restTemplate, new ObjectMapper(), createProperties());

        client.reverseImageSearchByUrlBing("https://i.imgur.com/HBrB8p0.png");

        server.verify();
    }

    @Test
    void shouldNormalizeEncodedBingImageQueryBeforeRequest() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://serpapi.com/search.json?engine=bing_images&q=Lei%20Jun&mkt=en-US&api_key=test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"image_results\":[]}", MediaType.APPLICATION_JSON));

        SerpApiClientImpl client = new SerpApiClientImpl(restTemplate, new ObjectMapper(), createProperties());

        client.searchBingImages("Lei%20Jun");

        server.verify();
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().setSerp(new SerpApiProperties());
        properties.getApi().getSerp().setBaseUrl("https://serpapi.com/search.json");
        properties.getApi().getSerp().setApiKey("test-key");
        properties.getApi().getSerp().setMaxRetries(1);
        properties.getApi().getSerp().setBingMarket("en-US");
        return properties;
    }
}
