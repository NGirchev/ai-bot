package ru.girchev.aibot.telegram.command.handler.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.girchev.aibot.common.command.ICommand;
import ru.girchev.aibot.common.service.BugreportService;
import ru.girchev.aibot.telegram.TelegramBot;
import ru.girchev.aibot.telegram.command.TelegramCommand;
import ru.girchev.aibot.telegram.command.TelegramCommandType;
import ru.girchev.aibot.telegram.command.handler.AbstractTelegramCommandHandler;
import ru.girchev.aibot.telegram.service.TelegramUserService;
import ru.girchev.aibot.telegram.service.TypingIndicatorService;

import java.util.List;

@Slf4j
public class BugreportTelegramCommandHandler extends AbstractTelegramCommandHandler {

    private final TelegramUserService telegramUserService;
    private final BugreportService bugReportService;

    public BugreportTelegramCommandHandler(ObjectProvider<TelegramBot> telegramBotProvider,
                                           TypingIndicatorService typingIndicatorService,
                                           TelegramUserService telegramUserService,
                                           BugreportService bugReportService) {
        super(telegramBotProvider, typingIndicatorService);
        this.telegramUserService = telegramUserService;
        this.bugReportService = bugReportService;
    }

    @Override
    public String getSupportedCommandText() {
        return TelegramCommand.BUGREPORT + " - 🐞 обратная связь и багрепорты";
    }

    @Override
    public boolean canHandle(ICommand<TelegramCommandType> command) {
        if (!(command instanceof TelegramCommand)) {
            return false;
        }
        var commandType = command.commandType();
        return commandType != null
                && commandType.command() != null
                && commandType.command().startsWith(TelegramCommand.BUGREPORT);
    }

    @Override
    public void handleInner(TelegramCommand command) throws TelegramApiException {
        if (command.update().hasCallbackQuery()) {
            CallbackQuery cq = command.update().getCallbackQuery();
            String data = cq.getData();
            var message = cq.getMessage();
            Long chatId = message.getChatId();
            var telegramBot = telegramBotProvider.getObject();

            var userSession = telegramUserService.getOrCreateSession(cq.getFrom());

            telegramBot.showTyping(chatId);
            ackCallback(cq.getId());

            switch (data) {
                case "ERROR" -> telegramBot.execute(new SendMessage(chatId.toString(), "Введите текст ошибки"));
                case "IMPROVEMENT" ->
                        telegramBot.execute(new SendMessage(chatId.toString(), "Введите текст предложения"));
                default -> telegramBot.execute(new SendMessage(chatId.toString(), "неизвестная команда: " + data));
            }
            telegramUserService.updateUserSession(userSession.getTelegramUser(), TelegramCommand.BUGREPORT + "/" + data);
        } else {
            if (command.update().hasMessage()) {
                Message message = command.update().getMessage();
                var userSession = telegramUserService.getOrCreateSession(message.getFrom());

                if (!StringUtils.isBlank(userSession.getBotStatus())) {
                    if (!userSession.getIsActive()) {
                        // TODO find how to handle
                        log.warn("We don't know what to do sessionIsActive[{}] and botStatus[{}]", userSession.getIsActive(), userSession.getBotStatus());
                    }
                    if ((TelegramCommand.BUGREPORT + "/ERROR").equals(userSession.getBotStatus())) {
                        bugReportService.saveBug(userSession.getTelegramUser(), message.getText().strip());
                        telegramBotProvider.getObject().clearStatus(message.getFrom().getId());
                        sendMessage(command.telegramId(), "Сообщение сохранено");
                    } else if ((TelegramCommand.BUGREPORT + "/IMPROVEMENT").equals(userSession.getBotStatus())) {
                        bugReportService.saveImprovementProposal(userSession.getTelegramUser(), message.getText().strip());
                        telegramBotProvider.getObject().clearStatus(message.getFrom().getId());
                        sendMessage(command.telegramId(), "Сообщение сохранено");
                    } else {
                        throw new IllegalArgumentException();
                    }
                } else {
                    telegramUserService.updateUserSession(userSession.getTelegramUser(), TelegramCommand.BUGREPORT);
                    sendMenu(command.telegramId());
                }
            }
        }
    }

    public void sendMenu(Long chatId) throws TelegramApiException {
        InlineKeyboardButton b1 = new InlineKeyboardButton("Сообщить об ошибке");
        b1.setCallbackData("ERROR");

        InlineKeyboardButton b2 = new InlineKeyboardButton("Предложить улучшение");
        b2.setCallbackData("IMPROVEMENT");

        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        kb.setKeyboard(List.of(
                List.of(b1, b2) // один ряд, две кнопки
        ));

        SendMessage msg = new SendMessage(chatId.toString(), "Выбери действие:");
        msg.setReplyMarkup(kb);
        telegramBotProvider.getObject().execute(msg);
    }

    public void ackCallback(String callbackQueryId) throws TelegramApiException {
        AnswerCallbackQuery ack = new AnswerCallbackQuery();
        ack.setCallbackQueryId(callbackQueryId);
        ack.setText("Ок! Обрабатываю...");
        ack.setShowAlert(false);
        telegramBotProvider.getObject().execute(ack);
    }
}
