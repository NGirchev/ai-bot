package ru.girchev.aibot.telegram.command.handler.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.girchev.aibot.common.command.ICommand;
import ru.girchev.aibot.common.model.ConversationThread;
import ru.girchev.aibot.common.repository.ConversationThreadRepository;
import ru.girchev.aibot.common.service.ConversationThreadService;
import ru.girchev.aibot.telegram.TelegramBot;
import ru.girchev.aibot.telegram.command.TelegramCommand;
import ru.girchev.aibot.telegram.command.TelegramCommandType;
import ru.girchev.aibot.telegram.command.handler.AbstractTelegramCommandHandlerWithResponseSend;
import ru.girchev.aibot.telegram.command.handler.TelegramCommandHandlerException;
import ru.girchev.aibot.telegram.model.TelegramUser;
import ru.girchev.aibot.telegram.service.TelegramUserService;
import ru.girchev.aibot.telegram.service.TypingIndicatorService;

import java.util.Optional;

/**
 * Обработчик команды /newthread для создания новой беседы
 */
@Slf4j
public class NewThreadTelegramCommandHandler extends AbstractTelegramCommandHandlerWithResponseSend {
    
    private final ConversationThreadService threadService;
    private final ConversationThreadRepository threadRepository;
    private final TelegramUserService userService;
    
    public NewThreadTelegramCommandHandler(
            ObjectProvider<TelegramBot> telegramBotProvider,
            TypingIndicatorService typingIndicatorService,
            ConversationThreadService threadService,
            ConversationThreadRepository threadRepository,
            TelegramUserService userService) {
        super(telegramBotProvider, typingIndicatorService);
        this.threadService = threadService;
        this.threadRepository = threadRepository;
        this.userService = userService;
    }
    
    @Override
    public boolean canHandle(ICommand<TelegramCommandType> command) {
        if (!(command instanceof TelegramCommand telegramCommand)) {
            return false;
        }
        var commandType = command.commandType();
        return commandType != null
                && commandType.command() != null
                && commandType.command().equals(TelegramCommand.NEWTHREAD)
                && !telegramCommand.update().hasCallbackQuery();
    }
    
    @Override
    public String handleInner(TelegramCommand command) throws TelegramCommandHandlerException {
        Message message = command.update().getMessage();
        if (message == null) {
            throw new TelegramCommandHandlerException(command.telegramId(), "Message is required for newthread command");
        }
        
        TelegramUser user = userService.getOrCreateUser(message.getFrom());
        
        // Закрываем текущий thread (если есть активный)
        Optional<ConversationThread> currentThread = threadRepository.findMostRecentActiveThread(user);
        boolean hadPreviousThread = currentThread.isPresent();
        currentThread.ifPresent(threadService::closeThread);
        
        // Создаем новый
        ConversationThread newThread = threadService.createNewThread(user);
        
        // Формируем сообщение в зависимости от того, была ли предыдущая беседа
        String responseMessage = "✅ Начата новая беседа!\n\n" +
            "ID беседы: `" + newThread.getThreadKey().substring(0, 8) + "...`";
        if (hadPreviousThread) {
            responseMessage += "\n\nИстория предыдущей беседы сохранена.";
        }
        
        return responseMessage;
    }
    
    @Override
    public String getSupportedCommandText() {
        return TelegramCommand.NEWTHREAD + " - 🆕 начать новую беседу";
    }
}
