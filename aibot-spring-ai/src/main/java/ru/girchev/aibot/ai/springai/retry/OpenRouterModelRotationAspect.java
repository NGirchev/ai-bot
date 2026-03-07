package ru.girchev.aibot.ai.springai.retry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import ru.girchev.aibot.ai.springai.config.SpringAIModelConfig;
import ru.girchev.aibot.common.ai.ModelType;
import ru.girchev.aibot.common.ai.command.AIBotChatOptions;
import ru.girchev.aibot.common.ai.command.AICommand;
import ru.girchev.aibot.common.ai.response.SpringAIStreamResponse;
import ru.girchev.aibot.common.openrouter.OpenRouterFreeModelResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ru.girchev.aibot.common.ai.LlmParamNames.MAX_PRICE;
import static ru.girchev.aibot.common.ai.LlmParamNames.OPTIONS;

@Slf4j
@Aspect
@RequiredArgsConstructor
public class OpenRouterModelRotationAspect {

    private final ObjectProvider<OpenRouterFreeModelResolver> openRouterFreeModelResolverProvider;
    private final int maxAttempts;
    private static final int MAX_ERROR_BODY_CHARS = 2_000;

    @Around("@annotation(rotate)")
    public Object rotateModels(ProceedingJoinPoint pjp, RotateOpenRouterModels rotate) throws Throwable {
        Object[] args = pjp.getArgs();
        SpringAIModelConfig modelConfig = extractModelConfig(args);
        if (modelConfig == null) {
            return pjp.proceed();
        }

        if (!shouldRotateFor(modelConfig, args)) {
            return pjp.proceed();
        }

        AICommand command = extractCommand(args);
        List<String> candidates = resolveCandidates(modelConfig, command);
        if (candidates.isEmpty()) {
            return pjp.proceed();
        }

        if (rotate.stream()) {
            Flux<ChatResponse> rotated = streamAttempt(pjp, args, modelConfig, candidates, 0);
            return new SpringAIStreamResponse(rotated);
        }

        return callWithRetry(pjp, args, modelConfig, candidates);
    }

    /**
     * AUTO-ротация по free-моделям включается только при явном желании использовать free-пул,
     * которое в нашем проекте выражается через {@code max_price=0} в body.
     *
     * Во всех остальных случаях "AUTO" может означать другой механизм определения модели.
     */
    private boolean shouldRotateFor(SpringAIModelConfig modelConfig, Object[] args) {
        if (isOpenRouterFreeModel(modelConfig)) {
            return true;
        }
        if (!isOpenRouterAutoModel(modelConfig)) {
            return false;
        }
        Map<String, Object> body = extractBody(args);
        return isMaxPriceZero(body);
    }

    private Object callWithRetry(
            ProceedingJoinPoint pjp,
            Object[] baseArgs,
            SpringAIModelConfig modelConfig,
            List<String> candidates
    ) throws Throwable {
        RuntimeException last = null;
        for (String modelId : candidates) {
            long startNs = System.nanoTime();
            try {
                Object[] args = replaceModelConfig(baseArgs, modelConfig, modelId);
                Object result = pjp.proceed(args);
                long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
                recordSuccessIfPossible(modelId, latencyMs);
                return result;
            } catch (Exception e) {
                long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
                recordFailureIfPossible(modelId, e, latencyMs);
                last = (e instanceof RuntimeException re) ? re : new RuntimeException(e);
                if (!isRetryable(e)) {
                    throw last;
                }
                log.warn("Spring AI call failed for model={}, retrying next if available. reason={}", modelId, e.getMessage());
            }
        }
        throw last != null ? last : new RuntimeException("No models available for retry");
    }

    private Flux<ChatResponse> streamAttempt(
            ProceedingJoinPoint pjp,
            Object[] baseArgs,
            SpringAIModelConfig modelConfig,
            List<String> candidates,
            int index
    ) {
        if (index >= candidates.size()) {
            return Flux.error(new RuntimeException("No models available for retry"));
        }
        String modelId = candidates.get(index);
        Object[] args = replaceModelConfig(baseArgs, modelConfig, modelId);
        return Flux.defer(() -> {
            try {
                Object result = pjp.proceed(args);
                if (result instanceof SpringAIStreamResponse streamResponse) {
                    return streamResponse.chatResponse();
                }
                return Flux.error(new IllegalStateException("Expected SpringAIStreamResponse"));
            } catch (Throwable t) {
                return Flux.error(t);
            }
        }).onErrorResume(nextError -> {
            if (!isRetryable(nextError)) {
                return Flux.error(nextError);
            }
            if (index + 1 >= candidates.size()) {
                return Flux.error(nextError);
            }
            log.warn("Spring AI stream failed for model={}, retrying next if available. reason={}", modelId, nextError.getMessage());
            return streamAttempt(pjp, baseArgs, modelConfig, candidates, index + 1);
        });
    }

    private SpringAIModelConfig extractModelConfig(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof SpringAIModelConfig config) {
                return config;
            }
        }
        return null;
    }

    private Object[] replaceModelConfig(Object[] args, SpringAIModelConfig original, String modelName) {
        Object[] copy = args.clone();
        SpringAIModelConfig patched = copyModelConfig(original, modelName);
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] instanceof SpringAIModelConfig) {
                copy[i] = patched;
                break;
            }
        }
        return copy;
    }

    private SpringAIModelConfig copyModelConfig(SpringAIModelConfig source, String modelName) {
        SpringAIModelConfig copy = new SpringAIModelConfig();
        copy.setName(modelName);
        copy.setCapabilities(source.getCapabilities());
        copy.setProviderType(source.getProviderType());
        copy.setPriority(source.getPriority());
        return copy;
    }

    private List<String> resolveCandidates(SpringAIModelConfig modelConfig, AICommand command) {
        if (!isOpenRouterAutoModel(modelConfig) && !isOpenRouterFreeModel(modelConfig)) {
            return modelConfig.getName() != null ? List.of(modelConfig.getName()) : Collections.emptyList();
        }
        OpenRouterFreeModelResolver resolver = openRouterFreeModelResolverProvider.getIfAvailable();
        if (resolver == null) {
            return modelConfig.getName() != null ? List.of(modelConfig.getName()) : Collections.emptyList();
        }
        Set<ModelType> requiredModelTypes = command != null ? command.modelTypes() : Collections.emptySet();
        String requestedModel = modelConfig.getName();
        List<String> candidates = new ArrayList<>(resolver.candidatesForModel(requestedModel, requiredModelTypes));
        if (candidates.isEmpty() && modelConfig.getName() != null) {
            candidates = List.of(modelConfig.getName());
        }
        if (maxAttempts >= 1 && candidates.size() > maxAttempts) {
            candidates = candidates.subList(0, maxAttempts);
        }
        log.info("OpenRouter auto model rotation candidates (maxAttempts={}): {}", maxAttempts, candidates);
        return candidates;
    }

    private AICommand extractCommand(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof AICommand cmd) {
                return cmd;
            }
        }
        return null;
    }

    private Map<String, Object> extractBody(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof AIBotChatOptions opts) {
                return opts.body();
            }
            if (arg instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) map;
                return cast;
            }
        }
        return null;
    }

    private boolean isMaxPriceZero(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        Object maxPrice = body.get(MAX_PRICE);
        if (maxPrice == null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> options = (Map<String, Object>) body.get(OPTIONS);
            if (options != null) {
                maxPrice = options.get(MAX_PRICE);
            }
        }
        if (maxPrice == null) {
            return false;
        }

        if (maxPrice instanceof Number number) {
            return number.doubleValue() == 0.0d;
        }
        if (maxPrice instanceof String str) {
            try {
                return Double.parseDouble(str.trim()) == 0.0d;
            } catch (Exception ignored) {
                return false;
            }
        }
        if (maxPrice instanceof Map<?, ?> map) {
            Double prompt = asDouble(map.get("prompt"));
            Double completion = asDouble(map.get("completion"));
            return prompt != null && completion != null && prompt == 0.0d && completion == 0.0d;
        }
        return false;
    }

    private Double asDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isOpenRouterAutoModel(SpringAIModelConfig modelConfig) {
        if (modelConfig == null || modelConfig.getProviderType() == null) {
            return false;
        }
        return modelConfig.getProviderType() == SpringAIModelConfig.ProviderType.OPENAI
                && modelConfig.getCapabilities() != null
                && modelConfig.getCapabilities().contains(ModelType.AUTO);
    }

    private boolean isOpenRouterFreeModel(SpringAIModelConfig modelConfig) {
        if (modelConfig == null || modelConfig.getProviderType() == null) {
            return false;
        }
        String name = modelConfig.getName();
        return modelConfig.getProviderType() == SpringAIModelConfig.ProviderType.OPENAI
                && name != null
                && name.contains(":free");
    }

    private boolean isRetryable(Throwable error) {
        if (error instanceof WebClientResponseException w) {
            int status = w.getStatusCode().value();
            if (status == 429 || (status >= 500 && status <= 599)) {
                return true;
            }
            // Некоторые free-провайдеры в OpenRouter имеют более строгую валидацию формата messages.
            // Это может быть модель-специфично, поэтому имеет смысл попробовать следующую кандидатуру.
            if (status == 400) {
                String body = w.getResponseBodyAsString();
                if (body != null && body.contains("Conversation roles must alternate")) {
                    return true;
                }
            }
            return false;
        }
        // timeouts/transport errors: retry
        return true;
    }

    private void recordSuccessIfPossible(String modelId, long latencyMs) {
        OpenRouterFreeModelResolver resolver = openRouterFreeModelResolverProvider.getIfAvailable();
        if (resolver == null || modelId == null || !modelId.contains(":free")) {
            return;
        }
        resolver.recordSuccess(modelId, latencyMs);
    }

    private void recordFailureIfPossible(String modelId, Throwable error, long latencyMs) {
        OpenRouterFreeModelResolver resolver = openRouterFreeModelResolverProvider.getIfAvailable();
        if (resolver == null || modelId == null || !modelId.contains(":free")) {
            return;
        }
        int status = 599;
        String responseBody = null;
        if (error instanceof WebClientResponseException w) {
            status = w.getStatusCode().value();
            responseBody = truncate(w.getResponseBodyAsString());
        }
        resolver.recordFailure(modelId, status, latencyMs);
        if (responseBody != null && status >= 400 && status <= 499) {
            log.warn("OpenRouter request failed. model={}, status={}, latencyMs={}, body={}",
                    modelId, status, latencyMs, responseBody);
        }
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
