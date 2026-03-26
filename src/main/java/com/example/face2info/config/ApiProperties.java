package com.example.face2info.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Face2Info 总配置入口。
 * 聚合管理外部 API、代理和异步线程池相关参数。
 */
@ConfigurationProperties(prefix = "face2info")
public class ApiProperties {

    private Api api = new Api();
    private Async async = new Async();

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public Async getAsync() {
        return async;
    }

    public void setAsync(Async async) {
        this.async = async;
    }

    /**
     * 外部 API 聚合配置。
     */
    public static class Api {

        private SerpApiProperties serp = new SerpApiProperties();
        private NewsApiProperties news = new NewsApiProperties();
        private Proxy proxy = new Proxy();

        public SerpApiProperties getSerp() {
            return serp;
        }

        public void setSerp(SerpApiProperties serp) {
            this.serp = serp;
        }

        public NewsApiProperties getNews() {
            return news;
        }

        public void setNews(NewsApiProperties news) {
            this.news = news;
        }

        public Proxy getProxy() {
            return proxy;
        }

        public void setProxy(Proxy proxy) {
            this.proxy = proxy;
        }
    }

    /**
     * 外部 HTTP 代理配置。
     */
    public static class Proxy {

        private boolean enabled;
        private String host;
        private Integer port;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }
    }

    /**
     * 异步线程池配置。
     */
    public static class Async {

        private int corePoolSize = 8;
        private int maxPoolSize = 16;
        private int queueCapacity = 100;
        private int keepAliveSeconds = 60;
        private String threadNamePrefix = "face2info-";

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(int keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }
    }
}
