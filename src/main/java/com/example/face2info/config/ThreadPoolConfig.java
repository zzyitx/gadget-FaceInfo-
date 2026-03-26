package com.example.face2info.config;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 聚合流程线程池配置。
 * 为信息聚合阶段的并行任务提供隔离的执行器。
 */
@Configuration
public class ThreadPoolConfig {

    private ThreadPoolTaskExecutor executor;

    /**
     * 创建聚合任务专用线程池。
     */
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

    /**
     * 应用关闭时优雅停止线程池，避免后台任务被强制中断。
     */
    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
