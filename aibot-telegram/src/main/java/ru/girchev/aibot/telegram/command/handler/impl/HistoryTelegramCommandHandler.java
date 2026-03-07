package ru.girchev.aibot.telegram.command.handler.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import ru.girchev.aibot.common.command.ICommand;
import ru.girchev.aibot.common.model.ConversationThread;
import ru.girchev.aibot.common.model.AIBotMessage;
import ru.girchev.aibot.common.model.MessageRole;
import ru.girchev.aibot.common.repository.ConversationThreadRepository;
import ru.girchev.aibot.common.repository.AIBotMessageRepository;
import ru.girchev.aibot.telegram.TelegramBot;
import ru.girchev.aibot.telegram.command.TelegramCommand;
import ru.girchev.aibot.telegram.command.TelegramCommandType;
import ru.girchev.aibot.telegram.command.handler.AbstractTelegramCommandHandlerWithResponseSend;
import ru.girchev.aibot.telegram.command.handler.TelegramCommandHandlerException;
import ru.girchev.aibot.telegram.model.TelegramUser;
import ru.girchev.aibot.telegram.service.TelegramUserService;
import ru.girchev.aibot.telegram.service.TypingIndicatorService;

import java.util.List;
import java.util.Optional;

/**
 * Обработчик команды /history для просмотра истории диалога
 */
@Slf4j
public class HistoryTelegramCommandHandler extends AbstractTelegramCommandHandlerWithResponseSend {
    
    private final ConversationThreadRepository threadRepository;
    private final AIBotMessageRepository messageRepository;
    private final TelegramUserService userService;
    
    public HistoryTelegramCommandHandler(
            ObjectProvider<TelegramBot> telegramBotProvider,
            TypingIndicatorService typingIndicatorService,
            ConversationThreadRepository threadRepository,
            AIBotMessageRepository messageRepository,
            TelegramUserService userService) {
        super(telegramBotProvider, typingIndicatorService);
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
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
                && commandType.command().equals(TelegramCommand.HISTORY)
                && !telegramCommand.update().hasCallbackQuery();
    }
    
    @Override
    public String handleInner(TelegramCommand command) throws TelegramCommandHandlerException {
        org.telegram.telegrambots.meta.api.objects.Message message = command.update().getMessage();
        if (message == null) {
            throw new TelegramCommandHandlerException(command.telegramId(), "Message is required for history command");
        }
        
        TelegramUser user = userService.getOrCreateUser(message.getFrom());
        
        // Получаем активный thread
        Optional<ConversationThread> threadOpt = threadRepository.findMostRecentActiveThread(user);
        if (threadOpt.isEmpty()) {
            return "❌ У вас нет активной беседы. Начните новую беседу, отправив сообщение.";
        }
        
        ConversationThread thread = threadOpt.get();
        
        // Загружаем историю сообщений
        List<AIBotMessage> messages = messageRepository.findByThreadOrderBySequenceNumberAsc(thread);
        
        if (messages.isEmpty()) {
            return "📝 История беседы пуста.\n\nID беседы: `" + thread.getThreadKey().substring(0, 8) + "...`";
        }
        
        // Формируем сообщение с историей
        StringBuilder history = new StringBuilder();
        history.append("📜 История беседы\n\n");
        history.append("ID беседы: `").append(thread.getThreadKey().substring(0, 8)).append("...`\n");
        history.append("Всего сообщений: ").append(messages.size()).append("\n\n");
        
        int messageCount = 0;
        AIBotMessage lastUserMessage = null;
        for (AIBotMessage msg : messages) {
            if (msg.getRole() == MessageRole.USER) {
                messageCount++;
                lastUserMessage = msg;
                history.append("💬 ").append(messageCount).append(". ").append(msg.getContent()).append("\n");
            } else if (msg.getRole() == MessageRole.ASSISTANT && lastUserMessage != null) {
                String responseText = msg.getContent();
                // Ограничиваем длину ответа для читаемости
                if (responseText != null && responseText.length() > 200) {
                    responseText = responseText.substring(0, 197) + "...";
                }
                history.append("🤖 ").append(responseText != null ? responseText : "⏳ Ожидание ответа...").append("\n\n");
                lastUserMessage = null;
            }
            
            // Ограничиваем количество сообщений для читаемости (последние 10 turns = 20 сообщений)
            if (messageCount >= 10 && messages.size() > 20) {
                int remaining = (messages.size() - messageCount * 2) / 2;
                history.append("... и еще ").append(remaining).append(" сообщений.\n");
                history.append("Используйте /newthread для начала новой беседы.");
                break;
            }
        }
        
        // Если осталось необработанное USER сообщение
        if (lastUserMessage != null) {
            history.append("⏳ Ожидание ответа...\n\n");
        }
        
        return history.toString();
    }
    
    @Override
    public String getSupportedCommandText() {
        return TelegramCommand.HISTORY + " - 📜 история текущей беседы";
    }
}
