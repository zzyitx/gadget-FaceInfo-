package com.example.face2info.config;

import com.example.face2info.util.LogSanitizer;
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

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(ApiProperties properties) {
        int connectTimeout = max(
                properties.getApi().getSerp().getConnectTimeoutMs(),
                properties.getApi().getNews().getConnectTimeoutMs(),
                properties.getApi().getJina().getConnectTimeoutMs(),
                properties.getApi().getKimi().getConnectTimeoutMs(),
                properties.getApi().getSummary().getConnectTimeoutMs()
        );
        int readTimeout = max(
                properties.getApi().getSerp().getReadTimeoutMs(),
                properties.getApi().getNews().getReadTimeoutMs(),
                properties.getApi().getJina().getReadTimeoutMs(),
                properties.getApi().getKimi().getReadTimeoutMs(),
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

    private static final class LoggingInterceptor implements ClientHttpRequestInterceptor {

        private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            String maskedUrl = LogSanitizer.maskUrl(request.getURI().toString());
            log.info("外部接口请求开始 method={} url={}", request.getMethod(), maskedUrl);
            ClientHttpResponse response = execution.execute(request, body);
            log.info("外部接口请求完成 method={} url={} status={}",
                    request.getMethod(), maskedUrl, response.getStatusCode());
            return response;
        }
    }
}
