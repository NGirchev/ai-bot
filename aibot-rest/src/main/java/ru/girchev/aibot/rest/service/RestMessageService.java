package ru.girchev.aibot.rest.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import ru.girchev.aibot.common.config.CoreCommonProperties;
import ru.girchev.aibot.common.model.*;
import ru.girchev.aibot.common.service.AIBotMessageService;
import ru.girchev.aibot.rest.model.RestUser;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для работы с REST сообщениями.
 * Создает Message Entity для REST API запросов.
 * Заменяет RestApiUserRequestService.
 */
@RequiredArgsConstructor
public class RestMessageService {

    private final AIBotMessageService messageService;
    private final RestUserService restUserService;
    private final CoreCommonProperties coreCommonProperties;
    
    /**
     * Сохраняет USER сообщение от REST пользователя с conversation thread
     * Автоматически получает или создает активный thread и роль для пользователя
     * 
     * @param assistantRoleContent опциональное содержание роли ассистента (если null, используется дефолтная)
     */
    @Transactional
    public AIBotMessage saveUserMessage(
            RestUser user,
            String content,
            RequestType requestType,
            String assistantRoleContent,
            HttpServletRequest request) {
        
        // Получаем или создаем роль ассистента для пользователя через RestUserService
        String roleContent = assistantRoleContent != null 
                ? assistantRoleContent 
                : coreCommonProperties.getAssistantRole();
        AssistantRole assistantRole = restUserService.getOrCreateAssistantRole(user, roleContent);
        
        // Подготавливаем REST-специфичные метаданные
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("client_ip", getClientIp(request));
        metadata.put("user_agent", request.getHeader("User-Agent"));
        metadata.put("endpoint", request.getRequestURI());
        
        // Используем базовый MessageService для сохранения сообщения
        return messageService.saveUserMessage(
                user, 
                content, 
                requestType, 
                assistantRole, 
                metadata);
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Получает client_ip из metadata сообщения (для REST)
     */
    public String getClientIpFromMetadata(AIBotMessage message) {
        if (message.getMetadata() == null) {
            return null;
        }
        Object clientIp = message.getMetadata().get("client_ip");
        return clientIp != null ? clientIp.toString() : null;
    }

    /**
     * Получает user_agent из metadata сообщения (для REST)
     */
    public String getUserAgentFromMetadata(AIBotMessage message) {
        if (message.getMetadata() == null) {
            return null;
        }
        Object userAgent = message.getMetadata().get("user_agent");
        return userAgent != null ? userAgent.toString() : null;
    }

    /**
     * Получает endpoint из metadata сообщения (для REST)
     */
    public String getEndpointFromMetadata(AIBotMessage message) {
        if (message.getMetadata() == null) {
            return null;
        }
        Object endpoint = message.getMetadata().get("endpoint");
        return endpoint != null ? endpoint.toString() : null;
    }
}

