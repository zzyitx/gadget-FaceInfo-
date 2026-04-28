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
    private Gfpgan gfpgan = new Gfpgan();

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

    @Getter
    @Setter
    public static class Gfpgan {
        private String projectPath = "D:/ideaProject/GFPGAN";
        private String pythonCommand = "D:/ideaProject/GFPGAN/.venv/Scripts/python.exe";
        private String scriptPath = "inference_gfpgan.py";
        // 与当前可运行命令保持一致，默认使用项目确认可用的 1.3 模型。
        private String modelVersion = "1.3";
        private int upscale = 2;
        private String outputExtension = "auto";
        private String backgroundUpsampler = "none";
        private boolean onlyCenterFace;
        private boolean aligned;
        private double weight = 0.5D;
        private long processTimeoutMs = 180000L;
    }
}
