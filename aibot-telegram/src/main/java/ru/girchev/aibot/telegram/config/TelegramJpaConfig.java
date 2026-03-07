package ru.girchev.aibot.telegram.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA конфигурация для Telegram модуля
 * Сканирует Entity и репозитории Telegram модуля
 * Активируется только если включен Telegram модуль (ai-bot.telegram.enabled=true)
 */
@Configuration
@EntityScan(basePackages = {
        "ru.girchev.aibot.telegram.model"
})
@EnableJpaRepositories(basePackages = {
        "ru.girchev.aibot.telegram.repository"
})
@ConditionalOnProperty(name = "ai-bot.telegram.enabled", havingValue = "true", matchIfMissing = true)
public class TelegramJpaConfig {
    // JPA конфигурация для Telegram Entity и репозиториев
}

