package ru.girchev.aibot.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import ru.girchev.aibot.common.model.ConversationThread;
import ru.girchev.aibot.common.model.AIBotMessage;
import ru.girchev.aibot.common.model.User;
import ru.girchev.aibot.common.repository.ConversationThreadRepository;
import ru.girchev.aibot.common.repository.AIBotMessageRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис для управления conversation threads (беседами с AI).
 * Базовый сервис в aibot-common, используется напрямую в handlers.
 * 
 * Бин создается в CoreAutoConfig (не используется @Service для явного контроля).
 */
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ConversationThreadService {
    
    private final ConversationThreadRepository threadRepository;
    private final AIBotMessageRepository messageRepository;
    
    private static final Duration THREAD_INACTIVITY_TIMEOUT = Duration.ofHours(24);
    
    /**
     * Получает или создает активный thread для пользователя
     */
    public ConversationThread getOrCreateThread(User user) {
        return threadRepository.findMostRecentActiveThread(user)
            .filter(this::isThreadStillActive)
            .orElseGet(() -> createNewThread(user));
    }
    
    /**
     * Создает новый thread
     */
    public ConversationThread createNewThread(User user) {
        ConversationThread thread = new ConversationThread();
        thread.setUser(user);
        thread.setThreadKey(UUID.randomUUID().toString());
        thread.setIsActive(true);
        thread.setLastActivityAt(OffsetDateTime.now());
        thread.setTotalMessages(0);
        thread.setTotalTokens(0L);
        thread.setMemoryBullets(new ArrayList<>());
        
        log.info("Created new conversation thread {} for user {}", thread.getThreadKey(), user.getId());
        return threadRepository.save(thread);
    }
    
    /**
     * Устанавливает title для thread на основе первого сообщения (если title еще не установлен)
     */
    public void updateThreadTitleIfNeeded(ConversationThread thread, String firstUserMessage) {
        if (thread.getTitle() == null || thread.getTitle().isEmpty()) {
            // Берем первые 50 символов первого сообщения как title
            String title = firstUserMessage.length() > 50 
                ? firstUserMessage.substring(0, 47) + "..." 
                : firstUserMessage;
            thread.setTitle(title);
            threadRepository.save(thread);
            log.debug("Set title for thread {}: {}", thread.getThreadKey(), title);
        }
    }
    
    /**
     * Обновляет счетчики thread на основе всех его сообщений
     * Вызывается после сохранения Message
     */
    public void updateThreadCounters(ConversationThread thread) {
        // Подсчитываем общее количество сообщений и токенов из всех Message
        Integer messageCount = messageRepository.countByThread(thread);
        int totalMessages = messageCount != null ? messageCount : 0;
        
        // Подсчитываем токены из всех Message
        List<AIBotMessage> messages = messageRepository
            .findByThreadOrderBySequenceNumberAsc(thread);
        long totalTokens = messages.stream()
            .mapToLong(m -> m.getTokenCount() != null ? m.getTokenCount() : 0)
            .sum();
        
        thread.setTotalMessages(totalMessages);
        thread.setTotalTokens(totalTokens);
        thread.setLastActivityAt(OffsetDateTime.now());
        threadRepository.save(thread);
        
        log.debug("Updated thread {} counters: {} messages, {} tokens", 
            thread.getThreadKey(), totalMessages, totalTokens);
    }
    
    /**
     * Закрывает thread (помечает как неактивный)
     */
    public void closeThread(ConversationThread thread) {
        thread.setIsActive(false);
        thread.setClosedAt(OffsetDateTime.now());
        threadRepository.save(thread);
        log.info("Closed conversation thread {}", thread.getThreadKey());
    }
    
    /**
     * Проверяет, активен ли thread (по времени последней активности)
     */
    private boolean isThreadStillActive(ConversationThread thread) {
        if (!thread.getIsActive()) {
            return false;
        }
        
        OffsetDateTime lastActivity = thread.getLastActivityAt();
        if (lastActivity == null) {
            return true; // Новый thread
        }
        
        Duration inactivity = Duration.between(lastActivity, OffsetDateTime.now());
        return inactivity.compareTo(THREAD_INACTIVITY_TIMEOUT) < 0;
    }
    
    /**
     * Обновляет summary и memory bullets для thread.
     * Также сохраняет текущее количество сообщений для отслеживания новых сообщений после суммаризации.
     */
    public void updateThreadSummary(ConversationThread thread, String summary, List<String> memoryBullets) {
        thread.setSummary(summary);
        thread.setMemoryBullets(memoryBullets != null ? memoryBullets : new ArrayList<>());
        // Сохраняем текущее количество сообщений на момент суммаризации
        thread.setMessagesAtLastSummarization(thread.getTotalMessages());
        threadRepository.save(thread);
        log.info("Updated summary for thread {} (messages at summarization: {})", 
            thread.getThreadKey(), thread.getMessagesAtLastSummarization());
    }
    
    /**
     * Находит thread по ключу
     */
    public Optional<ConversationThread> findByThreadKey(String threadKey) {
        return threadRepository.findByThreadKey(threadKey);
    }
    
    /**
     * Активирует thread для пользователя (закрывает текущий активный и активирует выбранный)
     */
    public ConversationThread activateThread(User user, ConversationThread threadToActivate) {
        // Закрываем текущий активный thread (если есть)
        threadRepository.findMostRecentActiveThread(user)
            .ifPresent(currentThread -> {
                if (!currentThread.getId().equals(threadToActivate.getId())) {
                    closeThread(currentThread);
                }
            });
        
        // Активируем выбранный thread
        threadToActivate.setIsActive(true);
        threadToActivate.setLastActivityAt(OffsetDateTime.now());
        threadToActivate.setClosedAt(null); // Очищаем дату закрытия, если была
        threadRepository.save(threadToActivate);
        
        log.info("Activated conversation thread {} for user {}", threadToActivate.getThreadKey(), user.getId());
        return threadToActivate;
    }
}

