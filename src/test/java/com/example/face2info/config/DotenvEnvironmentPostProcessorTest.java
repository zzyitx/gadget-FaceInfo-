package com.example.face2info.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DotenvEnvironmentPostProcessorTest {

    private static final String USER_DIR = "user.dir";
    private static final String SERP_API_KEY = "SERP_API_KEY";

    private final String originalUserDir = System.getProperty(USER_DIR);

    @AfterEach
    void tearDown() {
        if (originalUserDir == null) {
            System.clearProperty(USER_DIR);
        } else {
            System.setProperty(USER_DIR, originalUserDir);
        }
    }

    @Test
    void shouldLoadMissingPropertiesFromRootDotenv(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(".env"), """
                SERP_API_KEY=from-dotenv
                JINA_API_KEY="quoted-value"
                """);
        System.setProperty(USER_DIR, tempDir.toString());

        ConfigurableEnvironment environment = new StandardEnvironment();

        new DotenvEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(SERP_API_KEY)).isEqualTo("from-dotenv");
        assertThat(environment.getProperty("JINA_API_KEY")).isEqualTo("quoted-value");
    }

    @Test
    void shouldNotOverrideExistingProperties(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(".env"), "SERP_API_KEY=from-dotenv");
        System.setProperty(USER_DIR, tempDir.toString());

        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-existing", Map.of(SERP_API_KEY, "from-env")));

        new DotenvEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(SERP_API_KEY)).isEqualTo("from-env");
    }

    @Test
    void shouldIgnoreMissingDotenvFile(@TempDir Path tempDir) {
        System.setProperty(USER_DIR, tempDir.toString());

        ConfigurableEnvironment environment = new StandardEnvironment();

        new DotenvEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(SERP_API_KEY)).isNull();
    }
}
