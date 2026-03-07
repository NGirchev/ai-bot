package ru.girchev.aibot.ai.ui.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Свойства конфигурации для UI модуля
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai-bot.ui")
public class UIProperties {

    /**
     * Включен ли UI модуль
     */
    private Boolean enabled = false;
}

