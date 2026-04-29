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
        Properties properties = loadApplicationGitProperties();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("face2info.api.face-enhance.provider")).isEqualTo("gfpgan");
        assertThat(properties.containsKey("face2info.api.face-enhance.gfpgan.project-path")).isTrue();
        assertThat(properties.getProperty("face2info.api.face-enhance.gfpgan.python-command"))
                .isEqualTo("${GFPGAN_PYTHON:D:/ideaProject/GFPGAN/.venv/Scripts/python.exe}");
        assertThat(properties.getProperty("face2info.api.face-enhance.gfpgan.model-version"))
                .isEqualTo("${GFPGAN_MODEL_VERSION:1.3}");
        assertThat(properties.containsKey("face2info.api.face-enhance.replicate.model-version")).isTrue();
        assertThat(properties.containsKey("face2info.api.replicate.model-version")).isFalse();
    }

    @Test
    void shouldExposeSearchTemplateStructureInApplicationGitYaml() {
        Properties properties = loadApplicationGitProperties();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("face2info.search.query-templates.secondary_profile[0]")).isEqualTo("{name}");
        assertThat(properties.getProperty("face2info.search.query-templates.education[0]")).isEqualTo("{name} education");
        assertThat(properties.getProperty("face2info.search.query-templates.family_member_situation[6]"))
                .isEqualTo("{native_name} 商业纠纷");
        assertThat(properties.getProperty("face2info.search.query-templates.contact_information[8]"))
                .isEqualTo("{username} {platform}");
        assertThat(properties.getProperty("face2info.search.expand-enabled-topics[3]"))
                .isEqualTo("family_member_situation");
        assertThat(properties.getProperty("face2info.search.expand-max-query-count")).isEqualTo("4");
        assertThat(properties.getProperty("face2info.search.expand-max-term-length")).isEqualTo("16");
        assertThat(properties.containsKey("face2info.api.query-rewrite.enabled")).isFalse();
    }

    @Test
    void shouldExposeComprefaceStructureInApplicationGitYaml() {
        Properties properties = loadApplicationGitProperties();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("face2info.api.compreface.enabled")).isEqualTo("${COMPREFACE_ENABLED:true}");
        assertThat(properties.getProperty("face2info.api.compreface.base-url")).isEqualTo("http://127.0.0.1:8000");
        assertThat(properties.getProperty("face2info.api.compreface.session-ttl-seconds")).isEqualTo("600");
        assertThat(properties.getProperty("face2info.api.compreface.detection.path"))
                .isEqualTo("/api/v1/detection/detect");
        assertThat(properties.getProperty("face2info.api.compreface.verification.path"))
                .isEqualTo("/api/v1/verification/verify");
    }

    @Test
    void shouldExposeMaigretStructureInApplicationGitYaml() {
        Properties properties = loadApplicationGitProperties();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("face2info.api.maigret.enabled")).isEqualTo("${MAIGRET_ENABLED:false}");
        assertThat(properties.getProperty("face2info.api.maigret.executable")).isEqualTo("${MAIGRET_EXECUTABLE:maigret}");
        assertThat(properties.containsKey("face2info.api.maigret.command-prefix[0]")).isFalse();
        assertThat(properties.getProperty("face2info.api.maigret.top-sites")).isEqualTo("${MAIGRET_TOP_SITES:200}");
        assertThat(properties.getProperty("face2info.api.maigret.max-usernames")).isEqualTo("${MAIGRET_MAX_USERNAMES:3}");
        assertThat(properties.getProperty("face2info.api.maigret.max-accounts-per-username"))
                .isEqualTo("${MAIGRET_MAX_ACCOUNTS_PER_USERNAME:30}");
    }

    private Properties loadApplicationGitProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-git.yml"));
        return factory.getObject();
    }
}
