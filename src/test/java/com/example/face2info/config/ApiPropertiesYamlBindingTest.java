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
        assertThat(properties.getProperty("face2info.search.query-templates.secondary_profile[2]")).isEqualTo("{name} company position");
        assertThat(properties.containsKey("face2info.search.query-templates.family[0]")).isFalse();
        assertThat(properties.containsKey("face2info.search.query-templates.education[0]")).isFalse();
        assertThat(properties.containsKey("face2info.search.query-templates.family_member_situation[0]")).isFalse();
        assertThat(properties.containsKey("face2info.search.query-templates.contact_information[0]")).isFalse();
        assertThat(properties.containsKey("face2info.search.query-templates.china_related_statements[0]")).isFalse();
        assertThat(properties.containsKey("face2info.search.query-templates.political_view[0]")).isFalse();
        assertThat(properties.containsKey("face2info.search.query-templates.misconduct[0]")).isFalse();
        assertThat(properties.containsKey("face2info.search.derived-section-titles.china_related_statements[0]")).isFalse();
        assertThat(properties.containsKey("face2info.search.derived-section-titles.contact_information[0]")).isFalse();
        assertThat(properties.containsKey("face2info.search.expand-enabled-topics[0]")).isFalse();
        assertThat(properties.containsKey("face2info.search.expand-max-query-count")).isFalse();
        assertThat(properties.containsKey("face2info.search.expand-max-term-length")).isFalse();
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
        assertThat(properties.getProperty("face2info.api.maigret.enabled")).isEqualTo("${MAIGRET_ENABLED:true}");
        assertThat(properties.getProperty("face2info.api.maigret.project-path"))
                .isEqualTo("${MAIGRET_PROJECT_PATH:D:/ideaProject/maigret}");
        assertThat(properties.getProperty("face2info.api.maigret.executable"))
                .isEqualTo("${MAIGRET_EXECUTABLE:D:/ideaProject/maigret/.venv/Scripts/maigret.exe}");
        assertThat(properties.containsKey("face2info.api.maigret.command-prefix[0]")).isFalse();
        assertThat(properties.getProperty("face2info.api.maigret.top-sites")).isEqualTo("${OSINT_SOCIAL_SITE_LIMIT:50}");
        assertThat(properties.getProperty("face2info.api.maigret.max-usernames")).isEqualTo("${MAIGRET_MAX_USERNAMES:5}");
        assertThat(properties.getProperty("face2info.api.maigret.max-accounts-per-username"))
                .isEqualTo("${MAIGRET_MAX_ACCOUNTS_PER_USERNAME:30}");
        assertThat(properties.getProperty("face2info.api.maigret.sherlock.project-path"))
                .isEqualTo("${SHERLOCK_PROJECT_PATH:D:/ideaProject/sherlock}");
        assertThat(properties.getProperty("face2info.api.maigret.tookie.project-path"))
                .isEqualTo("${TOOKIE_PROJECT_PATH:D:/ideaProject/tookie-osint}");
        assertThat(properties.getProperty("face2info.api.maigret.social-sites[49]")).isEqualTo("Last.fm");
        assertThat(properties.containsKey("face2info.api.maigret.social-sites[50]")).isFalse();
    }

    @Test
    void shouldExposeRocketReachStructureInApplicationGitYaml() {
        Properties properties = loadApplicationGitProperties();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("face2info.api.rocketreach.enabled"))
                .isEqualTo("${ROCKETREACH_ENABLED:false}");
        assertThat(properties.getProperty("face2info.api.rocketreach.base-url"))
                .isEqualTo("${ROCKETREACH_API_BASE_URL:https://api.rocketreach.co/api/v2}");
        assertThat(properties.getProperty("face2info.api.rocketreach.person-search-path"))
                .isEqualTo("${ROCKETREACH_PERSON_SEARCH_PATH:/person/search}");
        assertThat(properties.getProperty("face2info.api.rocketreach.api-key"))
                .isEqualTo("${ROCKETREACH_API_KEY:}");
        assertThat(properties.getProperty("face2info.api.rocketreach.max-results"))
                .isEqualTo("${ROCKETREACH_MAX_RESULTS:5}");
    }

    @Test
    void shouldExposeDeepSeekTimeoutInApplicationGitYaml() {
        Properties properties = loadApplicationGitProperties();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("face2info.api.deepseek.read-timeout-ms")).isEqualTo("90000");
        assertThat(properties.getProperty("face2info.api.deepseek.final-profile-model"))
                .isEqualTo("${DEEPSEEK_FINAL_PROFILE_MODEL:DeepSeek-V4-Pro}");
    }

    @Test
    void shouldUseKimiAsSummaryFallbackProviderInApplicationGitYaml() {
        Properties properties = loadApplicationGitProperties();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("face2info.api.summary.provider")).isEqualTo("kimi");
        assertThat(properties.containsKey("face2info.api.summary.base-url")).isTrue();
        assertThat(properties.containsKey("face2info.api.summary.api-key")).isTrue();
        assertThat(properties.containsKey("face2info.api.summary.model")).isTrue();
        assertThat(properties.getProperty("face2info.api.summary.profile-summary-batch-size"))
                .isEqualTo("${PROFILE_SUMMARY_BATCH_SIZE:8}");
        assertThat(properties.containsKey("face2info.api.summary.page-routing-enabled")).isFalse();
        assertThat(properties.containsKey("face2info.api.summary.long-content-threshold")).isFalse();
        assertThat(properties.containsKey("face2info.api.summary.structured-page-keywords[0]")).isFalse();
    }

    @Test
    void shouldExposeSophnetVisionStructureInApplicationGitYaml() {
        Properties properties = loadApplicationGitProperties();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("face2info.api.sophnet-vision.enabled"))
                .isEqualTo("${SOPHNET_VISION_ENABLED:true}");
        assertThat(properties.getProperty("face2info.api.sophnet-vision.base-url"))
                .isEqualTo("${SOPHNET_VISION_API_BASE_URL:https://www.sophnet.com/api/open-apis/v1/chat/completions}");
        assertThat(properties.getProperty("face2info.api.sophnet-vision.api-key"))
                .isEqualTo("${SOPHNET_API_KEY:}");
        assertThat(properties.getProperty("face2info.api.sophnet-vision.models[0]"))
                .isEqualTo("${SOPHNET_VISION_GEMINI_MODEL:gemini-3.1-pro-preview}");
        assertThat(properties.containsKey("face2info.api.sophnet-vision.models[1]")).isFalse();
        assertThat(properties.getProperty("face2info.api.sophnet-vision.max-evidence-urls")).isEqualTo("8");
        assertThat(properties.getProperty("face2info.api.sophnet-vision.user-prompt"))
                .contains("public social media accounts")
                .contains("\"socialAccounts\"");
    }

    private Properties loadApplicationGitProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-git.yml"));
        return factory.getObject();
    }
}
