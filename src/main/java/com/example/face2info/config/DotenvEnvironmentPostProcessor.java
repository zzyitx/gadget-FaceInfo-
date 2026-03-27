package com.example.face2info.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在应用启动早期从项目根目录 .env 补充敏感配置。
 * 已存在的环境变量与系统属性优先，不会被 .env 覆盖。
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "face2infoDotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path dotenvPath = Path.of(System.getProperty("user.dir"), ".env");
        if (!Files.isRegularFile(dotenvPath)) {
            return;
        }

        Map<String, Object> properties = loadMissingProperties(dotenvPath, environment);
        if (!properties.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Map<String, Object> loadMissingProperties(Path dotenvPath, ConfigurableEnvironment environment) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String line : readLines(dotenvPath)) {
            DotenvEntry entry = parseLine(line);
            if (entry == null || environment.containsProperty(entry.key())) {
                continue;
            }
            properties.put(entry.key(), entry.value());
        }
        return properties;
    }

    private List<String> readLines(Path dotenvPath) {
        try {
            return Files.readAllLines(dotenvPath, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read .env file from " + dotenvPath, ex);
        }
    }

    private DotenvEntry parseLine(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#")) {
            return null;
        }

        String normalized = trimmed.startsWith("export ") ? trimmed.substring(7).trim() : trimmed;
        int separatorIndex = normalized.indexOf('=');
        if (separatorIndex <= 0) {
            return null;
        }

        String key = normalized.substring(0, separatorIndex).trim();
        String value = normalized.substring(separatorIndex + 1).trim();
        if (!StringUtils.hasText(key)) {
            return null;
        }

        return new DotenvEntry(key, stripQuotes(value));
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2) {
            boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
            boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
            if (doubleQuoted || singleQuoted) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private record DotenvEntry(String key, String value) {
    }
}
