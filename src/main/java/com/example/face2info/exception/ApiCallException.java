package com.example.face2info.exception;

/**
 * 外部 API 调用异常。
 */
public class ApiCallException extends RuntimeException {

    public ApiCallException(String message) {
        super(message);
    }

    public ApiCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
