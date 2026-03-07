package ru.girchev.aibot.telegram.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import ru.girchev.aibot.common.config.CoreCommonProperties;
import ru.girchev.aibot.common.model.*;
import ru.girchev.aibot.common.service.AIBotMessageService;
import ru.girchev.aibot.telegram.model.TelegramUser;
import ru.girchev.aibot.telegram.model.TelegramUserSession;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для работы с Telegram сообщениями.
 * Использует базовый Message Entity, сохраняя Telegram-специфичные данные в metadata.
 * Заменяет TelegramUserRequestService.
 */
@Slf4j
@RequiredArgsConstructor
public class TelegramMessageService {
    
    private final AIBotMessageService messageService;
    private final TelegramUserService telegramUserService;
    private final CoreCommonProperties coreCommonProperties;
    
    /**
     * Сохраняет USER сообщение от Telegram пользователя с сессией и conversation thread
     * Автоматически получает или создает активный thread и роль для пользователя
     * 
     * @param assistantRoleContent опциональное содержание роли ассистента (если null, используется дефолтная)
     */
    @Transactional
    public AIBotMessage saveUserMessage(
            TelegramUser telegramUser,
            TelegramUserSession session,
            String content,
            RequestType requestType,
            String assistantRoleContent) {
        
        // Получаем или создаем роль ассистента для пользователя через TelegramUserService
        String roleContent = assistantRoleContent != null 
                ? assistantRoleContent 
                : coreCommonProperties.getAssistantRole();
        AssistantRole assistantRole = telegramUserService.getOrCreateAssistantRole(telegramUser, roleContent);
        
        // Подготавливаем Telegram-специфичные метаданные
        Map<String, Object> metadata = null;
        if (session != null) {
            metadata = new HashMap<>();
            metadata.put("session_id", session.getId());
        }
        
        // Используем базовый MessageService для сохранения сообщения
        return messageService.saveUserMessage(
                telegramUser, 
                content, 
                requestType, 
                assistantRole, 
                metadata);
    }
    
    /**
     * Сохраняет ASSISTANT сообщение (ответ от AI) для Telegram пользователя
     * Автоматически получает или создает активный thread и роль для пользователя
     * 
     * @param assistantRoleContent опциональное содержание роли ассистента (если null, используется дефолтная)
     * @param responseDataMap полезные данные из ответа AI провайдера (usage tokens, finish_reason и т.д.)
     */
    @Transactional
    public AIBotMessage saveAssistantMessage(
            TelegramUser telegramUser,
            String content,
            String serviceName,
            String assistantRoleContent,
            Integer processingTimeMs,
            Map<String, Object> responseDataMap) {
        
        String roleContent = assistantRoleContent != null
                ? assistantRoleContent 
                : coreCommonProperties.getAssistantRole();
        AssistantRole assistantRole = telegramUserService.getOrCreateAssistantRole(telegramUser, roleContent);
        return messageService.saveAssistantMessage(
                telegramUser, 
                content, 
                serviceName, 
                assistantRole, 
                processingTimeMs, 
                responseDataMap);
    }
    
    /**
     * Сохраняет ASSISTANT сообщение (ответ от AI) для Telegram пользователя
     * Перегрузка для обратной совместимости без responseDataMap
     */
    @Transactional
    public AIBotMessage saveAssistantMessage(
            TelegramUser telegramUser,
            String content,
            String serviceName,
            String assistantRoleContent,
            Integer processingTimeMs) {
        return saveAssistantMessage(telegramUser, content, serviceName, assistantRoleContent, processingTimeMs, null);
    }
    
    /**
     * Сохраняет ASSISTANT сообщение с ошибкой для Telegram пользователя
     * Автоматически получает или создает активный thread и роль для пользователя
     * 
     * @param assistantRoleContent опциональное содержание роли ассистента (если null, используется дефолтная)
     */
    @Transactional
    public AIBotMessage saveAssistantErrorMessage(
            TelegramUser telegramUser,
            String errorMessage,
            String serviceName,
            String assistantRoleContent,
            String errorData) {
        
        // Получаем или создаем роль ассистента для пользователя через TelegramUserService
        String roleContent = assistantRoleContent != null 
                ? assistantRoleContent 
                : coreCommonProperties.getAssistantRole();
        AssistantRole assistantRole = telegramUserService.getOrCreateAssistantRole(telegramUser, roleContent);
        
        // Используем базовый MessageService для сохранения сообщения
        return messageService.saveAssistantErrorMessage(
                telegramUser, 
                errorMessage, 
                serviceName, 
                assistantRole, 
                errorData);
    }
}

