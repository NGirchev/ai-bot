package ru.girchev.aibot.rest.dto;

import java.util.List;

/**
 * DTO для истории сообщений в сессии
 */
public record ChatHistoryResponseDto(String sessionId, List<ChatMessageDto> messages) {
}

