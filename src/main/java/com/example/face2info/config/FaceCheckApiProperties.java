package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FaceCheckApiProperties {

    private String baseUrl = "https://facecheck.id";
    private String uploadPath = "/api/upload_pic";
    private String apiKey;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;
    private boolean resetPrevImages = true;
}
