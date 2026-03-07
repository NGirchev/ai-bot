package ru.girchev.aibot.rest.dto;

import java.time.OffsetDateTime;

/**
 * DTO для представления сессии чата
 */
public record ChatSessionDto(String sessionId, String name, OffsetDateTime createdAt) {
}

