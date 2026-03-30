package com.example.face2info.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void shouldMaskSensitiveQueryParametersInUrl() {
        String masked = LogSanitizer.maskUrl(
                "https://example.com/search?engine=google&api_key=secret123&apiKey=secret456&token=value"
        );

        assertThat(masked)
                .contains("engine=google")
                .contains("api_key=***")
                .contains("apiKey=***")
                .contains("token=***")
                .doesNotContain("secret123")
                .doesNotContain("secret456")
                .doesNotContain("value");
    }

    @Test
    void shouldMaskAuthorizationParameterRegardlessOfCase() {
        String masked = LogSanitizer.maskUrl(
                "https://example.com/chat?Authorization=Bearer%20abc123&name=kimi"
        );

        assertThat(masked)
                .contains("Authorization=***")
                .contains("name=kimi")
                .doesNotContain("abc123");
    }
}
