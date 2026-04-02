package com.example.face2info.entity.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 统一错误响应体。
 */
@Schema(description = "统一错误响应")
public class ErrorResponse {

    @Schema(description = "请求处理状态，失败时固定为 failed", example = "failed")
    private String status;

    @Schema(description = "错误信息描述，用于说明失败原因", example = "上传文件不能为空")
    private String error;

    @Schema(description = "错误发生时间，使用 ISO-8601 时间格式", example = "2026-04-02T14:30:00+08:00")
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
