package com.example.face2info.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

/**
 * RestTemplate 配置。
 * 统一设置超时、代理和请求日志拦截器。
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 创建全局复用的 RestTemplate。
     */
    @Bean
    public RestTemplate restTemplate(ApiProperties properties) {
        int connectTimeout = max(
                properties.getApi().getSerp().getConnectTimeoutMs(),
                properties.getApi().getNews().getConnectTimeoutMs(),
                properties.getApi().getJina().getConnectTimeoutMs(),
                properties.getApi().getSummary().getConnectTimeoutMs()
        );
        int readTimeout = max(
                properties.getApi().getSerp().getReadTimeoutMs(),
                properties.getApi().getNews().getReadTimeoutMs(),
                properties.getApi().getJina().getReadTimeoutMs(),
                properties.getApi().getSummary().getReadTimeoutMs()
        );
        ClientHttpRequestFactory requestFactory = requestFactory(connectTimeout, readTimeout, properties);
        RestTemplate restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(requestFactory));
        restTemplate.getInterceptors().add(new LoggingInterceptor());
        return restTemplate;
    }

    private int max(int... values) {
        int max = values[0];
        for (int value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    /**
     * 按配置构建底层 HTTP 请求工厂，并在需要时挂载代理。
     */
    private ClientHttpRequestFactory requestFactory(int connectTimeout, int readTimeout, ApiProperties properties) {
        RequestConfig.Builder configBuilder = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeout));
        ApiProperties.Proxy proxy = properties.getApi().getProxy();
        if (proxy.isEnabled() && proxy.getHost() != null && proxy.getPort() != null) {
            configBuilder.setProxy(new HttpHost(proxy.getHost(), proxy.getPort()));
        }
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(configBuilder.build())
                .build();
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    /**
     * 统一记录外部 HTTP 请求日志，便于排查接口调用问题。
     */
    private static final class LoggingInterceptor implements ClientHttpRequestInterceptor {

        private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            log.info("HTTP {} {}", request.getMethod(), request.getURI());
            ClientHttpResponse response = execution.execute(request, body);
            log.info("HTTP response status={}", response.getStatusCode());
            return response;
        }
    }
}
