package ru.girchev.aibot.common.openrouter.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import ru.girchev.aibot.common.openrouter.OpenRouterFreeModelResolver;

/**
 * Явно оборачивает stream, чтобы метрики модели записывались без AOP.
 */
@Slf4j
@RequiredArgsConstructor
public class OpenRouterStreamMetricsTracker {

    private final ObjectProvider<OpenRouterFreeModelResolver> openRouterFreeModelResolverProvider;
    private static final int MAX_ERROR_BODY_CHARS = 4_000;

    public <T> Flux<T> track(String modelId, Flux<T> source) {
        OpenRouterFreeModelResolver resolver = openRouterFreeModelResolverProvider.getIfAvailable();
        if (resolver == null || modelId == null || !modelId.contains(":free")) {
            return source;
        }

        return Flux.defer(() -> {
            long startNs = System.nanoTime();
            return source
                    .doOnComplete(() -> {
                        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
                        resolver.recordSuccess(modelId, durationMs);
                        log.info("OpenRouter stream completed. model={}, durationMs={}", modelId, durationMs);
                    })
                    .doOnError(error -> {
                        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
                        int status = 599;
                        String responseBody = null;
                        if (error instanceof WebClientResponseException w) {
                            status = w.getStatusCode().value();
                            responseBody = truncate(w.getResponseBodyAsString());
                        }
                        resolver.recordFailure(modelId, status, durationMs);
                        if (responseBody != null) {
                            log.warn("OpenRouter stream failed. model={}, status={}, durationMs={}, body={}",
                                    modelId, status, durationMs, responseBody);
                        } else {
                            log.warn("OpenRouter stream failed. model={}, status={}, durationMs={}", modelId, status, durationMs);
                        }
                    });
        });
    }

    private String truncate(String body) {
        if (body == null) {
            return null;
        }
        if (body.length() <= MAX_ERROR_BODY_CHARS) {
            return body;
        }
        return body.substring(0, MAX_ERROR_BODY_CHARS) + "...(truncated)";
    }
}
