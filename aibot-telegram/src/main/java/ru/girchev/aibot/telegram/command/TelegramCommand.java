package ru.girchev.aibot.telegram.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.girchev.aibot.common.command.IChatCommand;

@Data
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public class TelegramCommand implements IChatCommand<TelegramCommandType> {

    // Константы команд
    public static final String START = "/start";
    public static final String ROLE = "/role";
    public static final String MESSAGE = "/message";
    public static final String BUGREPORT = "/bugreport";
    public static final String NEWTHREAD = "/newthread";
    public static final String HISTORY = "/history";
    public static final String THREADS = "/threads";

    private Long userId;
    private Long telegramId;
    private TelegramCommandType commandType; // может быть null
    private Update update; // original message
    private String userText; // может быть null для callback query
    private boolean stream;

    public TelegramCommand(Long userId, Long chatId, TelegramCommandType telegramCommandType, Update update) {
        this.userId = userId;
        this.telegramId = chatId;
        this.commandType = telegramCommandType;
        this.update = update;
        this.stream = false;
    }

    public TelegramCommand(Long userId, Long chatId, TelegramCommandType telegramCommandType, Update update, boolean stream) {
        this.userId = userId;
        this.telegramId = chatId;
        this.commandType = telegramCommandType;
        this.update = update;
        this.stream = stream;
    }

    public TelegramCommand(Long userId, Long chatId, TelegramCommandType telegramCommandType, Update update, String userText) {
        this.userId = userId;
        this.telegramId = chatId;
        this.commandType = telegramCommandType;
        this.update = update;
        this.userText = userText;
    }
}
