package com.example.face2info.exception;

import com.example.face2info.entity.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;

/**
 * 全局异常处理器。
 * 将业务异常映射为稳定的 JSON 错误响应，避免异常栈直接暴露到接口层。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理参数错误和通用非法入参。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(buildError(ex.getMessage(), HttpStatus.BAD_REQUEST));
    }

    /**
     * 处理识别失败异常。
     */
    @ExceptionHandler(FaceRecognitionException.class)
    public ResponseEntity<ErrorResponse> handleFaceRecognition(FaceRecognitionException ex) {
        return ResponseEntity.badRequest().body(buildError(ex.getMessage(), HttpStatus.BAD_REQUEST));
    }

    /**
     * 处理外部 API 调用异常。
     */
    @ExceptionHandler(ApiCallException.class)
    public ResponseEntity<ErrorResponse> handleApiCall(ApiCallException ex) {
        log.error("External API call failed", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildError(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE));
    }

    /**
     * 处理上传文件过大异常。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(buildError("上传文件过大，图片大小不能超过 10MB。", HttpStatus.PAYLOAD_TOO_LARGE));
    }

    /**
     * 处理静态资源不存在异常。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("请求的静态资源不存在。", HttpStatus.NOT_FOUND));
    }

    /**
     * 兜底处理未预期异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("服务内部错误，请稍后重试。", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * 构造统一错误响应对象。
     */
    private ErrorResponse buildError(String message, HttpStatus status) {
        return new ErrorResponse()
                .setStatus("failed")
                .setError(message)
                .setTimestamp(OffsetDateTime.now());
    }
}
