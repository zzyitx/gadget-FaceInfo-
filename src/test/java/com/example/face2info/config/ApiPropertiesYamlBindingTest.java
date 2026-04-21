package com.example.face2info.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ApiPropertiesYamlBindingTest {

    @Test
    void shouldPlaceFaceEnhanceProvidersUnderFaceEnhanceInApplicationGitYaml() throws IOException {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-git.yml"));
        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("face2info.api.face-enhance.provider")).isEqualTo("gfpgan");
        assertThat(properties.containsKey("face2info.api.face-enhance.gfpgan.project-path")).isTrue();
        assertThat(properties.getProperty("face2info.api.face-enhance.gfpgan.model-version"))
                .isEqualTo("${GFPGAN_MODEL_VERSION:1.3}");
        assertThat(properties.containsKey("face2info.api.face-enhance.replicate.model-version")).isTrue();
        assertThat(properties.containsKey("face2info.api.replicate.model-version")).isFalse();
    }

    @Test
    void shouldExposeQueryRewriteStructureInApplicationGitYaml() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-git.yml"));
        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("face2info.api.query-rewrite.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("face2info.api.query-rewrite.topic-strategies.education")).isEqualTo("normalize");
        assertThat(properties.getProperty("face2info.api.query-rewrite.topic-strategies.china_related_statements")).isEqualTo("rewrite");
        assertThat(properties.getProperty("face2info.api.query-rewrite.topic-strategies.political_view")).isEqualTo("rewrite");
        assertThat(properties.getProperty("face2info.api.query-rewrite.base-query-templates.china_related_statements[0]"))
                .isEqualTo("%s 涉华言论");
        assertThat(properties.getProperty("face2info.api.query-rewrite.base-query-templates.family_member_situation[4]"))
                .isEqualTo("%s 商业纠纷");
        assertThat(properties.getProperty("face2info.api.query-rewrite.expand-enabled-topics[3]"))
                .isEqualTo("family_member_situation");
        assertThat(properties.getProperty("face2info.api.query-rewrite.expand-max-query-count")).isEqualTo("4");
        assertThat(properties.getProperty("face2info.api.query-rewrite.expand-max-term-length")).isEqualTo("16");
        assertThat(properties.getProperty("face2info.api.query-rewrite.fallback-templates.china_related_statements[0]"))
                .isEqualTo("%s涉华言论 中国评价 中美关系 中欧关系");
        assertThat(properties.getProperty("face2info.api.query-rewrite.fallback-templates.political_view[0]"))
                .isEqualTo("%s支持的政治理念");
    }

    @Test
    void shouldExposeComprefaceStructureInApplicationGitYaml() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-git.yml"));
        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("face2info.api.compreface.base-url")).isEqualTo("http://127.0.0.1:8000");
        assertThat(properties.getProperty("face2info.api.compreface.session-ttl-seconds")).isEqualTo("600");
        assertThat(properties.getProperty("face2info.api.compreface.detection.path"))
                .isEqualTo("/api/v1/detection/detect");
        assertThat(properties.getProperty("face2info.api.compreface.verification.path"))
                .isEqualTo("/api/v1/verify/verify");
    }
}
