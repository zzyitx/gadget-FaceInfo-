package com.example.face2info.util;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionSummaryUtilsTest {

    @Test
    void shouldDetectReadTimedOutFromCauseChain() {
        RuntimeException exception = new ResourceAccessException(
                "I/O error on POST request: Read timed out",
                new SocketTimeoutException("Read timed out")
        );

        assertThat(ExceptionSummaryUtils.isTimeout(exception)).isTrue();
        assertThat(ExceptionSummaryUtils.compactMessage(exception)).isEqualTo("调用超时");
    }

    @Test
    void shouldKeepOriginalMessageForNonTimeoutFailure() {
        RuntimeException exception = new RuntimeException("HTTP 500");

        assertThat(ExceptionSummaryUtils.isTimeout(exception)).isFalse();
        assertThat(ExceptionSummaryUtils.compactMessage(exception)).isEqualTo("HTTP 500");
    }
}
