package ru.girchev.aibot.rest.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA конфигурация для REST модуля
 * Сканирует Entity и репозитории REST модуля
 * Активируется только если включен REST модуль (ai-bot.rest.enabled=true)
 */
@EntityScan(basePackages = {
        "ru.girchev.aibot.rest.model"
})
@EnableJpaRepositories(basePackages = {
        "ru.girchev.aibot.rest.repository"
})
public class RestJpaConfig {
    // JPA конфигурация для REST Entity и репозиториев
}

