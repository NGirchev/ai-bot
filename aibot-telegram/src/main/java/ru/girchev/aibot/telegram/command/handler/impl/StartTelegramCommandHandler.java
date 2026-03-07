package ru.girchev.aibot.telegram.command.handler.impl;

import org.springframework.beans.factory.ObjectProvider;
import ru.girchev.aibot.common.command.ICommand;
import ru.girchev.aibot.telegram.TelegramBot;
import ru.girchev.aibot.telegram.command.TelegramCommand;
import ru.girchev.aibot.telegram.command.TelegramCommandType;
import ru.girchev.aibot.telegram.command.handler.AbstractTelegramCommandHandlerWithResponseSend;
import ru.girchev.aibot.telegram.command.handler.TelegramSupportedCommandProvider;
import ru.girchev.aibot.telegram.config.TelegramProperties;
import ru.girchev.aibot.telegram.service.TypingIndicatorService;

import java.util.Objects;
import java.util.stream.Collectors;

public class StartTelegramCommandHandler extends AbstractTelegramCommandHandlerWithResponseSend {

    private final ObjectProvider<TelegramSupportedCommandProvider> handlersProvider;
    private final TelegramProperties telegramProperties;

    public StartTelegramCommandHandler(ObjectProvider<TelegramBot> telegramBotProvider,
                                       TypingIndicatorService typingIndicatorService,
                                       ObjectProvider<TelegramSupportedCommandProvider> handlersProvider,
                                       TelegramProperties telegramProperties) {
        super(telegramBotProvider, typingIndicatorService);
        this.handlersProvider = handlersProvider;
        this.telegramProperties = telegramProperties;
    }

    @Override
    public boolean canHandle(ICommand<TelegramCommandType> command) {
        if (!(command instanceof TelegramCommand telegramCommand)) {
            return false;
        }
        var commandType = command.commandType();
        return commandType != null
                && commandType.command() != null
                && commandType.command().equals(TelegramCommand.START)
                && !telegramCommand.update().hasCallbackQuery();
    }

    @Override
    public String handleInner(TelegramCommand command) {
        return telegramProperties.getStartMessage()
                + handlersProvider.orderedStream()
                .filter(h -> h != this)
                .map(TelegramSupportedCommandProvider::getSupportedCommandText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    @Override
    public String getSupportedCommandText() {
        return null;
    }
}
