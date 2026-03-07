package ru.girchev.aibot.common.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA конфигурация для базового модуля aibot-common
 * Сканирует базовые Entity (User, Message) и репозитории
 * Эта конфигурация активна всегда, так как содержит базовые модели
 */
@Configuration
@EntityScan(basePackages = {
        "ru.girchev.aibot.common.model"
})
@EnableJpaRepositories(basePackages = {
        "ru.girchev.aibot.common.repository"
})
public class CoreJpaConfig {
    // JPA конфигурация для базовых Entity и репозиториев
}

