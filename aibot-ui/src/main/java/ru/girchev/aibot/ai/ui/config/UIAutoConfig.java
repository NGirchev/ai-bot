package ru.girchev.aibot.ai.ui.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ru.girchev.aibot.ai.ui.controller.PageController;
import ru.girchev.aibot.ai.ui.controller.UIAuthController;
import ru.girchev.aibot.rest.config.RestAutoConfig;
import ru.girchev.aibot.rest.service.RestAuthorizationService;
import ru.girchev.aibot.rest.service.RestUserService;

/**
 * Автоконфигурация для UI модуля
 * Зависит от RestAutoConfig для работы с REST API
 * Активируется только если включен UI модуль (ai-bot.ui.enabled=true)
 */
@AutoConfiguration
@AutoConfigureAfter(name = "ru.girchev.aibot.rest.config.RestAutoConfig")
@EnableConfigurationProperties(UIProperties.class)
@ConditionalOnProperty(name = "ai-bot.ui.enabled", havingValue = "true")
public class UIAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public PageController pageController() {
        return new PageController();
    }

    @Bean
    @ConditionalOnMissingBean
    public UIAuthController uiAuthController(RestUserService restUserService) {
        return new UIAuthController(restUserService);
    }
}
