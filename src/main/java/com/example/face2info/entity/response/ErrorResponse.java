package com.example.face2info.entity.response;

import java.time.OffsetDateTime;

/**
 * 统一错误响应体。
 */
public class ErrorResponse {

    private String status;
    private String error;
    private OffsetDateTime timestamp;

    public String getStatus() {
        return status;
    }

    public ErrorResponse setStatus(String status) {
        this.status = status;
        return this;
    }

    public String getError() {
        return error;
    }

    public ErrorResponse setError(String error) {
        this.error = error;
        return this;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public ErrorResponse setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
        return this;
    }
}
