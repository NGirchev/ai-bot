package ru.girchev.aibot.telegram.command.handler.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Обработчик команды /threads для просмотра списка всех бесед
 */
@Slf4j
public class ThreadsTelegramCommandHandler extends AbstractTelegramCommandHandlerWithResponseSend {
    
    private static final String CALLBACK_PREFIX = "THREADS_";
    private static final String THREAD_PREFIX = "Беседа ";
    
    private final ConversationThreadRepository threadRepository;
    private final ConversationThreadService threadService;
    private final TelegramUserService userService;
    
    public ThreadsTelegramCommandHandler(
            ObjectProvider<TelegramBot> telegramBotProvider,
            TypingIndicatorService typingIndicatorService,
            ConversationThreadRepository threadRepository,
            ConversationThreadService threadService,
            TelegramUserService userService) {
        super(telegramBotProvider, typingIndicatorService);
        this.threadRepository = threadRepository;
        this.threadService = threadService;
        this.userService = userService;
    }
    
    @Override
    public boolean canHandle(ICommand<TelegramCommandType> command) {
        if (!(command instanceof TelegramCommand telegramCommand)) {
            return false;
        }
        var commandType = command.commandType();
        if (commandType == null || commandType.command() == null) {
            return false;
        }
        
        // Обрабатываем обычную команду /threads
        if (commandType.command().equals(TelegramCommand.THREADS) && !telegramCommand.update().hasCallbackQuery()) {
            return true;
        }
        
        // Обрабатываем callback query для выбора беседы
        if (telegramCommand.update().hasCallbackQuery()) {
            CallbackQuery cq = telegramCommand.update().getCallbackQuery();
            return cq.getData() != null && cq.getData().startsWith(CALLBACK_PREFIX);
        }
        
        return false;
    }
    
    @Override
    public String handleInner(TelegramCommand command) throws TelegramCommandHandlerException {
        // Обрабатываем callback query для выбора беседы
        if (command.update().hasCallbackQuery()) {
            handleCallbackQuery(command);
            return null; // Возвращаем null, чтобы базовый класс не отправлял сообщение
        }
        
        // Обрабатываем обычную команду /threads
        Message message = command.update().getMessage();
        if (message == null) {
            throw new TelegramCommandHandlerException(command.telegramId(), "Message is required for threads command");
        }
        
        TelegramUser user = userService.getOrCreateUser(message.getFrom());
        
        // Получаем все threads (активные и неактивные)
        List<ConversationThread> allThreads = threadRepository.findByUserOrderByLastActivityAtDesc(user);
        
        if (allThreads.isEmpty()) {
            return "📝 У вас нет бесед. Начните новую беседу, отправив сообщение.";
        }
        
        // Формируем сообщение со списком threads
        StringBuilder threadsList = new StringBuilder();
        threadsList.append("📋 Выберите активную беседу:\n\n");
        
        // Ограничиваем количество threads для меню (первые 20)
        int threadsToShow = Math.min(allThreads.size(), 20);
        
        for (int i = 0; i < threadsToShow; i++) {
            ConversationThread thread = allThreads.get(i);
            threadsList.append((i + 1)).append(". ");
            
            // Показываем статус активности
            if (Boolean.TRUE.equals(thread.getIsActive())) {
                threadsList.append("✅ ");
            } else {
                threadsList.append("🔒 ");
            }
            
            if (thread.getTitle() != null && !thread.getTitle().isEmpty()) {
                threadsList.append(thread.getTitle());
            } else {
                threadsList.append(THREAD_PREFIX).append(thread.getThreadKey().substring(0, 8));
            }
            
            threadsList.append("\n");
        }
        
        if (allThreads.size() > 20) {
            threadsList.append("\n... и еще ").append(allThreads.size() - 20).append(" бесед.");
        }
        
        // Отправляем сообщение с меню
        sendMessageWithMenu(command.telegramId(), threadsList.toString(), command);
        return null; // Возвращаем null, так как сообщение уже отправлено
    }
    
    private void handleCallbackQuery(TelegramCommand command) throws TelegramCommandHandlerException {
        CallbackQuery cq = command.update().getCallbackQuery();
        String callbackData = cq.getData();
        
        if (callbackData == null || !callbackData.startsWith(CALLBACK_PREFIX)) {
            throw new TelegramCommandHandlerException(command.telegramId(), "Invalid callback data");
        }
        
        // Извлекаем threadKey из callback data
        String threadKey = callbackData.substring(CALLBACK_PREFIX.length());
        
        TelegramUser user = userService.getOrCreateUser(cq.getFrom());
        
        // Находим thread по ключу
        Optional<ConversationThread> threadOpt = threadService.findByThreadKey(threadKey);
        if (threadOpt.isEmpty()) {
            ackCallback(cq.getId(), "❌ Беседа не найдена");
            sendErrorMessage(command.telegramId(), "Беседа не найдена");
            return;
        }
        
        ConversationThread thread = threadOpt.get();
        
        // Проверяем, что thread принадлежит пользователю
        if (!thread.getUser().getId().equals(user.getId())) {
            ackCallback(cq.getId(), "❌ Доступ запрещен");
            sendErrorMessage(command.telegramId(), "Эта беседа не принадлежит вам");
            return;
        }
        
        // Активируем thread
        threadService.activateThread(user, thread);
        
        // Формируем сообщение об успешной активации
        String threadTitle = thread.getTitle() != null && !thread.getTitle().isEmpty() 
            ? thread.getTitle() 
            : THREAD_PREFIX + thread.getThreadKey().substring(0, 8);
        
        String responseMessage = "✅ Активная беседа изменена:\n\n" +
            "📝 " + threadTitle + "\n" +
            "ID: `" + thread.getThreadKey().substring(0, 8) + "...`";
        
        if (thread.getTotalMessages() != null && thread.getTotalMessages() > 0) {
            responseMessage += "\nСообщений: " + thread.getTotalMessages();
        }
        
        ackCallback(cq.getId(), "✅ Беседа активирована");
        sendMessage(command.telegramId(), responseMessage);
    }
    
    private void sendMessageWithMenu(Long chatId, String text, TelegramCommand command) throws TelegramCommandHandlerException {
        try {
            TelegramUser user = userService.getOrCreateUser(command.update().getMessage().getFrom());
            List<ConversationThread> allThreads = threadRepository.findByUserOrderByLastActivityAtDesc(user);
            
            if (allThreads.isEmpty()) {
                sendMessage(chatId, text);
                return;
            }
            
            // Создаем меню с кнопками для каждой беседы
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            // Ограничиваем количество кнопок (первые 20)
            int threadsToShow = Math.min(allThreads.size(), 20);
            
            for (int i = 0; i < threadsToShow; i++) {
                ConversationThread thread = allThreads.get(i);
                
                // Формируем текст кнопки
                String buttonText = (i + 1) + ". ";
                if (Boolean.TRUE.equals(thread.getIsActive())) {
                    buttonText += "✅ ";
                } else {
                    buttonText += "🔒 ";
                }
                
                String threadTitle = thread.getTitle() != null && !thread.getTitle().isEmpty()
                    ? thread.getTitle()
                    : THREAD_PREFIX + thread.getThreadKey().substring(0, 8);
                
                // Ограничиваем длину текста кнопки (Telegram ограничивает до 64 символов)
                if (buttonText.length() + threadTitle.length() > 60) {
                    threadTitle = threadTitle.substring(0, 60 - buttonText.length() - 3) + "...";
                }
                buttonText += threadTitle;
                
                InlineKeyboardButton button = new InlineKeyboardButton(buttonText);
                button.setCallbackData(CALLBACK_PREFIX + thread.getThreadKey());
                
                keyboard.add(List.of(button));
            }
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup(keyboard);
            
            SendMessage msg = new SendMessage(chatId.toString(), text);
            msg.setReplyMarkup(markup);
            msg.setParseMode("Markdown");
            
            telegramBotProvider.getObject().execute(msg);
        } catch (TelegramApiException e) {
            throw new TelegramCommandHandlerException("Ошибка отправки сообщения в Telegram", e);
        }
    }
    
    private void ackCallback(String callbackQueryId, String text) {
        try {
            AnswerCallbackQuery ack = new AnswerCallbackQuery();
            ack.setCallbackQueryId(callbackQueryId);
            ack.setText(text);
            ack.setShowAlert(false);
            telegramBotProvider.getObject().execute(ack);
        } catch (TelegramApiException e) {
            log.error("Error acknowledging callback query", e);
        }
    }
    
    @Override
    public String getSupportedCommandText() {
        return TelegramCommand.THREADS + " - 🗂 список всех бесед";
    }
}
