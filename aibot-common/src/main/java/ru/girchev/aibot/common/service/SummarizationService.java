package ru.girchev.aibot.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import ru.girchev.aibot.common.ai.command.ChatAICommand;
import ru.girchev.aibot.common.ai.ModelType;
import ru.girchev.aibot.common.config.CoreCommonProperties;
import ru.girchev.aibot.common.model.ConversationThread;
import ru.girchev.aibot.common.model.AIBotMessage;
import ru.girchev.aibot.common.model.MessageRole;
import ru.girchev.aibot.common.repository.AIBotMessageRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static ru.girchev.aibot.common.service.AIUtils.retrieveMessage;

/**
 * Сервис для автоматической summarization (сводки) длинных диалогов.
 * Когда диалог становится слишком длинным, автоматически создает сводку старых сообщений
 * и сохраняет ключевые факты в memory bullets.
 * <p>
 * Бин создается в CoreAutoConfig (не используется @Service для явного контроля).
 */
@RequiredArgsConstructor
@Slf4j
public class SummarizationService {

    private final AIBotMessageRepository messageRepository;
    private final ConversationThreadService threadService;
    private final AIGatewayRegistry aiGatewayRegistry;
    private final CoreCommonProperties coreCommonProperties;
    private final ObjectMapper objectMapper;
    
    // Синхронизация суммаризации по threadKey для предотвращения параллельных вызовов
    private final Set<String> ongoingSummarizations = ConcurrentHashMap.newKeySet();

    private static final String SUMMARIZATION_PROMPT = """
            Ты — ассистент, который создает краткие сводки диалогов.
            
            Проанализируй следующий диалог и создай:
            1. Краткую сводку (2-3 абзаца) основных тем и решений
            2. Список из 5-10 ключевых фактов/решений (bullet points)
            
            Формат ответа (строго JSON), не должно быть ```json {}```:
            {
              "summary": "краткая сводка диалога",
              "memory_bullets": ["факт 1", "факт 2", "факт 3", ...]
            }
            
            Диалог:
            """;

    /**
     * Проверяет, нужно ли запустить summarization по объему токенов.
     * Используется для не-Spring AI провайдеров (ConversationHistoryAICommandFactory).
     */
    public boolean shouldTriggerSummarization(ConversationThread thread) {
        Long totalTokens = thread.getTotalTokens();
        if (totalTokens == null || totalTokens == 0) {
            return false;
        }

        CoreCommonProperties.ConversationContextProperties contextConfig = coreCommonProperties.getConversationContext();
        double usageRatio = (double) totalTokens / contextConfig.getMaxContextTokens();
        boolean shouldTrigger = usageRatio >= contextConfig.getSummaryTriggerThreshold();

        if (shouldTrigger) {
            log.debug("Thread {} reached summarization threshold by tokens: {} tokens / {} max = {} (threshold: {})",
                    thread.getThreadKey(), totalTokens, contextConfig.getMaxContextTokens(),
                    usageRatio, contextConfig.getSummaryTriggerThreshold());
        }

        return shouldTrigger;
    }

    /**
     * Создает или обновляет сводку для thread
     * Вызывается асинхронно, когда thread превышает token budget
     */
    @Async("summarizationTaskExecutor")
    @Transactional
    public CompletableFuture<Void> summarizeThreadAsync(ConversationThread thread) {
        String threadKey = thread.getThreadKey();

        // Синхронизация по threadKey для предотвращения параллельных вызовов
        if (!ongoingSummarizations.add(threadKey)) {
            log.debug("Summarization already in progress for thread {}, skipping", threadKey);
            return CompletableFuture.completedFuture(null);
        }

        try {
            log.info("Starting summarization for thread {}", threadKey);

            // 1. Загружаем все сообщения из thread
            List<AIBotMessage> messages = messageRepository
                    .findByThreadOrderBySequenceNumberAsc(thread);

            if (messages.isEmpty()) {
                log.warn("No messages to summarize for thread {}", threadKey);
                return CompletableFuture.completedFuture(null);
            }

            // Асинхронный метод - загружаем все сообщения, фильтруем по keepRecentMessages
            CoreCommonProperties.ConversationContextProperties contextConfig = coreCommonProperties.getConversationContext();
            int keepRecentMessages = contextConfig.getDefaultWindowSize();
            List<AIBotMessage> messagesToSummarize = filterMessagesForSummarization(messages, keepRecentMessages, thread.getThreadKey());

            if (messagesToSummarize.isEmpty()) {
                log.info("No messages to summarize after filtering for thread {}", threadKey);
                return CompletableFuture.completedFuture(null);
            }

            performSummarization(thread, messagesToSummarize);

            log.info("Successfully summarized messages for thread {}", threadKey);
        } catch (Exception e) {
            log.error("Error during summarization for thread {}", threadKey, e);
        } finally {
            ongoingSummarizations.remove(threadKey);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Синхронный метод для суммаризации thread.
     * Принимает сообщения как параметр (не загружает из БД).
     * Используется в SummarizingChatMemory для Spring AI.
     */
    @Transactional
    public void summarizeThread(ConversationThread thread, List<AIBotMessage> messages) {
        String threadKey = thread.getThreadKey();

        // Синхронизация по threadKey для предотвращения параллельных вызовов
        if (!ongoingSummarizations.add(threadKey)) {
            log.debug("Summarization already in progress for thread {}, skipping", threadKey);
            return;
        }

        try {
            log.info("Starting synchronous summarization for thread {}", threadKey);

            if (messages.isEmpty()) {
                log.warn("No messages to summarize for thread {}", threadKey);
                return;
            }

            // Синхронный метод - сообщения уже отфильтрованы вызывающим кодом, не фильтруем
            performSummarization(thread, messages);

            log.info("Successfully completed synchronous summarization for thread {}", threadKey);
        } catch (Exception e) {
            log.error("Error during synchronous summarization for thread {}", threadKey, e);
            throw new RuntimeException("Summarization failed", e);
        } finally {
            ongoingSummarizations.remove(threadKey);
        }
    }

    /**
     * Фильтрует сообщения для суммаризации, оставляя последние N сообщений нетронутыми.
     * 
     * @param messages все сообщения
     * @param keepRecentMessages количество последних сообщений, которые нужно оставить
     * @param threadKey ключ потока для логирования
     * @return отфильтрованный список сообщений для суммаризации (пустой список, если нечего суммаризировать)
     */
    private List<AIBotMessage> filterMessagesForSummarization(List<AIBotMessage> messages, int keepRecentMessages, String threadKey) {
        // Если сообщений очень мало (<= 2), не суммаризируем вообще
        if (messages.size() <= 2) {
            log.info("Not enough messages to summarize for thread {} (only {} messages, need at least 3)",
                    threadKey, messages.size());
            return List.of();
        }
        
        int actualKeepMessages;
        if (messages.size() <= keepRecentMessages) {
            // Если сообщений меньше или равно keepRecentMessages, оставляем все
            actualKeepMessages = messages.size();
        } else {
            // Если сообщений больше keepRecentMessages, оставляем keepRecentMessages
            actualKeepMessages = keepRecentMessages;
        }
        
        // Вычисляем количество сообщений для суммаризации
        int messagesToSummarizeCount = messages.size() - actualKeepMessages;

        if (messagesToSummarizeCount <= 0) {
            log.info("Not enough messages to summarize for thread {} (only {} messages, keeping {} messages)",
                    threadKey, messages.size(), actualKeepMessages);
            return List.of();
        }
        
        List<AIBotMessage> messagesToSummarize = messages.subList(0, messagesToSummarizeCount);
        log.debug("Filtered messages: summarizing {} messages, keeping {} messages for thread {}",
                messagesToSummarizeCount, actualKeepMessages, threadKey);
        
        return messagesToSummarize;
    }

    /**
     * Общая логика суммаризации для синхронного и асинхронного методов.
     * Суммаризирует ВСЕ переданные сообщения без дополнительной фильтрации.
     * Вызывающий код должен передавать только те сообщения, которые нужно суммаризировать.
     */
    private void performSummarization(ConversationThread thread, List<AIBotMessage> messages) {
        if (messages.isEmpty()) {
            log.warn("No messages to summarize for thread {}", thread.getThreadKey());
            return;
        }

        log.debug("Summarizing {} messages for thread {}", messages.size(), thread.getThreadKey());

        // Формируем текст диалога для summarization из Message
        // Если есть предыдущая суммаризация, добавляем её в начало контекста
        StringBuilder dialogText = new StringBuilder();
        
        // Добавляем предыдущую суммаризацию, если она есть
        if (thread.getSummary() != null && !thread.getSummary().isEmpty()) {
            dialogText.append("=== Предыдущая суммаризация диалога ===\n");
            dialogText.append(thread.getSummary()).append("\n\n");
            
            if (thread.getMemoryBullets() != null && !thread.getMemoryBullets().isEmpty()) {
                dialogText.append("Ключевые моменты из предыдущей беседы:\n");
                thread.getMemoryBullets().forEach(bullet -> 
                    dialogText.append("• ").append(bullet).append("\n")
                );
                dialogText.append("\n");
            }
            
            dialogText.append("=== Продолжение диалога ===\n\n");
        }
        
        // Добавляем все переданные сообщения для суммаризации
        for (AIBotMessage message : messages) {
            if (message.getRole() == MessageRole.USER) {
                dialogText.append("USER: ").append(message.getContent()).append("\n\n");
            } else if (message.getRole() == MessageRole.ASSISTANT) {
                dialogText.append("ASSISTANT: ").append(message.getContent()).append("\n\n");
            }
        }
        String dialogTextStr = dialogText.toString();

        // Вызываем AI для создания сводки
        ChatAICommand summaryCommand = new ChatAICommand(
                Set.of(ModelType.SUMMARIZATION), // Можно использовать более дешевую модель
                0.3, // Низкая температура для детерминизма
                2000,
                SUMMARIZATION_PROMPT, // systemRole
                dialogTextStr // userRole
        );

        AIGateway aiGateway = aiGatewayRegistry
                .getSupportedAiGateways(summaryCommand)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No AI gateway for summarization"));

        String summaryResponse = retrieveMessage(aiGateway.generateResponse(summaryCommand))
                .orElseThrow(() -> new RuntimeException("Response is empty"));

        // Парсим JSON ответ
        SummaryResult result = parseSummaryResponse(summaryResponse);

        // Обновляем thread
        String combinedSummary = combineWithExistingSummary(thread.getSummary(), result.summary());
        List<String> combinedBullets = combineWithExistingBullets(
                thread.getMemoryBullets(), result.memoryBullets());

        threadService.updateThreadSummary(thread, combinedSummary, combinedBullets);

        log.info("Successfully summarized {} messages for thread {}",
                messages.size(), thread.getThreadKey());
    }

    private SummaryResult parseSummaryResponse(String response) {
        try {
            // Извлекаем JSON из markdown-блока, если он обёрнут в ```json
            String jsonContent = extractJsonFromMarkdown(response);
            
            JsonNode node = objectMapper.readTree(jsonContent);

            String summary = node.has("summary") ? node.get("summary").asText() : "";
            List<String> bullets = new ArrayList<>();

            if (node.has("memory_bullets") && node.get("memory_bullets").isArray()) {
                node.get("memory_bullets").forEach(b -> bullets.add(b.asText()));
            }

            return new SummaryResult(summary, bullets);
        } catch (Exception e) {
            log.error("Failed to parse summarization response, using fallback", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Извлекает JSON из markdown-блока, если он обёрнут в ```json
     * Если markdown-блок не найден, возвращает исходную строку
     */
    private String extractJsonFromMarkdown(String response) {
        if (response == null || response.trim().isEmpty()) {
            return response;
        }
        
        String trimmed = response.trim();
        
        // Проверяем, начинается ли строка с ```json или ```
        if (trimmed.startsWith("```json")) {
            // Удаляем ```json в начале и ``` в конце
            String content = trimmed.substring("```json".length());
            int endIndex = content.lastIndexOf("```");
            if (endIndex != -1) {
                return content.substring(0, endIndex).trim();
            }
        } else if (trimmed.startsWith("```")) {
            // Удаляем ``` в начале и ``` в конце (без указания языка)
            String content = trimmed.substring("```".length());
            int endIndex = content.lastIndexOf("```");
            if (endIndex != -1) {
                return content.substring(0, endIndex).trim();
            }
        }
        
        // Если markdown-блок не найден, возвращаем исходную строку
        return response;
    }

    private String combineWithExistingSummary(String existing, String newSummary) {
        if (existing == null || existing.isEmpty()) {
            return newSummary;
        }

        // Инкрементальное обновление: добавляем новую сводку к существующей
        return existing + "\n\n---\n\nПродолжение:\n" + newSummary;
    }

    private List<String> combineWithExistingBullets(List<String> existing, List<String> newBullets) {
        List<String> combined = new ArrayList<>(existing != null ? existing : new ArrayList<>());
        combined.addAll(newBullets);

        // Ограничиваем количество bullets (например, последние 20)
        if (combined.size() > 20) {
            return combined.subList(combined.size() - 20, combined.size());
        }

        return combined;
    }

    private record SummaryResult(String summary, List<String> memoryBullets) {
    }
}

