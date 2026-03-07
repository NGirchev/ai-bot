package ru.girchev.aibot.flyway.config;

import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация Flyway для основного приложения
 * Теперь использует модульные миграции из отдельных модулей
 * 
 * Конфигурация перенесена в модульные конфигурации:
 * - CoreFlywayConfig (aibot-common) - базовые миграции, выполняются всегда
 * - TelegramFlywayConfig (aibot-telegram) - миграции Telegram модуля
 * - RestFlywayConfig (aibot-rest) - миграции REST модуля
 * 
 * Каждая конфигурация создает Flyway бин с initMethod = "migrate",
 * который автоматически выполняет миграции при инициализации Spring контекста.
 * 
 * FlywayMigrationCheck проверяет статус всех миграций после запуска приложения.
 */
@Configuration
public class FlywayConfig {
    // Конфигурация миграций перенесена в модульные конфигурации
    // Этот класс оставлен для обратной совместимости и документации
} 