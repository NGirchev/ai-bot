package ru.girchev.aibot.telegram.command.handler.impl;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
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

public class BackoffCommandHandler extends AbstractTelegramCommandHandlerWithResponseSend {

    private final ObjectProvider<TelegramSupportedCommandProvider> handlersProvider;
    private final TelegramProperties telegramProperties;

    public BackoffCommandHandler(ObjectProvider<TelegramBot> telegramBotProvider,
                                 TypingIndicatorService typingIndicatorService,
                                 ObjectProvider<TelegramSupportedCommandProvider> handlersProvider,
                                 TelegramProperties telegramProperties) {
        super(telegramBotProvider, typingIndicatorService);
        this.handlersProvider = handlersProvider;
        this.telegramProperties = telegramProperties;
    }

    @Override
    public int priority() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public boolean canHandle(ICommand<TelegramCommandType> command) {
        return command instanceof TelegramCommand;
    }

    @Override
    public String handleInner(TelegramCommand command) {
        telegramBotProvider.getObject().clearStatus(command.userId());
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
