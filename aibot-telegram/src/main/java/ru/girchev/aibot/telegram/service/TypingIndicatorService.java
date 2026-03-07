package ru.girchev.aibot.telegram.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.girchev.aibot.telegram.TelegramBot;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для управления индикатором печати в Telegram.
 * Периодически отправляет индикатор печати, пока идет обработка команды пользователя.
 */
@Slf4j
@RequiredArgsConstructor
public class TypingIndicatorService {

    private final ObjectProvider<TelegramBot> telegramBotProvider;
    private final ScheduledExecutorService scheduledExecutorService;

    /**
     * Интервал отправки индикатора печати в секундах.
     * Telegram API требует обновлять индикатор каждые 4-5 секунд.
     */
    private static final long TYPING_INDICATOR_INTERVAL_SECONDS = 2;

    /**
     * Хранилище активных задач отправки индикатора для каждого пользователя.
     */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> activeTypingIndicators = new ConcurrentHashMap<>();

    /**
     * Запускает периодическую отправку индикатора печати для пользователя.
     * Если для пользователя уже есть активный индикатор, он будет остановлен и заменен новым.
     *
     * @param userId идентификатор пользователя (chatId)
     */
    public void startTyping(Long userId) {
        // Останавливаем предыдущий индикатор, если он есть
        stopTyping(userId);

        // Отправляем индикатор сразу
        sendTypingIndicator(userId);

        // Запускаем периодическую отправку
        ScheduledFuture<?> future = scheduledExecutorService.scheduleAtFixedRate(
                () -> sendTypingIndicator(userId),
                TYPING_INDICATOR_INTERVAL_SECONDS,
                TYPING_INDICATOR_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        activeTypingIndicators.put(userId, future);
        log.debug("Started typing indicator for user {}", userId);
    }

    /**
     * Останавливает периодическую отправку индикатора печати для пользователя.
     *
     * @param userId идентификатор пользователя (chatId)
     */
    public void stopTyping(Long userId) {
        ScheduledFuture<?> future = activeTypingIndicators.remove(userId);
        if (future != null) {
            future.cancel(false);
            log.debug("Stopped typing indicator for user {}", userId);
        }
    }

    /**
     * Отправляет индикатор печати пользователю.
     *
     * @param userId идентификатор пользователя (chatId)
     */
    private void sendTypingIndicator(Long userId) {
        try {
            telegramBotProvider.getObject().showTyping(userId);
        } catch (TelegramApiException e) {
            log.warn("Failed to send typing indicator for user {}: {}", userId, e.getMessage());
            // Останавливаем индикатор при ошибке
            stopTyping(userId);
        } catch (Exception e) {
            log.error("Unexpected error while sending typing indicator for user {}", userId, e);
            stopTyping(userId);
        }
    }
}

