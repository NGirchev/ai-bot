package ru.girchev.aibot.telegram.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "ai-bot.telegram")
public class TelegramProperties {
    
    @NotBlank(message = "Токен бота не может быть пустым")
    private String token;
    
    @NotBlank(message = "Имя пользователя бота не может быть пустым")
    private String username;
    
    /**
     * Строка с Telegram ID пользователей через запятую (например: "350001752,123456789")
     * Парсится в Set<Long> при инициализации
     */
    private String whitelistExceptions;
    
    /**
     * Множество Telegram ID пользователей, которые должны быть автоматически добавлены в whitelist при старте приложения
     * Парсится из whitelistExceptions строки
     */
    private Set<Long> whitelistExceptionsSet = new HashSet<>();
    
    /**
     * Строка с ID групп/каналов Telegram через запятую (например: "-1000000000000,@mygroup")
     * Участники этих групп/каналов получают доступ к боту.
     * Если пользователь не в whitelist, но является участником одной из этих групп/каналов,
     * он автоматически добавляется в whitelist.
     * Может быть как числовым ID (например: -1000000000000), так и username (например: @mygroup)
     * Парсится в Set<String> при инициализации
     */
    private String whitelistChannelIdExceptions;
    
    /**
     * Список ID групп/каналов Telegram, участники которых получают доступ к боту.
     * Парсится из whitelistChannelIdExceptions строки
     */
    private Set<String> whitelistChannelIdExceptionsSet = new HashSet<>();
    
    /**
     * Приветственное сообщение, отправляемое при команде /start
     */
    @NotBlank(message = "Приветственное сообщение не может быть пустым")
    private String startMessage;
    
    /**
     * Настройки включения/выключения обработчиков команд
     */
    private Commands commands = new Commands();
    
    @Getter
    @Setter
    public static class Commands {
        /**
         * Включить/выключить обработчик команды /start
         */
        private boolean startEnabled;
        
        /**
         * Включить/выключить обработчик команды /role
         */
        private boolean roleEnabled;
        
        /**
         * Включить/выключить обработчик обычных сообщений
         */
        private boolean messageEnabled;
    }
    
    @PostConstruct
    public void parseWhitelistExceptions() {
        if (whitelistExceptions == null || whitelistExceptions.trim().isEmpty()) {
            whitelistExceptionsSet = new HashSet<>();
            log.info("whitelist-exceptions пуст или null, список исключений будет пустым");
        } else {
            try {
                whitelistExceptionsSet = Arrays.stream(whitelistExceptions.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Long::parseLong)
                        .collect(Collectors.toSet());
                log.info("Распарсен whitelist-exceptions: '{}' -> {}", whitelistExceptions, whitelistExceptionsSet);
            } catch (NumberFormatException e) {
                log.warn("Ошибка парсинга whitelist-exceptions '{}': {}. Список исключений будет пустым", 
                        whitelistExceptions, e.getMessage());
                whitelistExceptionsSet = new HashSet<>();
            }
        }
        
        if (whitelistChannelIdExceptions == null || whitelistChannelIdExceptions.trim().isEmpty()) {
            whitelistChannelIdExceptionsSet = new HashSet<>();
            log.info("whitelist-channel-id-exceptions пуст или null, список каналов будет пустым");
        } else {
            whitelistChannelIdExceptionsSet = Arrays.stream(whitelistChannelIdExceptions.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            log.info("Распарсен whitelist-channel-id-exceptions: '{}' -> {}", 
                    whitelistChannelIdExceptions, whitelistChannelIdExceptionsSet);
        }
    }
} 