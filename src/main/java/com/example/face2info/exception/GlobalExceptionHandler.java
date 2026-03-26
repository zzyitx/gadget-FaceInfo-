package com.example.face2info.exception;

import com.example.face2info.entity.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.OffsetDateTime;

@ControllerAdvice
/**
 * 全局异常处理器。
 * 将业务异常映射为稳定的 JSON 响应。
 */
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(buildError(ex.getMessage(), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(FaceRecognitionException.class)
    public ResponseEntity<ErrorResponse> handleFaceRecognition(FaceRecognitionException ex) {
        return ResponseEntity.badRequest().body(buildError(ex.getMessage(), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(ApiCallException.class)
    public ResponseEntity<ErrorResponse> handleApiCall(ApiCallException ex) {
        log.error("External API call failed", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildError(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(buildError("上传文件过大，图片大小不能超过 10MB。", HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("请求的静态资源不存在。", HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("服务内部错误，请稍后重试。", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private ErrorResponse buildError(String message, HttpStatus status) {
        return new ErrorResponse()
                .setStatus("failed")
                .setError(message)
                .setTimestamp(OffsetDateTime.now());
    }
}
