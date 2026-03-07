package ru.girchev.aibot.common.service;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Flux;
import ru.girchev.aibot.common.ai.response.AIResponse;
import ru.girchev.aibot.common.ai.response.SpringAIResponse;
import ru.girchev.aibot.common.ai.response.SpringAIStreamResponse;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static ru.girchev.aibot.common.ai.LlmParamNames.ACTUAL_MODEL;
import static ru.girchev.aibot.common.ai.LlmParamNames.CHOICES;
import static ru.girchev.aibot.common.ai.LlmParamNames.COMPLETION_TOKENS;
import static ru.girchev.aibot.common.ai.LlmParamNames.CONTENT;
import static ru.girchev.aibot.common.ai.LlmParamNames.FINISH_REASON;
import static ru.girchev.aibot.common.ai.LlmParamNames.GENERATION_RESULTS;
import static ru.girchev.aibot.common.ai.LlmParamNames.ID;
import static ru.girchev.aibot.common.ai.LlmParamNames.MESSAGE;
import static ru.girchev.aibot.common.ai.LlmParamNames.MODEL;
import static ru.girchev.aibot.common.ai.LlmParamNames.PROMPT_TOKENS;
import static ru.girchev.aibot.common.ai.LlmParamNames.RESPONSE_CALLS;
import static ru.girchev.aibot.common.ai.LlmParamNames.TOTAL_TOKENS;
import static ru.girchev.aibot.common.ai.LlmParamNames.USAGE;

@UtilityClass
@Slf4j
public class AIUtils {

    /**
     * Извлекает сообщение из AIResponse.
     *
     * @param aiResponse ответ от AI провайдера
     * @return Optional с текстом сообщения
     */
    public static Optional<String> retrieveMessage(AIResponse aiResponse) {
        if (aiResponse == null) {
            return Optional.empty();
        }

        return switch (aiResponse.gatewaySource()) {
            case SPRINGAI -> {
                if (aiResponse instanceof SpringAIResponse springAIStreamResponse) {
                    yield retrieveMessageFromSpringAI(springAIStreamResponse);
                } else if (aiResponse instanceof SpringAIStreamResponse) {
                    throw new UnsupportedOperationException("Use AIUtils.processStreamingResponse first, because it's streaming response");
                } else {
                    throw new UnsupportedOperationException("Can't handle this class: " + aiResponse.getClass());
                }
            }
            case DEEPSEEK, OPENROUTER, MOCK -> retrieveMessage(aiResponse.toMap());
        };
    }

    /**
     * Извлекает полезные данные из AIResponse.
     *
     * @param aiResponse ответ от AI провайдера
     * @return Map с полезными данными или null, если нет полезных данных
     */
    public static Map<String, Object> extractUsefulData(AIResponse aiResponse) {
        if (aiResponse == null) {
            return null;
        }

        return switch (aiResponse.gatewaySource()) {
            case SPRINGAI -> {
                if (aiResponse instanceof SpringAIResponse springAIStreamResponse) {
                    yield extractSpringAiUsefulData(springAIStreamResponse.chatResponse());
                } else if (aiResponse instanceof SpringAIStreamResponse) {
                    throw new UnsupportedOperationException("Use AIUtils.processStreamingResponse first, because it's streaming response");
                } else {
                    throw new UnsupportedOperationException("Can't handle this class: " + aiResponse.getClass());
                }
            }
            case DEEPSEEK, OPENROUTER, MOCK -> extractUsefulData(aiResponse.toMap());
        };
    }

    public static Optional<String> extractError(AIResponse aiResponse) {
        if (aiResponse == null) {
            return Optional.empty();
        }

        return switch (aiResponse.gatewaySource()) {
            case SPRINGAI -> {
                if (aiResponse instanceof SpringAIResponse springAIStreamResponse) {
                    yield extractError(springAIStreamResponse.chatResponse());
                } else if (aiResponse instanceof SpringAIStreamResponse) {
                    throw new UnsupportedOperationException("Use AIUtils.processStreamingResponse first, because it's streaming response");
                } else {
                    throw new UnsupportedOperationException("Can't handle this class: " + aiResponse.getClass());
                }
            }
            case DEEPSEEK, OPENROUTER, MOCK -> extractError(aiResponse.toMap());
        };
    }

    public static Map<String, Object> extractSpringAiUsefulData(ChatResponse chatResponse) {
        if (chatResponse == null) {
            log.debug("SpringAIResponse or ChatResponse is null, returning null");
            return null;
        }

        Map<String, Object> usefulData;

        try {
            var chatResponseMetadata = chatResponse.getMetadata();
            var id = chatResponseMetadata.getId();
            var usage = chatResponseMetadata.getUsage();
            var model = chatResponseMetadata.getModel();
            var responseCalls = chatResponse.getResults().size();
            var generationResults = chatResponse.getResults().stream().map(r -> new GenerationResult(
                    r.getOutput().getToolCalls().size(),
                    r.getMetadata().getFinishReason()
            )).toList();

            usefulData = convertChatResponseMetadataToMap(new ChatResponseMetadata(id, usage, model, responseCalls, generationResults));
        } catch (Exception e) {
            log.error("Error extracting useful data from SpringAIResponse: {}", e.getMessage(), e);
            return null;
        }

        if (!usefulData.isEmpty()) {
            log.debug("Extracted useful data from SpringAIResponse: {}", usefulData);
            return usefulData;
        } else {
            log.debug("No useful data found in SpringAIResponse");
            return null;
        }
    }

    private static Map<String, Object> convertChatResponseMetadataToMap(ChatResponseMetadata chatResponseMetadata) {
		return Map.of(
			ID, chatResponseMetadata.id(),
			USAGE, chatResponseMetadata.usage(),
			MODEL, chatResponseMetadata.model(),
			RESPONSE_CALLS, chatResponseMetadata.responseCalls(),
			GENERATION_RESULTS, chatResponseMetadata.generationResults()
		);
	}

    public static Optional<String> retrieveMessage(Map<String, Object> aiRawResponse) {
        Optional<String> answer = Optional.empty();
        if (aiRawResponse != null && aiRawResponse.containsKey(CHOICES)) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) aiRawResponse.get(CHOICES);
            if (!choices.isEmpty()) {
                Map<String, Object> firstChoice = choices.getFirst();
                Map<String, Object> message = (Map<String, Object>) firstChoice.get(MESSAGE);
                String content = (String) message.get(CONTENT);

                answer = Optional.ofNullable(content);

                // Если content пустой, логируем предупреждение
                if (content == null || content.isEmpty()) {
                    log.warn("Content is empty in response");
                }
            } else {
                log.error("Response is incorrect. Choices is empty. Response: {}", aiRawResponse);
            }
        } else {
            log.error("Ai response null or doesn't contains choices. Response: {}", aiRawResponse);
        }
        return answer.filter(StringUtils::hasLength);
    }

    private static Optional<String> retrieveMessageFromSpringAI(SpringAIResponse response) {
        try {
            String content = response.chatResponse().getResult().getOutput().getText();
            return Optional.ofNullable(content).filter(StringUtils::hasLength);
        } catch (Exception e) {
            log.error("Error extracting message from SpringAIResponse", e);
            return Optional.empty();
        }
    }

    /**
     * Извлекает полезные данные из ответа AI провайдера.
     * Сохраняет только данные, которых нет в таблице message:
     * - usage.prompt_tokens - реальное количество токенов в промпте (у нас только оценка)
     * - usage.completion_tokens - реальное количество токенов в ответе (у нас только оценка)
     * - usage.total_tokens - общее количество токенов (у нас только оценка)
     * - finish_reason - причина завершения (stop, length, content_filter и т.д.)
     * - model - реальная модель, которая использовалась (может отличаться от запрошенной)
     *
     * @param aiRawResponse сырой ответ от AI провайдера
     * @return Map с полезными данными или null, если нет полезных данных
     */

    public static Map<String, Object> extractUsefulData(Map<String, Object> aiRawResponse) {
        if (aiRawResponse == null || aiRawResponse.isEmpty()) {
            log.debug("AI response is null or empty, returning null");
            return null;
        }

        log.info("Full response structure - usage: {}, model: {}, choices: {}",
                aiRawResponse.get(USAGE),
                aiRawResponse.get(MODEL),
                aiRawResponse.get(CHOICES));

        Map<String, Object> usefulData = new HashMap<>();
        boolean hasUsefulData = false;

        // Извлекаем usage данные (реальные токены от провайдера)
        Object usageObj = aiRawResponse.get(USAGE);
        log.debug("Usage object: type={}, value={}", usageObj != null ? usageObj.getClass() : "null", usageObj);
        if (usageObj != null) {
            try {
                // Пытаемся преобразовать в Map, если это не Map напрямую
                Map<String, Object> usage;
                if (usageObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> usageMap = (Map<String, Object>) usageObj;
                    usage = usageMap;
                } else {
                    log.warn("Usage is not a Map, skipping. Type: {}", usageObj.getClass());
                    usage = null;
                }

                if (usage != null) {
                    Object promptTokens = usage.get(PROMPT_TOKENS);
                    Object completionTokens = usage.get(COMPLETION_TOKENS);
                    Object totalTokens = usage.get(TOTAL_TOKENS);


                    if (promptTokens != null) {
                        usefulData.put(PROMPT_TOKENS, promptTokens);
                        hasUsefulData = true;
                    }
                    if (completionTokens != null) {
                        usefulData.put(COMPLETION_TOKENS, completionTokens);
                        hasUsefulData = true;
                    }
                    if (totalTokens != null) {
                        usefulData.put(TOTAL_TOKENS, totalTokens);
                        hasUsefulData = true;
                    }
                }
            } catch (Exception e) {
                log.error("Error extracting usage data: {}", e.getMessage(), e);
            }
        } else {
            log.warn("Usage data not found in response");
        }

        // Извлекаем finish_reason из корня Map (для Spring AI) или из первого choice (для OpenRouter/DeepSeek)
        Object finishReasonObj = aiRawResponse.get(FINISH_REASON);
        if (finishReasonObj != null) {
            usefulData.put(FINISH_REASON, finishReasonObj);
            hasUsefulData = true;
            log.debug("Extracted finish_reason from root: {}", finishReasonObj);
        } else {
            // Fallback: извлекаем finish_reason из первого choice (для обратной совместимости с OpenRouter/DeepSeek)
            Object choicesObj = aiRawResponse.get(CHOICES);
            if (choicesObj != null) {
                try {
                    if (choicesObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<?> choices = (List<?>) choicesObj;
                        if (!choices.isEmpty()) {
                            Object firstChoiceObj = choices.getFirst();
                            if (firstChoiceObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> firstChoice = (Map<String, Object>) firstChoiceObj;
                                Object finishReason = firstChoice.get(FINISH_REASON);
                                if (finishReason != null) {
                                    usefulData.put(FINISH_REASON, finishReason);
                                    hasUsefulData = true;
                                    log.debug("Extracted finish_reason from choices[0]: {}", finishReason);
                                } else {
                                    log.debug("finish_reason not found in first choice. Available keys: {}", firstChoice.keySet());
                                }
                            } else {
                                log.debug("First choice is not a Map. Type: {}", firstChoiceObj.getClass());
                            }
                        } else {
                            log.debug("Choices list is empty");
                        }
                    } else {
                        log.debug("Choices is not a List. Type: {}", choicesObj.getClass());
                    }
                } catch (Exception e) {
                    log.error("Error extracting finish_reason from choices: {}", e.getMessage(), e);
                }
            } else {
                log.debug("finish_reason not found in root and choices not found in response");
            }
        }

        // Извлекаем реальную модель, которая использовалась (может отличаться от запрошенной)
        Object modelObj = aiRawResponse.get(MODEL);
        if (modelObj != null) {
            String actualModel = modelObj.toString();
            usefulData.put(ACTUAL_MODEL, actualModel);
            hasUsefulData = true;
            log.debug("Extracted actual_model: {}", actualModel);
        } else {
            log.debug("Model not found in response");
        }

        if (hasUsefulData) {
            log.debug("Extracted useful data: {}", usefulData);
        } else {
            log.warn("No useful data found in AI response. Response structure: usage={}, choices={}, model={}",
                    aiRawResponse.get(USAGE) != null ? "present" : "null",
                    aiRawResponse.get(CHOICES) != null ? "present" : "null",
                    aiRawResponse.get(MODEL) != null ? aiRawResponse.get(MODEL) : "null");
        }

        return hasUsefulData ? usefulData : null;
    }

    /**
     * Обрабатывает streaming ответ от AI, разбивая его по абзацам (двойные переносы строк \n\n)
     * и отправляя посимвольно (с корректной обработкой emoji через codePoints).
     *
     * @param responseFlux поток ответов от AI
     * @param listener обработчик для каждого символа
     * @return итоговый ChatResponse с полным текстом и метаданными
     * @throws WebClientException если произошла ошибка при обработке стрима
     */
    public static ChatResponse processStreamingResponseByParagraphs(
            Flux<ChatResponse> responseFlux,
            Consumer<String> listener
    ) throws WebClientException {
        // Дефолтный таймаут: 10 минут (600 секунд)
        return processStreamingResponseByParagraphs(responseFlux, listener, Duration.ofMinutes(10));
    }
    
    /**
     * Обрабатывает streaming ответ от AI, разбивая его по абзацам (двойные переносы строк \n\n)
     * и отправляя посимвольно (с корректной обработкой emoji через codePoints).
     *
     * @param responseFlux поток ответов от AI
     * @param listener обработчик для каждого символа
     * @param timeout таймаут ожидания завершения стрима
     * @return итоговый ChatResponse с полным текстом и метаданными
     * @throws WebClientException если произошла ошибка при обработке стрима
     */
    public static ChatResponse processStreamingResponseByParagraphs(
            Flux<ChatResponse> responseFlux,
            Consumer<String> listener,
            Duration timeout
    ) throws WebClientException {
        AtomicReference<String> fullResponse = new AtomicReference<>("");
        AtomicReference<String> tail = new AtomicReference<>("");
        AtomicReference<String> accumulatedShortParagraphs = new AtomicReference<>("");
        AtomicReference<ChatResponse> lastResponse = new AtomicReference<>(null);
        
        final int MIN_PARAGRAPH_LENGTH = 300;

        try {
            // Используем ОДИН поток chatResponse() - из него извлекаем и текст, и метаданные
            responseFlux
                    .doOnError(error -> log.warn("Error in streaming response: {}", error.getMessage()))
                    .doOnNext(lastResponse::set) // Сохраняем последний ответ для метаданных
                    .map(AIUtils::extractText)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .flatMap(chunk -> {
                        try {
                            // Объединяем хвост с новым чанком
                            String text = tail.get() + chunk;

                            // Разбиваем на абзацы по двойным переносам строк
                            String[] paragraphs = text.split("\n\n", -1);

                            // Проверяем, заканчивается ли текст на \n\n
                            boolean endsWithParagraph = text.endsWith("\n\n");

                            if (endsWithParagraph) {
                                // Текст заканчивается разделителем - обрабатываем все параграфы
                                tail.set("");
                                return Flux.fromArray(paragraphs);
                            } else {
                                // Последний кусок может быть незавершённым абзацем - оставляем в хвосте
                                tail.set(paragraphs[paragraphs.length - 1]);
                                return Flux.fromArray(Arrays.copyOfRange(paragraphs, 0, paragraphs.length - 1));
                            }
                        } catch (Exception e) {
                            log.debug("Error processing chunk: {}", e.getMessage());
                            return Flux.empty();
                        }
                    })
                    // Фильтруем пустые абзацы
                    .filter(paragraph -> !paragraph.trim().isEmpty())
                    // Обрабатываем параграфы с учётом минимальной длины
                    .flatMap(paragraph -> {
                        String trimmed = paragraph.trim();
                        
                        // Если параграф короткий (< 100 символов), накапливаем его
                        if (trimmed.length() < MIN_PARAGRAPH_LENGTH) {
                            String toSend = accumulatedShortParagraphs.get();
                            toSend = toSend.isEmpty() ? trimmed : toSend + "\n\n" + trimmed;
                            accumulatedShortParagraphs.set(toSend);
                            
                            // Если накопленный текст достиг минимальной длины, отправляем его
                            if (toSend.length() >= MIN_PARAGRAPH_LENGTH) {
                                accumulatedShortParagraphs.set("");
                                return Flux.just(toSend);
                            }
                            return Flux.empty();
                        } else {
                            // Параграф достаточно длинный
                            String accumulated = accumulatedShortParagraphs.get();
                            if (!accumulated.isEmpty()) {
                                // Отправляем накопленные короткие параграфы вместе с текущим
                                accumulatedShortParagraphs.set("");
                                return Flux.just(accumulated + "\n\n" + trimmed);
                            }
                            return Flux.just(trimmed);
                        }
                    })
                    // Отправляем каждый блок целиком
                    .doOnNext(block -> {
                        fullResponse.updateAndGet(current -> current + block + "\n\n");
                        listener.accept(block + "\n\n");
                    })
                    .blockLast(timeout);

            // Получаем последний ChatResponse для метаданных
            ChatResponse finalResponse = lastResponse.get();

            // Обрабатываем оставшийся хвост
            String remainingTail = tail.get().trim();
            if (!remainingTail.isEmpty()) {
                // Если хвост короткий, добавляем его к накопленным коротким параграфам
                if (remainingTail.length() < MIN_PARAGRAPH_LENGTH) {
                    String accumulated = accumulatedShortParagraphs.get();
                    accumulated = accumulated.isEmpty() ? remainingTail : accumulated + "\n\n" + remainingTail;
                    accumulatedShortParagraphs.set(accumulated);
                } else {
                    // Хвост достаточно длинный - отправляем его вместе с накопленными
                    String accumulated = accumulatedShortParagraphs.get().trim();
                    if (!accumulated.isEmpty()) {
                        fullResponse.updateAndGet(current -> current + accumulated + "\n\n" + remainingTail);
                        listener.accept(accumulated + "\n\n" + remainingTail);
                        accumulatedShortParagraphs.set("");
                    } else {
                        fullResponse.updateAndGet(current -> current + remainingTail);
                        listener.accept(remainingTail);
                    }
                }
            }

            // Отправляем накопленные короткие параграфы, если остались
            String accumulated = accumulatedShortParagraphs.get().trim();
            if (!accumulated.isEmpty()) {
                fullResponse.updateAndGet(current -> current + accumulated);
                listener.accept(accumulated);
            }

            // Получаем финальный текст
            String finalText = fullResponse.get().trim();

            // Если получили данные из стрима, возвращаем их
            if (!finalText.isEmpty() && finalResponse != null) {
                AssistantMessage fullMessage = new AssistantMessage(finalText);
                Generation generation = new Generation(fullMessage);

                return ChatResponse.builder()
                        .generations(List.of(generation))
                        .metadata(finalResponse.getMetadata())
                        .build();
            }

            // Если данных нет, но есть finalResponse, возвращаем его
            if (finalResponse != null) {
                return finalResponse;
            }

            // Если ничего не получили, это будет обработано в catch блоке
            throw new RuntimeException("No data received from streaming response");

        } catch (Exception e) {
            log.error("Error processing streaming response: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Обрабатывает streaming ответ от AI, отправляя каждый символ отдельно
     * (с корректной обработкой emoji через codePoints).
     *
     * @param responseFlux поток ответов от AI
     * @param listener обработчик для каждого символа
     * @return итоговый ChatResponse с полным текстом и метаданными
     * @throws WebClientException если произошла ошибка при обработке стрима
     */
    public static ChatResponse processStreamingResponse(
            Flux<ChatResponse> responseFlux,
            Consumer<String> listener
    ) throws WebClientException {
        // Дефолтный таймаут: 10 минут (600 секунд)
        return processStreamingResponse(responseFlux, listener, Duration.ofMinutes(10));
    }
    
    /**
     * Обрабатывает streaming ответ от AI, отправляя каждый символ отдельно
     * (с корректной обработкой emoji через codePoints).
     *
     * @param responseFlux поток ответов от AI
     * @param listener обработчик для каждого символа
     * @param timeout таймаут ожидания завершения стрима
     * @return итоговый ChatResponse с полным текстом и метаданными
     * @throws WebClientException если произошла ошибка при обработке стрима
     */
    public static ChatResponse processStreamingResponse(
            Flux<ChatResponse> responseFlux,
            Consumer<String> listener,
            Duration timeout
    ) throws WebClientException {
        AtomicReference<ChatResponse> lastResponse = new AtomicReference<>(null);
        StringBuilder fullResponse = new StringBuilder();

        try {
            // Используем ОДИН поток chatResponse() - из него извлекаем и текст, и метаданные
            responseFlux
                    .doOnNext(lastResponse::set)
                    .map(AIUtils::extractText)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .doOnNext(fullResponse::append)
                    // режем каждый chunk на символы (codepoints, не ломает эмодзи)
                    .flatMap(chunk -> Flux.fromStream(chunk.codePoints().mapToObj(cp -> new String(Character.toChars(cp)))))
                    .doOnNext(listener)
                    .blockLast(timeout);

            // Получаем последний ChatResponse для метаданных
            ChatResponse finalResponse = lastResponse.get();

            // Получаем финальный текст
            String finalText = fullResponse.toString().trim();

            // Если получили данные из стрима, возвращаем их
            if (!finalText.isEmpty() && finalResponse != null) {
                AssistantMessage fullMessage = new AssistantMessage(finalText);
                Generation generation = new Generation(fullMessage);

                return ChatResponse.builder()
                        .generations(List.of(generation))
                        .metadata(finalResponse.getMetadata())
                        .build();
            }

            // Если данных нет, но есть finalResponse, возвращаем его
            if (finalResponse != null) {
                return finalResponse;
            }

            // Если ничего не получили, это будет обработано в catch блоке
            throw new RuntimeException("No data received from streaming response");

        } catch (Exception e) {
            log.error("Error processing streaming response: {}", e.getMessage(), e);
            throw e;
        }
    }

    public static Optional<String> extractText(ChatResponse response) {
        try {
            return Optional.ofNullable(response.getResult().getOutput().getText()).filter(StringUtils::hasLength);
        } catch (Exception e) {
            log.warn("Could not extract content from stream chunk: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Конвертирует Markdown разметку в HTML теги для Telegram Bot API.
     * Поддерживает: жирный курсив (***текст***), жирный (**текст**), курсив (*текст*),
     * код (`текст`), зачеркнутый (~~текст~~).
     * Также экранирует HTML символы для безопасной отправки.
     *
     * @param text исходный текст с Markdown разметкой
     * @return текст с HTML тегами, готовый для отправки с parse_mode="HTML"
     */
    public static String convertMarkdownToHtml(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Сначала экранируем HTML символы
        String escaped = text
                .replace("&", "&amp;")  // Сначала &, чтобы не экранировать уже экранированные символы
                .replace("<", "&lt;")
                .replace(">", "&gt;");

        // Конвертируем Markdown в HTML (важен порядок - сначала тройные звездочки)
        // ***текст*** -> <b><i>текст</i></b> (жирный курсив)
        String html = escaped.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "<b><i>$1</i></b>");

        // **текст** -> <b>текст</b> (жирный)
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");

        // *текст* -> <i>текст</i> (курсив)
        // После обработки тройных и двойных звездочек, оставшиеся одинарные - это курсив
        html = html.replaceAll("\\*([^*]+?)\\*", "<i>$1</i>");

        // `текст` -> <code>текст</code> (код)
        html = html.replaceAll("`(.+?)`", "<code>$1</code>");

        // ~~текст~~ -> <s>текст</s> (зачеркнутый)
        html = html.replaceAll("~~(.+?)~~", "<s>$1</s>");

        return html;
    }

    private Optional<String> extractError(Map<String, Object> responseMap) {
        if (responseMap == null) {
            return Optional.of("Response is null");
        }

        Object choicesObj = responseMap.get(CHOICES);
        if (choicesObj instanceof List<?> choices) {
            if (choices.isEmpty()) {
                return Optional.of("Response contains empty choices list");
            }

            Object firstChoiceObj = choices.getFirst();
            if (firstChoiceObj instanceof Map<?, ?> firstChoice) {
                Object finishReasonObj = firstChoice.get(FINISH_REASON);
                String finishReason = finishReasonObj != null ? finishReasonObj.toString() : null;

                Object messageObj = firstChoice.get(MESSAGE);
                if (messageObj instanceof Map<?, ?> message) {
                    Object contentObj = message.get(CONTENT);
                    String content = contentObj != null ? contentObj.toString() : null;

                    if (content == null || content.isEmpty()) {
                        return Optional.of(getEmptyContentReasonText(finishReason));
                    }
                }
            }
        } else if (choicesObj == null) {
            return Optional.of("Response does not contain choices field");
        }

        return Optional.empty();
    }

    public static Optional<String> extractError(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return Optional.of("ChatResponse is null");
        }

        try {
            // Проверяем наличие result
            chatResponse.getResult();

            // Извлекаем content
            String content = null;
            try {
                content = chatResponse.getResult().getOutput().getText();
            } catch (Exception e) {
                log.debug("Could not extract content from ChatResponse: {}", e.getMessage());
            }

            // Если content пустой, извлекаем finishReason для определения причины
            if (content == null || content.isEmpty()) {
                String finishReason = extractFinishReason(chatResponse);
                return Optional.of(getEmptyContentReasonText(finishReason));
            }

            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error extracting error from ChatResponse: {}", e.getMessage(), e);
            return Optional.of("Failed to extract error from response: " + e.getMessage());
        }
    }

    public static String extractFinishReason(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return null;
        }
        try {
            return chatResponse.getResult().getMetadata().getFinishReason();
        } catch (Exception e) {
            log.debug("Could not extract finishReason from ChatResponse: {}", e.getMessage());
        }

        return null;
    }

    public static String getEmptyContentReasonText(String finishReason) {
        if (finishReason == null) {
            return "Content is empty (finish_reason is not provided)";
        }

        return switch (finishReason.toUpperCase()) {
            case "LENGTH" ->
                    "Token limit reached (finish_reason: length). Model hit max_tokens limit and response was truncated.";
            case "CONTENT_FILTER" ->
                    "Content was filtered by safety system (finish_reason: content_filter). Response may contain unsafe content.";
            case "STOP" -> "Model stopped naturally but content is empty (finish_reason: stop). This is unexpected.";
            case "FUNCTION_CALL" ->
                    "Model requested function call instead of text response (finish_reason: function_call).";
            case "TOOL_CALLS" -> "Model requested tool calls instead of text response (finish_reason: tool_calls).";
            default -> "Content is empty (finish_reason: " + finishReason + ")";
        };
    }

    public record ChatResponseMetadata(String id, Usage usage, String model, int responseCalls, List<GenerationResult> generationResults) { }
    public record GenerationResult(int toolCalls, String finishReason) { }
}
