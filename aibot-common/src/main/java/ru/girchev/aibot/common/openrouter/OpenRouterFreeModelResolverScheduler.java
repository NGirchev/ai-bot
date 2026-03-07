package ru.girchev.aibot.common.openrouter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Периодически обновляет список free-моделей OpenRouter.
 *
 * Важно: scheduling включён на уровне приложения (см. {@code ru.girchev.aibot.Application}).
 */
@Slf4j
@RequiredArgsConstructor
public class OpenRouterFreeModelResolverScheduler {

    private final OpenRouterFreeModelResolver openRouterFreeModelResolver;

    @Scheduled(
            initialDelayString = "${ai-bot.common.openrouter-free-models.refresh-initial-delay}",
            fixedDelayString = "${ai-bot.common.openrouter-free-models.refresh-interval}"
    )
    public void refreshFreeModels() {
        try {
            openRouterFreeModelResolver.refresh();
            log.info("OpenRouter free models refreshed. count={}, refreshedAt={}",
                    openRouterFreeModelResolver.getCachedFreeModelIds().size(),
                    openRouterFreeModelResolver.getLastRefreshAtEpochMs());
        } catch (Exception e) {
            log.warn("Failed to refresh OpenRouter free models. reason={}", e.getMessage(), e);
        }
    }
}

