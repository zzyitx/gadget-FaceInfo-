package com.example.face2info.config;

import com.example.face2info.util.LogSanitizer;
import com.example.face2info.util.ExceptionSummaryUtils;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.net.URI;
import java.util.Locale;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(ApiProperties properties) {
        int connectTimeout = resolveConnectTimeout(properties);
        int readTimeout = resolveReadTimeout(properties);
        return buildRestTemplate(connectTimeout, readTimeout, properties);
    }

    @Bean
    @Qualifier("kimiRestTemplate")
    public RestTemplate kimiRestTemplate(ApiProperties properties) {
        int connectTimeout = resolveKimiConnectTimeout(properties);
        int readTimeout = resolveKimiReadTimeout(properties);
        return buildRestTemplate(connectTimeout, readTimeout, properties);
    }

    int resolveConnectTimeout(ApiProperties properties) {
        return max(
                properties.getApi().getSerp().getConnectTimeoutMs(),
                properties.getApi().getGoogle().getConnectTimeoutMs(),
                properties.getApi().getJina().getConnectTimeoutMs(),
                properties.getApi().getKimi().getConnectTimeoutMs(),
                properties.getApi().getDeepseek().getConnectTimeoutMs(),
                properties.getApi().getCompreface().getConnectTimeoutMs(),
                properties.getApi().getSummary().getConnectTimeoutMs(),
                properties.getApi().getFaceDetection().getConnectTimeoutMs(),
                properties.getApi().getFaceEnhance().getConnectTimeoutMs(),
                properties.getApi().getRocketreach().getConnectTimeoutMs(),
                properties.getApi().getSophnetVision().getConnectTimeoutMs()
        );
    }

    int resolveReadTimeout(ApiProperties properties) {
        return max(
                properties.getApi().getSerp().getReadTimeoutMs(),
                properties.getApi().getGoogle().getReadTimeoutMs(),
                properties.getApi().getJina().getReadTimeoutMs(),
                properties.getApi().getKimi().getReadTimeoutMs(),
                properties.getApi().getDeepseek().getReadTimeoutMs(),
                properties.getApi().getCompreface().getReadTimeoutMs(),
                properties.getApi().getSummary().getReadTimeoutMs(),
                properties.getApi().getFaceDetection().getReadTimeoutMs(),
                properties.getApi().getFaceEnhance().getReadTimeoutMs(),
                properties.getApi().getRocketreach().getReadTimeoutMs(),
                properties.getApi().getSophnetVision().getReadTimeoutMs()
        );
    }

    int resolveKimiConnectTimeout(ApiProperties properties) {
        return properties.getApi().getKimi().getConnectTimeoutMs();
    }

    int resolveKimiReadTimeout(ApiProperties properties) {
        return properties.getApi().getKimi().getReadTimeoutMs();
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
        CloseableHttpClient httpClient;
        ApiProperties.Proxy proxy = properties.getApi().getProxy();
        if (proxy.isEnabled() && proxy.getHost() != null && proxy.getPort() != null) {
            HttpHost proxyHost = new HttpHost(proxy.getHost(), proxy.getPort());
            httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(configBuilder.build())
                    .setRoutePlanner(new DefaultProxyRoutePlanner(proxyHost) {
                        @Override
                        protected HttpHost determineProxy(HttpHost target, HttpContext context) {
                            return shouldBypassProxy(toUri(target)) ? null : proxyHost;
                        }
                    })
                    .build();
        } else {
            httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(configBuilder.build())
                    .build();
        }
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    boolean shouldBypassProxy(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ("localhost".equals(host) || "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host)) {
            return true;
        }
        if (host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")
                || host.startsWith("169.254.")) {
            return true;
        }
        String[] parts = host.split("\\.");
        if (parts.length == 4 && "172".equals(parts[0])) {
            try {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80:");
    }

    private URI toUri(HttpHost target) {
        return URI.create(target.getSchemeName() + "://" + target.getHostName());
    }

    private RestTemplate buildRestTemplate(int connectTimeout, int readTimeout, ApiProperties properties) {
        ClientHttpRequestFactory requestFactory = requestFactory(connectTimeout, readTimeout, properties);
        RestTemplate restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(requestFactory));
        restTemplate.getInterceptors().add(new LoggingInterceptor());
        return restTemplate;
    }

    private static final class LoggingInterceptor implements ClientHttpRequestInterceptor {

        private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            String maskedUrl = LogSanitizer.maskUrl(request.getURI().toString());
            log.info("外部接口请求开始 method={} url={}", request.getMethod(), maskedUrl);
            try {
                ClientHttpResponse response = execution.execute(request, body);
                log.info("外部接口请求完成 method={} url={} status={}",
                        request.getMethod(), maskedUrl, response.getStatusCode());
                return response;
            } catch (IOException ex) {
                if (ExceptionSummaryUtils.isTimeout(ex)) {
                    log.warn("外部接口请求超时 method={} url={} error={}",
                            request.getMethod(), maskedUrl, ExceptionSummaryUtils.compactMessage(ex));
                } else {
                    log.warn("外部接口请求失败 method={} url={} error={}",
                            request.getMethod(), maskedUrl, ExceptionSummaryUtils.compactMessage(ex));
                }
                throw ex;
            }
        }
    }
}
