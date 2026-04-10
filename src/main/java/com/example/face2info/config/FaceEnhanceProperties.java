package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FaceEnhanceProperties {

    private boolean enabled = true;
    private String provider = "noop";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 90000;
    private Replicate replicate = new Replicate();

    @Getter
    @Setter
    public static class Replicate {
        private String baseUrl = "https://api.replicate.com";
        private String apiKey;
        private String modelOwner = "tencentarc";
        private String modelName = "gfpgan";
        private String modelVersion;
        private String gfpganVersion = "v1.4";
        private int scale = 2;
        private int preferWaitSeconds = 30;
        private int pollIntervalMs = 1500;
        private int pollTimeoutMs = 45000;
        private int maxRetries = 2;
        private long backoffInitialMs = 300L;
    }
}
