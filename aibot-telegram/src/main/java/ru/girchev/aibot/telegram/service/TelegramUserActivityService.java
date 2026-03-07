package ru.girchev.aibot.telegram.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import ru.girchev.aibot.telegram.model.TelegramUser;
import ru.girchev.aibot.telegram.model.TelegramUserSession;
import ru.girchev.aibot.telegram.repository.TelegramUserSessionRepository;

import java.time.OffsetDateTime;

@Slf4j
@RequiredArgsConstructor
public class TelegramUserActivityService {

    private final TelegramUserSessionRepository sessionRepository;
    private final TelegramUserSessionService sessionService;

    private static final int INACTIVITY_THRESHOLD_MINUS = 15;
    private static final int BATCH_SIZE = 100;

    @Scheduled(fixedRate = 600000) // Каждый час
    @Transactional
    public void checkUserActivity() {
        log.info("Начинаем проверку активности пользователей");
        
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(INACTIVITY_THRESHOLD_MINUS);
        int page = 0;
        Page<TelegramUserSession> activeSessionsPage;
        
        do {
            activeSessionsPage = sessionRepository.findActiveSessionsBefore(
                threshold, PageRequest.of(page, BATCH_SIZE));
            
            log.info("Обрабатываем страницу {} из {} с {} сессиями", 
                page + 1, activeSessionsPage.getTotalPages(), activeSessionsPage.getNumberOfElements());
            
            activeSessionsPage.getContent().forEach(session -> {
                try {
                    checkUserAndCloseSession(session);
                } catch (Exception e) {
                    log.error("Ошибка при проверке активности пользователя {}: {}", 
                        session.getTelegramUser().getTelegramId(), e.getMessage());
                }
            });
            
            page++;
        } while (activeSessionsPage.hasNext());
        
        log.info("Завершена проверка активности пользователей. Обработано {} страниц, всего {} сессий", 
            page, activeSessionsPage.getTotalElements());
    }

    private void checkUserAndCloseSession(TelegramUserSession session) {
        TelegramUser user = session.getTelegramUser();
        
        // Проверяем время последней активности
        if (user.getLastActivityAt().isBefore(OffsetDateTime.now().minusHours(INACTIVITY_THRESHOLD_MINUS))) {
            log.info("Пользователь {} неактивен более {} часов, закрываем сессию", 
                user.getTelegramId(), INACTIVITY_THRESHOLD_MINUS);
            sessionService.closeSession(session);
        }
    }
} 