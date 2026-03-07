package ru.girchev.aibot.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Конфигурация для асинхронной работы (например, summarization).
 * Используется для выполнения долгих операций в фоновом режиме.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    
    /**
     * Executor для summarization операций.
     * Используется через @Async("summarizationTaskExecutor") в SummarizationService.
     */
    @Bean(name = "summarizationTaskExecutor")
    public Executor summarizationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("summarization-");
        executor.initialize();
        return executor;
    }
}

