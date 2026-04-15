package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Face2Info 总配置入口。
 * 聚合管理外部 API、代理和异步线程池相关参数。
 */
@ConfigurationProperties(prefix = "face2info")
@Getter
@Setter
public class ApiProperties {

    private Api api = new Api();
    private Async async = new Async();

    /**
     * 外部 API 聚合配置。
     */
    @Getter
    @Setter
    public static class Api {

        private SerpApiProperties serp = new SerpApiProperties();
        private GoogleSearchProperties google = new GoogleSearchProperties();
        private NewsApiProperties news = new NewsApiProperties();
        private JinaApiProperties jina = new JinaApiProperties();
        private KimiApiProperties kimi = new KimiApiProperties();
        private DeepSeekApiProperties deepseek = new DeepSeekApiProperties();
        private SummaryApiProperties summary = new SummaryApiProperties();
        private QueryRewriteProperties queryRewrite = new QueryRewriteProperties();
        private FaceCheckApiProperties facecheck = new FaceCheckApiProperties();
        private FaceDetectionProperties faceDetection = new FaceDetectionProperties();
        private FaceEnhanceProperties faceEnhance = new FaceEnhanceProperties();
        private MinioProperties minio = new MinioProperties();
        private RedisCacheProperties redisCache = new RedisCacheProperties();
        private Proxy proxy = new Proxy();

    }

    /**
     * 外部 HTTP 代理配置。
     */
    @Getter
    @Setter
    public static class Proxy {

        private boolean enabled;
        private String host;
        private Integer port;

    }

    /**
     * 异步线程池配置。
     */
    @Getter
    @Setter
    public static class Async {

        private int corePoolSize = 8;
        private int maxPoolSize = 16;
        private int queueCapacity = 100;
        private int keepAliveSeconds = 60;
        private String threadNamePrefix = "face2info-";

    }
}
