package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FaceDetectionProperties {

    private String baseUrl = "http://localhost:8091";
    private String detectPath = "/detect";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 10000;
    private long sessionTtlSeconds = 600;
}
