package ru.girchev.aibot.it.telegram;

import io.github.ngirchev.dotenv.DotEnvLoader;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.girchev.aibot.common.config.CoreFlywayConfig;
import ru.girchev.aibot.common.config.CoreJpaConfig;
import ru.girchev.aibot.common.repository.AIBotMessageRepository;
import ru.girchev.aibot.telegram.TelegramBot;
import ru.girchev.aibot.telegram.command.TelegramCommand;
import ru.girchev.aibot.telegram.command.TelegramCommandType;
import ru.girchev.aibot.telegram.command.handler.impl.MessageTelegramCommandHandler;
import ru.girchev.aibot.telegram.config.TelegramFlywayConfig;
import ru.girchev.aibot.telegram.config.TelegramJpaConfig;
import ru.girchev.aibot.test.TestDatabaseConfiguration;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест для Telegram модуля с реальным Telegram Bot API.
 * 
 * <p><b>Цель:</b> Протестировать модуль aibot-telegram целиком без моков - 
 * реальные бины, реальная БД, реальный Telegram API.
 * 
 * <p>Этот тест проверяет работу TelegramBot с реальным Telegram API.
 * Тест по умолчанию отключен (@Disabled), так как требует реальных credentials.
 * 
 * <p>Для запуска теста:
 * <ol>
 *   <li>Убедитесь что файл .env содержит TELEGRAM_TOKEN, TELEGRAM_USERNAME и ADMIN_TELEGRAM_ID</li>
 *   <li>Удалите @Disabled с нужного теста или со всего класса</li>
 *   <li>Запустите тест</li>
 * </ol>
 */
@Slf4j
@Disabled("Требует реальные Telegram credentials. Удалите @Disabled для локального запуска.")
@SpringBootTest(
        classes = TelegramRealGatewayIT.TestConfig.class,
        properties = {
                "spring.main.banner-mode=off"
        }
)
@ActiveProfiles("integration-test")
@Import({
        TestDatabaseConfiguration.class,
        CoreFlywayConfig.class,
        CoreJpaConfig.class,
        TelegramFlywayConfig.class,
        TelegramJpaConfig.class
})
@TestPropertySource(properties = {
        "ai-bot.telegram.enabled=true",
        "ai-bot.telegram.token=${TELEGRAM_TOKEN}",
        "ai-bot.telegram.username=${TELEGRAM_USERNAME}",
        "ai-bot.common.bulkhead.enabled=true",
        "ai-bot.ai.gateway-mock.enabled=true"
})
class TelegramRealGatewayIT {

    static {
        DotEnvLoader.loadDotEnv(Path.of("../.env"));
    }

    @Value("${ADMIN_TELEGRAM_ID}")
    private Long adminTelegramId;

    @Autowired
    private TelegramBot telegramBot;

    @Autowired
    private MessageTelegramCommandHandler messageHandler;

    @Autowired
    private AIBotMessageRepository messageRepository;

    /**
     * Тест отправки сообщения через реальный Telegram API.
     * <p>
     * Для запуска установите переменную окружения TEST_TELEGRAM_CHAT_ID
     * с вашим chat ID (можно получить через @userinfobot в Telegram).
     */
    @Test
    void messageCommand_sendsRealTelegramMessage() {
        // Arrange
        assertThat(telegramBot.getBotToken())
                .as("TELEGRAM_TOKEN должен быть установлен")
                .isNotBlank();
        assertThat(telegramBot.getBotUsername())
                .as("TELEGRAM_USERNAME должен быть установлен")
                .isNotBlank();

        log.info("=== Testing real Telegram message command ===");
        log.info("Bot: @{}", telegramBot.getBotUsername());
        log.info("Chat ID: {}", adminTelegramId);

        // Создаём имитацию Update с реальным chatId
        var update = new Update();

        var from = new User();
        from.setId(adminTelegramId);
        from.setUserName("real-test-user");
        from.setFirstName("Real");
        from.setLastName("Test");
        from.setLanguageCode("ru");

        var msg = new Message();
        msg.setMessageId(1);
        var chat = new Chat();
        chat.setId(adminTelegramId);
        msg.setChat(chat);
        msg.setText("Тестовое сообщение из интеграционного теста");
        msg.setFrom(from);
        update.setMessage(msg);

        var command = new TelegramCommand(
                null,
                msg.getChatId(),
                new TelegramCommandType(TelegramCommand.MESSAGE),
                update,
                msg.getText()
        );
        command.stream(false);

        // Act - выполняем обработку, это отправит реальное сообщение в Telegram
        messageHandler.handle(command);

        // Assert
        assertThat(messageRepository.count())
                .as("Сообщения должны быть сохранены в БД")
                .isGreaterThanOrEqualTo(2);
        
        log.info("=== Real Telegram message test completed successfully ===");
    }

    /**
     * Тест прямой отправки сообщения через TelegramBot.sendMessage()
     */
    @Test
    void directSendMessage_sendsRealTelegramMessage() throws TelegramApiException {
        // Arrange
        assertThat(telegramBot.getBotToken())
                .as("TELEGRAM_TOKEN должен быть установлен")
                .isNotBlank();

        log.info("=== Testing direct Telegram send message ===");
        log.info("Bot: @{}", telegramBot.getBotUsername());
        log.info("Chat ID: {}", adminTelegramId);

        // Act - отправляем прямое сообщение через Telegram API
        telegramBot.sendMessage(adminTelegramId, "🧪 Прямое тестовое сообщение из TelegramRealGatewayIT");
        
        log.info("=== Direct send message test completed successfully ===");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.ai.model.ollama.autoconfigure.OllamaAutoConfiguration",
            "org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration",
            "ru.girchev.aibot.ai.springai.config.SpringAIAutoConfig",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration",
            "ru.girchev.aibot.rest.config.RestAutoConfig",
            "ru.girchev.aibot.ui.config.UIAutoConfig"
    })
    static class TestConfig {
    }
}
