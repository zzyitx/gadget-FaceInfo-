package com.example.face2info.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestTemplateConfigTest {

    @Test
    void shouldExcludeFacecheckTimeoutWhenCalculatingConnectTimeout() {
        ApiProperties properties = createPropertiesWithFacecheckOutlier();
        RestTemplateConfig config = new RestTemplateConfig();

        int timeout = config.resolveConnectTimeout(properties);

        assertThat(timeout).isEqualTo(4000);
    }

    @Test
    void shouldExcludeFacecheckTimeoutWhenCalculatingReadTimeout() {
        ApiProperties properties = createPropertiesWithFacecheckOutlier();
        RestTemplateConfig config = new RestTemplateConfig();

        int timeout = config.resolveReadTimeout(properties);

        assertThat(timeout).isEqualTo(9000);
    }

    private ApiProperties createPropertiesWithFacecheckOutlier() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().getSerp().setConnectTimeoutMs(3000);
        properties.getApi().getGoogle().setConnectTimeoutMs(3500);
        properties.getApi().getNews().setConnectTimeoutMs(2000);
        properties.getApi().getJina().setConnectTimeoutMs(2500);
        properties.getApi().getKimi().setConnectTimeoutMs(4000);
        properties.getApi().getSummary().setConnectTimeoutMs(2800);
        properties.getApi().getFaceDetection().setConnectTimeoutMs(1500);
        properties.getApi().getFaceEnhance().setConnectTimeoutMs(1800);
        properties.getApi().getFacecheck().setConnectTimeoutMs(60000);

        properties.getApi().getSerp().setReadTimeoutMs(6000);
        properties.getApi().getGoogle().setReadTimeoutMs(7000);
        properties.getApi().getNews().setReadTimeoutMs(5000);
        properties.getApi().getJina().setReadTimeoutMs(8000);
        properties.getApi().getKimi().setReadTimeoutMs(9000);
        properties.getApi().getSummary().setReadTimeoutMs(7500);
        properties.getApi().getFaceDetection().setReadTimeoutMs(3000);
        properties.getApi().getFaceEnhance().setReadTimeoutMs(4000);
        properties.getApi().getFacecheck().setReadTimeoutMs(120000);
        return properties;
    }
}
