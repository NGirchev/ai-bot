package ru.girchev.aibot.telegram.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.girchev.aibot.telegram.TelegramBot;
import ru.girchev.aibot.telegram.command.handler.TelegramSupportedCommandProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сервис для установки меню команд Telegram бота
 */
@Slf4j
@RequiredArgsConstructor
public class TelegramBotMenuService {

    private final ObjectProvider<TelegramBot> telegramBotProvider;
    private final ObjectProvider<TelegramSupportedCommandProvider> commandHandlersProvider;

    /**
     * Устанавливает меню команд для бота на основе обработчиков команд
     */
    public void setupBotMenu() {
        try {
            List<BotCommand> commands = buildCommandsList();
            if (commands.isEmpty()) {
                log.warn("No commands found to set up bot menu");
                return;
            }
            
            TelegramBot bot = telegramBotProvider.getObject();
            bot.setMyCommands(commands);
            log.info("Bot menu successfully configured with {} commands", commands.size());
        } catch (TelegramApiException e) {
            log.error("Failed to set up bot menu", e);
            throw new RuntimeException("Failed to set up bot menu", e);
        }
    }

    /**
     * Строит список команд из обработчиков
     * @return список команд для меню
     */
    private List<BotCommand> buildCommandsList() {
        List<BotCommand> commands = new ArrayList<>();
        
        commandHandlersProvider.orderedStream()
                .map(TelegramSupportedCommandProvider::getSupportedCommandText)
                .filter(Objects::nonNull)
                .forEach(commandText -> {
                    BotCommand command = parseCommandText(commandText);
                    if (command != null) {
                        commands.add(command);
                    }
                });
        
        return commands;
    }

    /**
     * Парсит строку вида "/command - описание" в BotCommand
     * @param commandText строка с командой и описанием
     * @return BotCommand или null, если не удалось распарсить
     */
    private BotCommand parseCommandText(String commandText) {
        if (commandText == null || commandText.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = commandText.trim();
        int dashIndex = trimmed.indexOf(" - ");
        
        if (dashIndex == -1) {
            // Если нет описания, используем команду как есть
            String command = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
            return new BotCommand(command, "");
        }
        
        String command = trimmed.substring(0, dashIndex).trim();
        String description = trimmed.substring(dashIndex + 3).trim();
        
        // Убеждаемся, что команда начинается с /
        if (!command.startsWith("/")) {
            command = "/" + command;
        }
        
        // Ограничиваем длину описания (Telegram ограничивает до 256 символов)
        if (description.length() > 256) {
            description = description.substring(0, 253) + "...";
        }
        
        return new BotCommand(command, description);
    }
}

