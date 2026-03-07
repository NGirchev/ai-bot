package ru.girchev.aibot.common.openrouter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestTemplate;
import ru.girchev.aibot.common.config.CoreAutoConfig;
import ru.girchev.aibot.common.openrouter.metrics.OpenRouterStreamMetricsTracker;
import reactor.core.publisher.Flux;

/**
 * Общая автоконфигурация для обновления/резолвинга free-моделей OpenRouter.
 *
 * Доступна всем модулям (spring-ai, openrouter и т.д.) без дублирования @Bean методов.
 */
@AutoConfiguration
@AutoConfigureAfter(CoreAutoConfig.class)
@EnableConfigurationProperties(OpenRouterFreeModelsProperties.class)
public class OpenRouterFreeModelsAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai-bot.common.openrouter-free-models", name = "enabled", havingValue = "true")
    public OpenRouterFreeModelResolver openRouterFreeModelResolver(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            OpenRouterFreeModelsProperties openRouterFreeModelsProperties
    ) {
        return new OpenRouterFreeModelResolver(restTemplate, objectMapper, openRouterFreeModelsProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai-bot.common.openrouter-free-models", name = "enabled", havingValue = "true")
    public OpenRouterFreeModelResolverScheduler openRouterFreeModelResolverScheduler(
            OpenRouterFreeModelResolver openRouterFreeModelResolver
    ) {
        return new OpenRouterFreeModelResolverScheduler(openRouterFreeModelResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(Flux.class)
    @ConditionalOnProperty(prefix = "ai-bot.common.openrouter-free-models", name = "enabled", havingValue = "true")
    public OpenRouterStreamMetricsTracker openRouterStreamMetricsTracker(
            ObjectProvider<OpenRouterFreeModelResolver> openRouterFreeModelResolverProvider
    ) {
        return new OpenRouterStreamMetricsTracker(openRouterFreeModelResolverProvider);
    }
}
