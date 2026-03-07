package ru.girchev.aibot.common.service;

import lombok.extern.slf4j.Slf4j;
import ru.girchev.aibot.common.config.CoreCommonProperties;
import ru.girchev.aibot.common.model.AssistantRole;
import ru.girchev.aibot.common.model.ConversationThread;
import ru.girchev.aibot.common.model.AIBotMessage;
import ru.girchev.aibot.common.model.MessageRole;
import ru.girchev.aibot.common.repository.AIBotMessageRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Сервис для построения контекста для AI запроса с учетом token budget.
 * Формирует "скользящее окно" истории диалога, включая summary и memory bullets.
 * Работает с новой структурой Message вместо UserRequest/ServiceResponse.
 */
@Slf4j
public class ConversationContextBuilderService {
    
    private final AIBotMessageRepository messageRepository;
    private final TokenCounter tokenCounter;
    private final CoreCommonProperties coreCommonProperties;
    
    public ConversationContextBuilderService(
            AIBotMessageRepository messageRepository,
            TokenCounter tokenCounter,
            CoreCommonProperties coreCommonProperties) {
        this.messageRepository = messageRepository;
        this.tokenCounter = tokenCounter;
        this.coreCommonProperties = coreCommonProperties;
    }
    
    /**
     * Строит контекст для AI запроса с учетом token budget
     * 
     * @param thread текущий conversation thread
     * @param currentUserMessage новый запрос пользователя
     * @param assistantRole роль ассистента (AssistantRole Entity)
     * @return список сообщений в формате API: List<Map<String, String>> где каждый Map содержит "role" и "content"
     */
    public List<Map<String, String>> buildContext(
            ConversationThread thread, 
            String currentUserMessage,
            AssistantRole assistantRole) {
        
        List<Map<String, String>> context = new ArrayList<>();
        CoreCommonProperties.ConversationContextProperties contextConfig = coreCommonProperties.getConversationContext();
        int remainingTokens = contextConfig.getMaxContextTokens();
        
        // 1. Добавляем system prompt из AssistantRole (обязательно)
        if (contextConfig.getIncludeSystemPrompt() && assistantRole != null) {
            String systemPrompt = assistantRole.getContent();
            int systemTokens = tokenCounter.estimateTokens(systemPrompt);
            context.add(Map.of("role", "system", "content", systemPrompt));
            remainingTokens -= systemTokens;
            log.debug("Added system prompt from AssistantRole {}: {} tokens", 
                assistantRole.getId(), systemTokens);
        }
        
        // 2. Добавляем summary (если есть)
        if (thread.getSummary() != null && !thread.getSummary().isEmpty()) {
            String summaryContent = "Краткое содержание предыдущей беседы:\n" + thread.getSummary();
            if (thread.getMemoryBullets() != null && !thread.getMemoryBullets().isEmpty()) {
                summaryContent += "\n\nКлючевые моменты:\n" + 
                    String.join("\n", thread.getMemoryBullets());
            }
            int summaryTokens = tokenCounter.estimateTokens(summaryContent);
            context.add(Map.of("role", "system", "content", summaryContent));
            remainingTokens -= summaryTokens;
            log.debug("Added summary: {} tokens", summaryTokens);
        }
        
        // 3. Загружаем историю из Message (уже отсортированы по sequence_number)
        List<AIBotMessage> messages = messageRepository.findByThreadOrderBySequenceNumberAsc(thread);
        
        // 4. Добавляем сообщения, пока не упремся в лимит
        List<Map<String, String>> historyMessages = new ArrayList<>();
        int historyTokens = 0;
        int currentMessageTokens = tokenCounter.estimateTokens(currentUserMessage);
        
        for (AIBotMessage message : messages) {
            // Пропускаем SYSTEM сообщения (они уже добавлены выше)
            if (message.getRole() == MessageRole.SYSTEM) {
                continue;
            }
            
            String content = message.getContent();
            if (content == null || content.isEmpty()) {
                log.warn("Skipping Message {} with null or empty content", message.getId());
                continue;
            }
            
            int messageTokens = tokenCounter.estimateTokens(content);
            
            // Проверяем, не превысим ли мы лимит, если добавим это сообщение
            // Оставляем запас для ответа модели (maxResponseTokens)
            if (remainingTokens - messageTokens - currentMessageTokens < 
                contextConfig.getMaxResponseTokens()) {
                log.debug("Token budget exceeded, stopping at {} messages", historyMessages.size());
                break;
            }
            
            String role = message.getRole() == MessageRole.USER ? "user" : "assistant";
            historyMessages.add(Map.of("role", role, "content", content));
            historyTokens += messageTokens;
            remainingTokens -= messageTokens;
        }
        
        context.addAll(historyMessages);
        log.info("Added {} history messages ({} tokens), remaining budget: {}", 
            historyMessages.size(), historyTokens, remainingTokens);
        
        // 5. Добавляем текущий запрос пользователя
        int currentTokens = tokenCounter.estimateTokens(currentUserMessage);
        context.add(Map.of("role", "user", "content", currentUserMessage));
        remainingTokens -= currentTokens;
        
        log.info("Final context: {} messages, ~{} tokens used, ~{} tokens remaining for response",
            context.size(), 
            contextConfig.getMaxContextTokens() - remainingTokens,
            remainingTokens);
        
        return context;
    }
}

