package ru.girchev.aibot.ai.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import ru.girchev.aibot.ai.springai.config.SpringAIModelConfig;
import ru.girchev.aibot.common.ai.ModelType;
import ru.girchev.aibot.common.ai.command.AIBotChatOptions;
import ru.girchev.aibot.common.ai.command.AICommand;
import ru.girchev.aibot.common.ai.response.AIResponse;
import ru.girchev.aibot.common.ai.response.SpringAIResponse;
import ru.girchev.aibot.common.ai.response.SpringAIStreamResponse;
import ru.girchev.aibot.common.openrouter.metrics.OpenRouterStreamMetricsTracker;
import ru.girchev.aibot.ai.springai.retry.RotateOpenRouterModels;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class SpringAIChatService {

    private final SpringAIPromptFactory promptFactory;
    private final ObjectProvider<OpenRouterStreamMetricsTracker> openRouterStreamMetricsTrackerProvider;
    private static final int MAX_ERROR_BODY_CHARS = 4_000;

    @RotateOpenRouterModels(stream = true)
    public AIResponse streamChat(
            SpringAIModelConfig modelConfig,
            AICommand command,
            AIBotChatOptions chatOptions,
            List<Message> messages
    ) {
        String modelForStream = resolveModelName(modelConfig, chatOptions != null ? chatOptions.body() : null);
        Object conversationId = command != null ? command.metadata().get(AICommand.THREAD_KEY_FIELD) : null;
        boolean toolsEnabled = command != null && command.modelTypes().contains(ModelType.TOOL_CALLING);
        var promptBuilder = promptFactory.preparePrompt(
                modelConfig,
                modelForStream,
                chatOptions != null ? chatOptions.body() : null,
                conversationId,
                toolsEnabled,
                messages,
                chatOptions
        );

        log.info("Spring AI stream request. model={}, providerType={}, messages={}, toolsEnabled={}",
                modelForStream,
                modelConfig != null ? modelConfig.getProviderType() : null,
                messages != null ? messages.size() : 0,
                toolsEnabled);

        AtomicBoolean firstChunk = new AtomicBoolean(true);
        Flux<ChatResponse> chatResponseFlux = promptBuilder.stream().chatResponse()
                .doOnNext(cr -> {
                    // Логируем только первый чанк - начало стрима
                    if (firstChunk.compareAndSet(true, false) && cr != null) {
                        log.info("Spring AI stream started - first chunk received");
                    }
                })
                .doOnComplete(() -> log.info("Spring AI stream completed"))
                .doOnError(e -> logStreamError(e, modelForStream, chatOptions != null ? chatOptions.body() : null));
        return new SpringAIStreamResponse(trackStreamIfPossible(modelForStream, chatResponseFlux));
    }

    @RotateOpenRouterModels
    public AIResponse callChat(
            SpringAIModelConfig modelConfig,
            AICommand command,
            AIBotChatOptions chatOptions,
            List<Message> messages
    ) {
        Object conversationId = command != null ? command.metadata().get(AICommand.THREAD_KEY_FIELD) : null;
        boolean toolsEnabled = command != null && command.modelTypes().contains(ModelType.TOOL_CALLING);
        Map<String, Object> body = chatOptions != null ? chatOptions.body() : null;
        return callChatOnce(modelConfig, body, conversationId, toolsEnabled, messages, chatOptions);
    }

    private AIResponse callChatOnce(
            SpringAIModelConfig modelConfig,
            Map<String, Object> body,
            Object conversationId,
            boolean toolsEnabled,
            List<Message> messages,
            AIBotChatOptions chatOptions
    ) {
        String modelName = resolveModelName(modelConfig, body);
        var promptBuilder = promptFactory.preparePrompt(
                modelConfig,
                modelName,
                body,
                conversationId,
                toolsEnabled,
                messages,
                chatOptions
        );

        log.info("Spring AI call request. model={}, providerType={}, messages={}, toolsEnabled={}",
                modelName,
                modelConfig != null ? modelConfig.getProviderType() : null,
                messages != null ? messages.size() : 0,
                toolsEnabled);
        try {
            ChatResponse response = promptBuilder.call().chatResponse();
            return new SpringAIResponse(response);
        } catch (WebClientResponseException webClientError) {
            log.error("Spring AI call error. model={}, status={}, bodyKeys={}, body={}",
                    modelName,
                    webClientError.getStatusCode(),
                    body != null ? body.keySet() : null,
                    truncate(webClientError.getResponseBodyAsString()));
            throw webClientError;
        }
    }

    @RotateOpenRouterModels
    public AIResponse callChatFromBody(
            SpringAIModelConfig modelConfig,
            Map<String, Object> requestBody,
            Object conversationId,
            boolean toolsEnabled,
            List<Message> messages
    ) {
        return callChatOnce(modelConfig, requestBody, conversationId, toolsEnabled, messages, null);
    }

    private Flux<ChatResponse> trackStreamIfPossible(String modelId, Flux<ChatResponse> flux) {
        OpenRouterStreamMetricsTracker tracker = openRouterStreamMetricsTrackerProvider.getIfAvailable();
        if (tracker == null) {
            return flux;
        }
        return tracker.track(modelId, flux);
    }

    private void logStreamError(Throwable error, String modelName, Map<String, Object> body) {
        if (error instanceof WebClientResponseException webClientError) {
            log.error("Spring AI stream error. model={}, status={}, body={}",
                    modelName,
                    webClientError.getStatusCode(),
                    truncate(webClientError.getResponseBodyAsString()));
        } else {
            log.error("Spring AI stream error. model={}, body={}", modelName, body, error);
        }
    }

    private String resolveModelName(SpringAIModelConfig modelConfig, Map<String, Object> body) {
        if (modelConfig != null && modelConfig.getName() != null) {
            return modelConfig.getName();
        }
        if (body == null || body.isEmpty()) {
            return null;
        }
        Object model = body.get("model");
        if (model instanceof String) {
            return (String) model;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) body.get("options");
        if (options != null) {
            Object optionsModel = options.get("model");
            if (optionsModel instanceof String) {
                return (String) optionsModel;
            }
        }
        return null;
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
