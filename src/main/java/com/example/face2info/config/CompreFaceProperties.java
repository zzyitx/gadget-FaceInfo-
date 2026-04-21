package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompreFaceProperties {

    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:8000";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
    private long sessionTtlSeconds = 600;
    private Detection detection = new Detection();
    private Verification verification = new Verification();
    private Admin admin = new Admin();

    @Getter
    @Setter
    public static class Detection {
        private String apiKey;
        private String path = "/api/v1/detection/detect";
        private double detProbThreshold = 0.8D;
        private int limit = 0;
        private boolean status;
    }

    @Getter
    @Setter
    public static class Verification {
        private String apiKey;
        private String path = "/api/v1/verify/verify";
        private double detProbThreshold = 0.8D;
        private int limit = 1;
        private int predictionCount = 1;
        private boolean status;
    }

    @Getter
    @Setter
    public static class Admin {
        private String baseUrl = "http://127.0.0.1:8000";
        private String email;
        private String password;
    }
}
