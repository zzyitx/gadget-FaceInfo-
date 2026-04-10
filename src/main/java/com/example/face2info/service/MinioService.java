package com.example.face2info.service;

public interface MinioService {
    String upload(byte[] bytes, String filename, String contentType);
}
