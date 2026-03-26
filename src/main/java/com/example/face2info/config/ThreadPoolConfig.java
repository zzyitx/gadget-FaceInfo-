package com.example.face2info.config;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
/**
 * 聚合流程使用的线程池配置。
 */
public class ThreadPoolConfig {

    private ThreadPoolTaskExecutor executor;

    @Bean(name = "face2InfoExecutor")
    public ThreadPoolTaskExecutor face2InfoExecutor(ApiProperties properties) {
        ApiProperties.Async async = properties.getAsync();
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(async.getCorePoolSize());
        taskExecutor.setMaxPoolSize(async.getMaxPoolSize());
        taskExecutor.setQueueCapacity(async.getQueueCapacity());
        taskExecutor.setKeepAliveSeconds(async.getKeepAliveSeconds());
        taskExecutor.setThreadNamePrefix(async.getThreadNamePrefix());
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.setAwaitTerminationSeconds(10);
        taskExecutor.initialize();
        this.executor = taskExecutor;
        return taskExecutor;
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
