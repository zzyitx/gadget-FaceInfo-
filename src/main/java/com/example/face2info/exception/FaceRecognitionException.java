package com.example.face2info.exception;

/**
 * 人脸识别失败异常。
 */
public class FaceRecognitionException extends RuntimeException {

    public FaceRecognitionException(String message) {
        super(message);
    }
}
