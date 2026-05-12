package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.RestTemplateConfig;
import com.example.face2info.entity.internal.PageSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("真实大模型网关连通性测试")
class RealModelGatewaySmokeTest {

    @DisplayName("DeepSeek 实时翻译应真实调用 application.yml 中的大模型接口")
    @Test
    void shouldCallRealDeepSeekRealtimeTranslationApi() {
        assumeRealGatewaySmokeEnabled();
        ApiProperties properties = loadLocalApplicationProperties();
        assumeTrue(hasText(properties.getApi().getRealtimeTranslation().getBaseUrl()),
                "未配置 REALTIME_TRANSLATION_API_BASE_URL 或本地默认 base-url");
        assumeTrue(hasText(properties.getApi().getRealtimeTranslation().getApiKey()),
                "未配置 SOPHNET_API_KEY 或 realtime-translation api-key");

        DeepSeekRealtimeTranslationClient client = new DeepSeekRealtimeTranslationClient(
                buildRestTemplate(properties),
                properties,
                new ObjectMapper()
        );

        String translated = client.translateQuery("Jensen Huang China related statements", "fr");

        System.out.println("真实翻译模型返回: " + translated);
        assertThat(translated).isNotBlank();
    }

    @DisplayName("DeepSeek 总结应真实调用 application.yml 中的大模型接口并返回模型输出")
    @Test
    void shouldCallRealDeepSeekSummaryApi() {
        assumeRealGatewaySmokeEnabled();
        ApiProperties properties = loadLocalApplicationProperties();
        assumeTrue(hasText(properties.getApi().getDeepseek().getBaseUrl()),
                "未配置 DEEPSEEK_API_BASE_URL 或本地默认 base-url");
        assumeTrue(hasText(properties.getApi().getDeepseek().getApiKey()),
                "未配置 SOPHNET_API_KEY 或 deepseek api-key");

        DeepSeekSummaryGenerationClient client = new DeepSeekSummaryGenerationClient(
                buildRestTemplate(properties),
                properties,
                new ObjectMapper()
        );

        String summary = client.summarizeSectionFromPageSummaries(
                "Jensen Huang",
                "career",
                List.of(new PageSummary()
                        .setSourceUrl("https://www.nvidia.com/en-us/about-nvidia/leadership/jensen-huang/")
                        .setTitle("Jensen Huang")
                        .setSummary("Jensen Huang is the founder and CEO of NVIDIA."))
        );

        System.out.println("真实总结模型返回: " + summary);
        assertThat(summary).isNotBlank();
    }

    private RestTemplate buildRestTemplate(ApiProperties properties) {
        return new RestTemplateConfig().restTemplate(properties);
    }

    private void assumeRealGatewaySmokeEnabled() {
        // 真实网关烟测依赖本地网络和模型额度，默认跳过以保证常规单测可重复。
        assumeTrue("true".equalsIgnoreCase(System.getProperty("face2info.realModelGatewaySmoke"))
                        || "true".equalsIgnoreCase(System.getenv("FACE2INFO_REAL_MODEL_GATEWAY_SMOKE")),
                "未启用真实大模型网关烟测");
    }

    private ApiProperties loadLocalApplicationProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        try {
            loader.load("application.yml", new FileSystemResource("src/main/resources/application.yml"))
                    .forEach(propertySource -> environment.getPropertySources().addLast(propertySource));
        } catch (IOException ex) {
            throw new IllegalStateException("读取本地 application.yml 失败", ex);
        }
        return Binder.get(environment)
                .bind("face2info", ApiProperties.class)
                .orElseThrow(() -> new IllegalStateException("绑定 face2info 配置失败"));
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
