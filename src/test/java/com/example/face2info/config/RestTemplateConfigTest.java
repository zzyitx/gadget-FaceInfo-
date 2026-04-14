package com.example.face2info.config;

import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void shouldUseDedicatedKimiConnectTimeout() {
        ApiProperties properties = createPropertiesWithKimiAndFaceEnhanceOutliers();
        RestTemplateConfig config = new RestTemplateConfig();

        int timeout = config.resolveKimiConnectTimeout(properties);

        assertThat(timeout).isEqualTo(12000);
    }

    @Test
    void shouldUseDedicatedKimiReadTimeout() {
        ApiProperties properties = createPropertiesWithKimiAndFaceEnhanceOutliers();
        RestTemplateConfig config = new RestTemplateConfig();

        int timeout = config.resolveKimiReadTimeout(properties);

        assertThat(timeout).isEqualTo(75000);
    }

    @Test
    void shouldExposeDeepSeekAndSummaryRoutingProperties() {
        ApiProperties properties = new ApiProperties();

        properties.getApi().getDeepseek().setBaseUrl("https://www.sophnet.com/api/open-apis/v1/chat/completions");
        properties.getApi().getDeepseek().setModel("DeepSeek-V3.2-Fast");
        properties.getApi().getSummary().setPageRoutingEnabled(true);
        properties.getApi().getSummary().setLongContentThreshold(4000);
        properties.getApi().getSummary().setStructuredPageKeywords(List.of("简历", "履历", "获奖"));

        assertThat(properties.getApi().getDeepseek().getBaseUrl())
                .isEqualTo("https://www.sophnet.com/api/open-apis/v1/chat/completions");
        assertThat(properties.getApi().getDeepseek().getModel()).isEqualTo("DeepSeek-V3.2-Fast");
        assertThat(properties.getApi().getSummary().isPageRoutingEnabled()).isTrue();
        assertThat(properties.getApi().getSummary().getLongContentThreshold()).isEqualTo(4000);
        assertThat(properties.getApi().getSummary().getStructuredPageKeywords()).contains("履历");
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

    private ApiProperties createPropertiesWithKimiAndFaceEnhanceOutliers() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().getSerp().setConnectTimeoutMs(3000);
        properties.getApi().getGoogle().setConnectTimeoutMs(3500);
        properties.getApi().getNews().setConnectTimeoutMs(2000);
        properties.getApi().getJina().setConnectTimeoutMs(2500);
        properties.getApi().getKimi().setConnectTimeoutMs(12000);
        properties.getApi().getSummary().setConnectTimeoutMs(2800);
        properties.getApi().getFaceDetection().setConnectTimeoutMs(1500);
        properties.getApi().getFaceEnhance().setConnectTimeoutMs(60000);
        properties.getApi().getFacecheck().setConnectTimeoutMs(1000);

        properties.getApi().getSerp().setReadTimeoutMs(6000);
        properties.getApi().getGoogle().setReadTimeoutMs(7000);
        properties.getApi().getNews().setReadTimeoutMs(5000);
        properties.getApi().getJina().setReadTimeoutMs(8000);
        properties.getApi().getKimi().setReadTimeoutMs(75000);
        properties.getApi().getSummary().setReadTimeoutMs(7500);
        properties.getApi().getFaceDetection().setReadTimeoutMs(3000);
        properties.getApi().getFaceEnhance().setReadTimeoutMs(90000);
        properties.getApi().getFacecheck().setReadTimeoutMs(120000);
        return properties;
    }
}
