package com.example.face2info.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ApiPropertiesYamlBindingTest {

    @Test
    void shouldPlaceReplicateConfigUnderFaceEnhanceInApplicationGitYaml() throws IOException {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-git.yml"));
        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.containsKey("face2info.api.face-enhance.replicate.model-version")).isTrue();
        assertThat(properties.containsKey("face2info.api.replicate.model-version")).isFalse();
    }
}
