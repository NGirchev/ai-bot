package ru.girchev.aibot.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "ai-bot.common")
@Validated
@Getter
@Setter
public class CoreCommonProperties {
    
    @NotBlank(message = "Описание роли ассистента не может быть пустым")
    private String assistantRole = "You are a helpful assistant, who talks with an old person and trying to help with new difficult world. You need to check your answers, because you shouldn't give an bad, wrong advises. Also, you prefer to answer shortly, without extra details if you were not asked about it. Also you are speaking only in Russian language.";
    
    /**
     * Конфигурация для управления контекстом conversation history.
     * Все значения обязательны и должны быть указаны в application.yml.
     */
    @Valid
    @NestedConfigurationProperty
    private ConversationContextProperties conversationContext = new ConversationContextProperties();
    
    /**
     * Конфигурация для инициализации администратора при старте приложения.
     */
    @Valid
    @NestedConfigurationProperty
    private AdminProperties admin = new AdminProperties();
    
    @Getter
    @Setter
    @Validated
    public static class ConversationContextProperties {
        
        /**
         * Включен ли режим ручного управления контекстом разговора.
         * Если true - используется ConversationHistoryAICommandFactory для ручного построения контекста.
         * Если false - используется Spring AI ChatMemory для автоматического управления контекстом.
         */
        @NotNull(message = "enabled обязателен")
        private Boolean enabled;
        
        /**
         * Максимальное количество токенов для контекста (модель 32k → оставляем 24k на ответ)
         */
        @NotNull(message = "maxContextTokens обязателен")
        @Min(value = 1000, message = "maxContextTokens должен быть >= 1000")
        private Integer maxContextTokens;
        
        /**
         * Резерв под ответ модели
         */
        @NotNull(message = "maxResponseTokens обязателен")
        @Min(value = 500, message = "maxResponseTokens должен быть >= 500")
        private Integer maxResponseTokens;
        
        /**
         * Количество последних сообщений для включения в контекст по умолчанию
         */
        @NotNull(message = "defaultWindowSize обязателен")
        @Min(value = 1, message = "defaultWindowSize должен быть >= 1")
        private Integer defaultWindowSize;
        
        /**
         * При 70% заполнения контекста → триггер summarization (0.0-1.0)
         */
        @NotNull(message = "summaryTriggerThreshold обязателен")
        @Min(value = 0, message = "summaryTriggerThreshold должен быть >= 0.0")
        @Max(value = 1, message = "summaryTriggerThreshold должен быть <= 1.0")
        private Double summaryTriggerThreshold;
        
        /**
         * Включать ли system prompt в каждый запрос
         */
        @NotNull(message = "includeSystemPrompt обязателен")
        private Boolean includeSystemPrompt;
        
        /**
         * Грубая оценка: 1 токен ≈ N символов (для русского языка обычно 4)
         */
        @NotNull(message = "tokenEstimationCharsPerToken обязателен")
        @Min(value = 1, message = "tokenEstimationCharsPerToken должен быть >= 1")
        private Integer tokenEstimationCharsPerToken;
    }
    
    /**
     * Свойства для конфигурации администратора
     */
    @Getter
    @Setter
    @Validated
    public static class AdminProperties {
        
        /**
         * Включена ли инициализация администратора
         */
        private Boolean enabled = false;
        
        /**
         * Telegram ID администратора (опционально)
         */
        private Long telegramId;
        
        /**
         * Email REST администратора (опционально)
         */
        private String restEmail;
    }
} 