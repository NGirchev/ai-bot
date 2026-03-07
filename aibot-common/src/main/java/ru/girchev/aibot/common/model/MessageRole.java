package ru.girchev.aibot.common.model;

/**
 * Перечисление ролей сообщений в диалоге.
 * Соответствует Spring AI Message roles.
 */
public enum MessageRole {
    /**
     * Сообщение от пользователя
     */
    USER,
    
    /**
     * Сообщение от ассистента (AI)
     */
    ASSISTANT,
    
    /**
     * Системное сообщение (system prompt, summary и т.д.)
     */
    SYSTEM
}

