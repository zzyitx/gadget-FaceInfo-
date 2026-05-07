package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.RocketReachProperties;
import com.example.face2info.entity.response.SocialAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RocketReachClientImplTest {

    @Test
    void shouldSearchPeopleAndMapProfileLinksAsSuspectedAccounts() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://api.rocketreach.co/api/v2/person/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Api-Key", "rocket-key"))
                .andExpect(content().json("""
                        {"query":{"name":["Jensen Huang"]},"start":1,"page_size":3}
                        """))
                .andRespond(withSuccess("""
                        {
                          "people": [
                            {
                              "name": "Jensen Huang",
                              "linkedin_url": "https://www.linkedin.com/in/jenhsunhuang",
                              "social_links": [
                                {"platform": "twitter", "url": "https://x.com/nvidia"}
                              ]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        RocketReachClientImpl client = new RocketReachClientImpl(restTemplate, new ObjectMapper(), createProperties());

        List<SocialAccount> accounts = client.findProfileAccounts("Jensen Huang");

        assertThat(accounts)
                .extracting(SocialAccount::getSource, SocialAccount::getPlatform, SocialAccount::getUrl, SocialAccount::getSuspected)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("rocketreach", "linkedin", "https://www.linkedin.com/in/jenhsunhuang", true),
                        org.assertj.core.groups.Tuple.tuple("rocketreach", "twitter", "https://x.com/nvidia", true)
                );
        server.verify();
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        RocketReachProperties rocketreach = new RocketReachProperties();
        rocketreach.setEnabled(true);
        rocketreach.setBaseUrl("https://api.rocketreach.co/api/v2");
        rocketreach.setPersonSearchPath("/person/search");
        rocketreach.setApiKey("rocket-key");
        rocketreach.setMaxResults(3);
        rocketreach.setMaxRetries(1);
        properties.getApi().setRocketreach(rocketreach);
        return properties;
    }
}
