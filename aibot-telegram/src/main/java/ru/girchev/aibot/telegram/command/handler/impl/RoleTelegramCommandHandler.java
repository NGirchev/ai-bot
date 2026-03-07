package ru.girchev.aibot.telegram.command.handler.impl;

import org.springframework.beans.factory.ObjectProvider;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.girchev.aibot.common.command.ICommand;
import ru.girchev.aibot.common.model.AssistantRole;
import ru.girchev.aibot.telegram.TelegramBot;
import ru.girchev.aibot.telegram.command.TelegramCommand;
import ru.girchev.aibot.telegram.command.TelegramCommandType;
import ru.girchev.aibot.telegram.command.handler.AbstractTelegramCommandHandlerWithResponseSend;
import ru.girchev.aibot.telegram.command.handler.TelegramCommandHandlerException;
import ru.girchev.aibot.telegram.model.TelegramUser;
import ru.girchev.aibot.telegram.service.TelegramUserService;
import ru.girchev.aibot.telegram.service.TypingIndicatorService;
import ru.girchev.aibot.common.config.CoreCommonProperties;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RoleTelegramCommandHandler extends AbstractTelegramCommandHandlerWithResponseSend {

    private static final String CALLBACK_PREFIX = "ROLE_";
    private static final String CALLBACK_CUSTOM = CALLBACK_PREFIX + "CUSTOM";

    private final TelegramUserService telegramUserService;
    private final CoreCommonProperties coreCommonProperties;

    public RoleTelegramCommandHandler(ObjectProvider<TelegramBot> telegramBotProvider,
                                      TypingIndicatorService typingIndicatorService,
                                      TelegramUserService telegramUserService,
                                      CoreCommonProperties coreCommonProperties) {
        super(telegramBotProvider, typingIndicatorService);
        this.telegramUserService = telegramUserService;
        this.coreCommonProperties = coreCommonProperties;
    }

    @Override
    public String getSupportedCommandText() {
        return TelegramCommand.ROLE + " - 🎭 установить роль ассистента";
    }

    @Override
    public boolean canHandle(ICommand<TelegramCommandType> command) {
        if (!(command instanceof TelegramCommand telegramCommand)) {
            return false;
        }
        if (telegramCommand.update().hasCallbackQuery()) {
            CallbackQuery cq = telegramCommand.update().getCallbackQuery();
            return cq.getData() != null && cq.getData().startsWith(CALLBACK_PREFIX);
        }
        var commandType = command.commandType();
        return commandType != null
                && commandType.command() != null
                && commandType.command().equals(TelegramCommand.ROLE);
    }

    @Override
    public String handleInner(TelegramCommand command) {
        if (command.update().hasCallbackQuery()) {
            handleCallbackQuery(command);
            return null;
        }
        Message message = command.update().getMessage();
        if (message == null) {
            throw new TelegramCommandHandlerException(command.telegramId(), "Message is required for role command");
        }
        TelegramUser user = telegramUserService.getOrCreateUser(message.getFrom());
        String userText = command.userText() != null ? command.userText().trim() : null;
        
        if (userText == null || userText.isEmpty()) {
            // Показываем текущую роль
            AssistantRole currentRole = telegramUserService.getOrCreateAssistantRole(
                    user, 
                    coreCommonProperties.getAssistantRole()
            );
            
            // Извлекаем данные из роли внутри транзакции, чтобы избежать LazyInitializationException
            Integer roleVersion = currentRole.getVersion();
            String roleContent = currentRole.getContent();
            
            // Отправляем первое сообщение с заголовком
            String roleHeader = String.format(
                    "📋 Текущая роль ассистента (версия %d):", 
                    roleVersion
            );
            sendMessage(command.telegramId(), roleHeader);
            
            // Отправляем второе сообщение с содержимым роли
            sendMessage(command.telegramId(), roleContent);
            
            // Отправляем третье сообщение с меню выбора роли
            sendRoleMenu(command.telegramId());
            
            // Возвращаем null, так как сообщения уже отправлены
            return null;
        } else {
            // Обновляем роль
            telegramUserService.updateAssistantRole(message.getFrom(), userText);
            telegramBotProvider.getObject().clearStatus(message.getFrom().getId());
            
            // Отправляем подтверждение с ответом на исходное сообщение пользователя
            Integer replyToMessageId = message != null ? message.getMessageId() : null;
            sendMessage(command.telegramId(), "✅ Роль ассистента успешно обновлена!", replyToMessageId);
            return null;
        }
    }

    private void handleCallbackQuery(TelegramCommand command) {
        CallbackQuery cq = command.update().getCallbackQuery();
        String callbackData = cq.getData();
        if (callbackData == null || !callbackData.startsWith(CALLBACK_PREFIX)) {
            throw new TelegramCommandHandlerException(command.telegramId(), "Invalid callback data");
        }

        String roleKey = callbackData.substring(CALLBACK_PREFIX.length());
        if ("CUSTOM".equals(roleKey)) {
            TelegramUser user = telegramUserService.getOrCreateUser(cq.getFrom());
            telegramUserService.updateUserSession(user, TelegramCommand.ROLE);
            ackCallback(cq.getId(), "✏️ Введите новую роль");
            sendMessage(command.telegramId(), "✏️ Введите новую роль текстом.");
            return;
        }

        Optional<RolePreset> preset = getRolePresets().stream()
                .filter(role -> role.key().equals(roleKey))
                .findFirst();
        if (preset.isEmpty()) {
            ackCallback(cq.getId(), "❌ Роль не найдена");
            sendErrorMessage(command.telegramId(), "Неизвестная роль");
            return;
        }

        // Обновляем роль (TelegramUserService сам добавит требование учитывать локаль)
        telegramUserService.updateAssistantRole(cq.getFrom(), preset.get().content());
        telegramBotProvider.getObject().clearStatus(cq.getFrom().getId());
        ackCallback(cq.getId(), "✅ Роль обновлена");
        sendMessage(command.telegramId(), "✅ Роль изменена: " + preset.get().title());
    }

    private void sendRoleMenu(Long chatId) {
        try {
            List<List<InlineKeyboardButton>> keyboard = getRolePresets().stream()
                    .map(role -> {
                        InlineKeyboardButton button = new InlineKeyboardButton(role.title());
                        button.setCallbackData(CALLBACK_PREFIX + role.key());
                        return List.of(button);
                    })
                    .toList();

            InlineKeyboardButton customButton = new InlineKeyboardButton("✏️ Написать свою роль");
            customButton.setCallbackData(CALLBACK_CUSTOM);

            keyboard = new java.util.ArrayList<>(keyboard);
            keyboard.add(List.of(customButton));

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup(keyboard);
            SendMessage msg = new SendMessage(chatId.toString(), "🎭 Выберите роль из списка или задайте свою:");
            msg.setReplyMarkup(markup);
            telegramBotProvider.getObject().execute(msg);
        } catch (Exception e) {
            throw new TelegramCommandHandlerException("Ошибка отправки меню ролей", e);
        }
    }

    private void ackCallback(String callbackQueryId, String text) {
        try {
            AnswerCallbackQuery ack = new AnswerCallbackQuery();
            ack.setCallbackQueryId(callbackQueryId);
            ack.setText(text);
            ack.setShowAlert(false);
            telegramBotProvider.getObject().execute(ack);
        } catch (Exception e) {
            throw new TelegramCommandHandlerException("Ошибка подтверждения callback", e);
        }
    }

    private List<RolePreset> getRolePresets() {
        return List.of(
                new RolePreset("DEFAULT", "🌟 Стандартная", coreCommonProperties.getAssistantRole()),
                new RolePreset("COACH", "🧭 Коуч", "Ты - коуч по развитию и целям. Помогаешь уточнить запрос, "
                        + "задаешь вопросы, предлагаешь шаги и поддерживаешь мотивацию. Ответы краткие и структурированные."),
                new RolePreset("EDITOR", "✍️ Редактор", "Ты - редактор русского текста. Исправляешь ошибки, "
                        + "улучшаешь стиль, предлагаешь более удачные формулировки, сохраняя смысл."),
                new RolePreset("DEV", "💻 Разработчик", "Ты - старший Java-разработчик и архитект. "
                        + "Предлагаешь решения, код и пояснения, учитывая Spring Boot, чистую архитектуру и best practices.")
        );
    }

    private record RolePreset(String key, String title, String content) {
        private RolePreset {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(content, "content");
        }
    }
}
