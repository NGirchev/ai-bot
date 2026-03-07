package ru.girchev.aibot.ai.mock.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "ai-bot.ai.gateway-mock")
@Validated
@Getter
@Setter
public class MockGatewayProperties {
    
    /**
     * Включает/выключает Mock Gateway.
     * По умолчанию выключен (false).
     */
    private Boolean enabled = false;
}
