package com.example.face2info.util;

import com.example.face2info.exception.ApiCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.concurrent.Callable;

/**
 * 简单重试工具。
 * 用于对外部 HTTP 调用执行指数退避重试。
 */
public final class RetryUtils {

    private static final Logger log = LoggerFactory.getLogger(RetryUtils.class);

    private RetryUtils() {
    }

    /**
     * 执行带重试的调用。
     */
    public static <T> T execute(String name, int maxRetries, long initialBackoffMs, Callable<T> callable) {
        ApiCallException lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return callable.call();
            } catch (HttpStatusCodeException ex) {
                HttpStatusCode status = ex.getStatusCode();
                if (status.is4xxClientError()) {
                    throw new ApiCallException(name + " 调用失败：" + status.value() + " " + ex.getStatusText(), ex);
                }
                lastException = new ApiCallException(name + " 调用失败：" + status.value() + " " + ex.getStatusText(), ex);
            } catch (Exception ex) {
                lastException = new ApiCallException(name + " 调用失败：" + ex.getMessage(), ex);
            }
            if (attempt < maxRetries) {
                long sleepMs = initialBackoffMs * (1L << (attempt - 1));
                log.warn("{} 第 {} 次尝试失败，将在 {} 毫秒后重试", name, attempt, sleepMs);
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new ApiCallException(name + " 调用被中断。", interruptedException);
                }
            }
        }
        throw lastException == null ? new ApiCallException(name + " 调用失败。") : lastException;
    }
}
