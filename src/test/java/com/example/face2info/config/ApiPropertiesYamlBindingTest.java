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
}
