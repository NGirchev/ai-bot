package io.github.ngirchev.aibot.telegram;

import io.github.ngirchev.aibot.common.service.CommandSyncService;
import io.github.ngirchev.aibot.telegram.config.TelegramProperties;
import io.github.ngirchev.aibot.telegram.model.TelegramUser;
import io.github.ngirchev.aibot.telegram.model.TelegramUserSession;
import io.github.ngirchev.aibot.telegram.model.TelegramUserSession;
import io.github.ngirchev.aibot.telegram.service.TelegramUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TelegramBot.
 * Covers getters, clearWebhook, onUpdateReceived, and mapTo* methods with mocks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramBotTest {

    @Mock
    private CommandSyncService commandSyncService;
    @Mock
    private TelegramUserService userService;

    private TelegramProperties config;
    private TelegramBot bot;

    @BeforeEach
    void setUp() {
        config = new TelegramProperties();
        config.setToken("test-token");
        config.setUsername("test-bot");
        config.setMaxMessageLength(4096);
        config.parseWhitelistExceptions();
        bot = new TelegramBot(config, commandSyncService, userService);
    }

    @Test
    void getBotToken_returnsConfigToken() {
        assertEquals("test-token", bot.getBotToken());
    }

    @Test
    void getBotUsername_returnsConfigUsername() {
        assertEquals("test-bot", bot.getBotUsername());
    }

    @Test
    void clearWebhook_doesNotThrow() {
        assertDoesNotThrow(() -> bot.clearWebhook());
    }

    @Test
    void onUpdateReceived_whenUnsupportedUpdate_doesNotCallSyncAndHandle() {
        Update update = new Update();
        Message message = new Message();
        message.setMessageId(1);
        update.setMessage(message);
        // No text, no photo, no document -> mapUpdateToCommand returns null

        bot.onUpdateReceived(update);

        verify(commandSyncService, never()).syncAndHandle(any());
    }

    @Test
    void onUpdateReceived_whenMessageWithText_callsSyncAndHandle() {
        Update update = new Update();
        Message message = new Message();
        message.setMessageId(1);
        Chat chat = new Chat();
        chat.setId(100L);
        message.setChat(chat);
        User from = new User(200L, "testUser", false);
        message.setFrom(from);
        message.setText("/start");
        update.setMessage(message);

        TelegramUser telegramUser = new TelegramUser();
        telegramUser.setId(1L);
        telegramUser.setTelegramId(200L);
        telegramUser.setLanguageCode("en");
        when(userService.getOrCreateUser(any(User.class))).thenReturn(telegramUser);
        when(userService.getOrCreateSession(any(User.class))).thenReturn(new TelegramUserSession());

        bot.onUpdateReceived(update);

        verify(commandSyncService).syncAndHandle(any());
    }

    @Test
    void mapToTelegramTextCommand_returnsCommandWithUserText() {
        Update update = new Update();
        Message message = new Message();
        message.setMessageId(1);
        Chat chat = new Chat();
        chat.setId(100L);
        message.setChat(chat);
        User from = new User(200L, "u", false);
        message.setFrom(from);
        message.setText("Hello");
        update.setMessage(message);

        TelegramUser telegramUser = new TelegramUser();
        telegramUser.setId(1L);
        telegramUser.setTelegramId(200L);
        telegramUser.setLanguageCode("en");
        when(userService.getOrCreateUser(any(User.class))).thenReturn(telegramUser);
        TelegramUserSession session = new TelegramUserSession();
        session.setBotStatus(null);
        when(userService.getOrCreateSession(any(User.class))).thenReturn(session);

        var command = bot.mapToTelegramTextCommand(update);

        assertNotNull(command);
        assertEquals(1L, command.userId());
        assertEquals(100L, command.telegramId()); // telegramId in command is chatId
        assertEquals("Hello", command.userText());
        assertEquals("en", command.languageCode());
    }

    @Test
    void mapToTelegramCommand_whenCallbackQuery_returnsCommand() {
        Update update = new Update();
        org.telegram.telegrambots.meta.api.objects.CallbackQuery cq = new org.telegram.telegrambots.meta.api.objects.CallbackQuery();
        cq.setId("cq1");
        cq.setData("ROLE_DEFAULT");
        User from = new User(200L, "u", false);
        cq.setFrom(from);
        Message msg = new Message();
        msg.setMessageId(1);
        Chat chat = new Chat();
        chat.setId(100L);
        msg.setChat(chat);
        cq.setMessage(msg);
        update.setCallbackQuery(cq);

        TelegramUser telegramUser = new TelegramUser();
        telegramUser.setId(1L);
        telegramUser.setTelegramId(200L);
        telegramUser.setLanguageCode("en");
        when(userService.getOrCreateUser(any(User.class))).thenReturn(telegramUser);
        TelegramUserSession session = new TelegramUserSession();
        session.setBotStatus(null);
        when(userService.getOrCreateSession(any(User.class))).thenReturn(session);

        var command = bot.mapToTelegramCommand(update);

        assertNotNull(command);
        assertEquals(1L, command.userId());
        assertEquals("en", command.languageCode());
    }

    @Test
    void onUpdateReceived_whenSyncAndHandleThrows_callsSendErrorReplyIfPossible() throws Exception {
        Update update = new Update();
        Message message = new Message();
        message.setMessageId(1);
        Chat chat = new Chat();
        chat.setId(100L);
        message.setChat(chat);
        User from = new User(200L, "u", false);
        message.setFrom(from);
        message.setText("Hi");
        update.setMessage(message);

        TelegramUser telegramUser = new TelegramUser();
        telegramUser.setId(1L);
        telegramUser.setTelegramId(200L);
        telegramUser.setLanguageCode("en");
        when(userService.getOrCreateUser(any(User.class))).thenReturn(telegramUser);
        when(userService.getOrCreateSession(any(User.class))).thenReturn(new TelegramUserSession());
        doThrow(new RuntimeException("sync failed")).when(commandSyncService).syncAndHandle(any());

        TelegramBot spyBot = spy(bot);
        doNothing().when(spyBot).sendErrorMessage(anyLong(), anyString(), any());

        spyBot.onUpdateReceived(update);

        verify(commandSyncService).syncAndHandle(any());
        verify(spyBot).sendErrorMessage(eq(100L), anyString(), eq(1));
    }

    @Test
    void clearStatus_whenSessionHasBotStatus_clearsIt() {
        TelegramUser user = new TelegramUser();
        user.setTelegramId(200L);
        TelegramUserSession session = new TelegramUserSession();
        session.setTelegramUser(user);
        session.setBotStatus("/role");
        when(userService.tryToGetSession(100L)).thenReturn(java.util.Optional.of(session));

        bot.clearStatus(100L);

        verify(userService).updateUserSession(eq(user), isNull());
    }

    @Test
    void clearStatus_whenSessionHasNoBotStatus_doesNotUpdate() {
        TelegramUserSession session = new TelegramUserSession();
        session.setBotStatus(null);
        when(userService.tryToGetSession(100L)).thenReturn(java.util.Optional.of(session));

        bot.clearStatus(100L);

        verify(userService, never()).updateUserSession(any(), any());
    }

    @Test
    void setMyCommands_withLanguageCode_executesWithLanguage() throws Exception {
        List<BotCommand> commands = List.of(new BotCommand("start", "Start bot"));
        TelegramBot spyBot = spy(bot);
        doReturn(null).when(spyBot).execute(any(SetMyCommands.class));

        spyBot.setMyCommands(commands, "ru");

        verify(spyBot).execute(any(SetMyCommands.class));
    }

    @Test
    void setMyCommands_withoutLanguageCode_executesDefault() throws Exception {
        List<BotCommand> commands = List.of(new BotCommand("start", "Start"));
        TelegramBot spyBot = spy(bot);
        doReturn(null).when(spyBot).execute(any(SetMyCommands.class));

        spyBot.setMyCommands(commands);

        verify(spyBot).execute(any(SetMyCommands.class));
    }

}
