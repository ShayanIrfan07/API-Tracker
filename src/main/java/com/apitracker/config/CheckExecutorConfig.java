package com.apitracker.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(CheckProperties.class)
public class CheckExecutorConfig {

    public static final String CHECK_TASK_EXECUTOR = "checkTaskExecutor";

    @Bean(name = CHECK_TASK_EXECUTOR)
    Executor checkTaskExecutor(CheckProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.poolCoreSize());
        executor.setMaxPoolSize(properties.poolMaxSize());
        executor.setQueueCapacity(properties.poolQueueCapacity());
        executor.setThreadNamePrefix("api-check-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
