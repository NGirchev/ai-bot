package ru.girchev.aibot.rest.dto;

/**
 * DTO для представления сообщения в чате
 */
public record ChatMessageDto(String role, String content) {
}

