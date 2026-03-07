package ru.girchev.aibot.ai.springai.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.lang.NonNull;
import ru.girchev.aibot.common.model.ConversationThread;
import ru.girchev.aibot.common.model.AIBotMessage;
import ru.girchev.aibot.common.repository.AIBotMessageRepository;
import ru.girchev.aibot.common.repository.ConversationThreadRepository;
import ru.girchev.aibot.common.service.SummarizationService;

import java.util.List;
import java.util.Optional;

/**
 * Кастомная реализация ChatMemory, которая интегрирует SummarizationService.
 * 
 * Основные возможности:
 * 1. Делегирует основную работу MessageWindowChatMemory
 * 2. При загрузке истории добавляет summary из ConversationThread как SystemMessage
 * 3. При сохранении проверяет, нужно ли запустить суммаризацию через SummarizationService
 * 
 * ВАЖНО: conversationId соответствует thread_key из ConversationThread.
 */
@Slf4j
public class SummarizingChatMemory implements ChatMemory {

    private final MessageWindowChatMemory delegate; // MessageWindowChatMemory
    private final ConversationThreadRepository conversationThreadRepository;
    private final AIBotMessageRepository messageRepository;
    private final SummarizationService summarizationService;
    private final Integer maxMessages; // Максимальное количество сообщений из MessageWindowChatMemory

    public SummarizingChatMemory(
            ChatMemoryRepository chatMemoryRepository,
            ConversationThreadRepository conversationThreadRepository,
            AIBotMessageRepository messageRepository,
            SummarizationService summarizationService,
            Integer maxMessages) {
        this.conversationThreadRepository = conversationThreadRepository;
        this.messageRepository = messageRepository;
        this.summarizationService = summarizationService;
        this.maxMessages = maxMessages;
        this.delegate = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }

    @Override
    @NonNull
    public List<Message> get(@NonNull String conversationId) {
        // Получаем сообщения из delegate (MessageWindowChatMemory)
        List<Message> messages = delegate.get(conversationId);
        
        // Проверяем количество сообщений в ChatMemory
        int messageCount = messages.size();
        
        // Если сообщений больше maxMessages, нужно суммаризировать
        if (messageCount >= maxMessages) {
            log.debug("ChatMemory has {} messages (max: {}), triggering summarization for conversationId {}", 
                messageCount, maxMessages, conversationId);
            
            // Выполняем суммаризацию и обновляем ChatMemory
            if (performSummarizationAndUpdateChatMemory(conversationId)) {
                // После успешной суммаризации получаем обновленный список сообщений
                messages = delegate.get(conversationId);
                log.debug("After summarization, ChatMemory has {} messages for conversationId {}", 
                    messages.size(), conversationId);
            }
        }
        
        return messages;
    }

    @Override
    public void add(@NonNull String conversationId, @NonNull Message message) {
        // Сохраняем сообщение через delegate
        delegate.add(conversationId, message);
    }

    @Override
    public void add(@NonNull String conversationId, @NonNull List<Message> messages) {
        // Сохраняем сообщения через delegate
        delegate.add(conversationId, messages);
    }

    /**
     * Выполняет суммаризацию и обновляет ChatMemory.
     * 
     * Логика:
     * 1. Получает все сообщения из основной БД для суммаризации
     * 2. Вызывает синхронную суммаризацию (синхронизация внутри SummarizationService)
     * 3. Очищает ChatMemory (delegate.clear) - удаляет сообщения из SPRING_AI_CHAT_MEMORY
     * 4. Добавляет summary как SystemMessage в ChatMemory (delegate.add)
     * 
     * @param conversationId идентификатор разговора
     * @return true если суммаризация выполнена успешно, false в противном случае
     */
    private boolean performSummarizationAndUpdateChatMemory(@NonNull String conversationId) {
        try {
            Optional<ConversationThread> threadOpt = conversationThreadRepository.findByThreadKey(conversationId);
            
            if (threadOpt.isEmpty()) {
                log.debug("Thread not found for conversationId {}, skipping summarization", conversationId);
                return false;
            }
            
            ConversationThread thread = threadOpt.get();
            
            log.info("Triggering summarization for conversationId {} (thread {})",
                conversationId, thread.getThreadKey());
            
            // Получаем сообщения из основной БД для суммаризации
            // Если была предыдущая суммаризация, берем только новые сообщения после неё
            // Если messagesAtLastSummarization == null, значит суммаризации еще не было, берем все сообщения (начиная с 0)
            Integer messagesAtLastSummarization = thread.getMessagesAtLastSummarization();
            int minSequenceNumber = messagesAtLastSummarization != null ? messagesAtLastSummarization : 0;
            
            List<AIBotMessage> messages = messageRepository
                .findByThreadAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(thread, minSequenceNumber);
            messages.removeLast();
            
            log.debug("Getting messages for summarization (sequenceNumber > {}) for thread {}: {} messages",
                minSequenceNumber, thread.getThreadKey(), messages.size());
            
            if (messages.isEmpty()) {
                log.warn("No messages to summarize for conversationId {}", conversationId);
                return false;
            }
            
            // Вызываем синхронную суммаризацию
            summarizationService.summarizeThread(thread, messages);
            
            // Обновляем thread из БД после суммаризации
            thread = conversationThreadRepository.findByThreadKey(conversationId)
                .orElseThrow(() -> new RuntimeException("Thread not found after summarization"));
            
            // Очищаем ChatMemory (временная история Spring AI) - удаляет сообщения из SPRING_AI_CHAT_MEMORY
            delegate.clear(conversationId);
            
            // Добавляем summary как SystemMessage в ChatMemory
            if (thread.getSummary() != null && !thread.getSummary().isEmpty()) {
                String summaryContent = buildSummaryContent(thread);
                SystemMessage summaryMessage = new SystemMessage(summaryContent);
                delegate.add(conversationId, summaryMessage);
                
                log.info("Successfully summarized and updated ChatMemory for conversationId {}: {} chars",
                    conversationId, summaryContent.length());
                return true;
            } else {
                log.warn("Summarization completed but summary is empty for conversationId {}", conversationId);
                return false;
            }
        } catch (Exception e) {
            log.error("Error during summarization for conversationId {}", conversationId, e);
            return false;
        }
    }

    @Override
    public void clear(@NonNull String conversationId) {
        delegate.clear(conversationId);
    }

    /**
     * Строит содержимое SystemMessage из summary и memory bullets
     */
    private String buildSummaryContent(ConversationThread thread) {
        StringBuilder content = new StringBuilder();
        
        content.append("Краткое содержание предыдущей беседы:\n");
        content.append(thread.getSummary());
        
        if (thread.getMemoryBullets() != null && !thread.getMemoryBullets().isEmpty()) {
            content.append("\n\nКлючевые моменты:\n");
            thread.getMemoryBullets().forEach(bullet -> 
                content.append("• ").append(bullet).append("\n")
            );
        }
        
        return content.toString();
    }
}
