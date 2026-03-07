package ru.girchev.aibot.rest.dto;

/**
 * DTO для ответа на запрос чата
 */
public record ChatResponseDto<T>(T message, String sessionId) {
}

