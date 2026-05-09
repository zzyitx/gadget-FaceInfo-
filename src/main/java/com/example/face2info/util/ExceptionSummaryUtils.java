package com.example.face2info.util;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;

/**
 * 统一收敛外部调用异常，避免超时场景在业务日志中刷出整段底层堆栈。
 */
public final class ExceptionSummaryUtils {

    private static final String TIMEOUT_MESSAGE = "调用超时";

    private ExceptionSummaryUtils() {
    }

    public static boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("timeout")
                        || normalized.contains("timed out")
                        || normalized.contains("read timed out")
                        || normalized.contains("connect timed out")
                        || normalized.contains("连接超时")
                        || normalized.contains("读取超时")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public static String compactMessage(Throwable throwable) {
        if (isTimeout(throwable)) {
            return TIMEOUT_MESSAGE;
        }
        if (throwable == null || throwable.getMessage() == null) {
            return "";
        }
        return throwable.getMessage();
    }
}
