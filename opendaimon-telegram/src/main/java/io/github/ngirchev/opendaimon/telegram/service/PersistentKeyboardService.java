package io.github.ngirchev.opendaimon.telegram.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import io.github.ngirchev.opendaimon.common.config.CoreCommonProperties;
import io.github.ngirchev.opendaimon.common.model.ConversationThread;
import io.github.ngirchev.opendaimon.telegram.TelegramBot;
import io.github.ngirchev.opendaimon.telegram.command.TelegramCommand;
import io.github.ngirchev.opendaimon.telegram.config.TelegramProperties;

import java.util.List;

@Slf4j
public class PersistentKeyboardService {

    private final UserModelPreferenceService userModelPreferenceService;
    private final CoreCommonProperties coreCommonProperties;
    private final ObjectProvider<TelegramBot> telegramBotProvider;
    private final TelegramProperties telegramProperties;

    public PersistentKeyboardService(UserModelPreferenceService userModelPreferenceService,
                                     CoreCommonProperties coreCommonProperties,
                                     ObjectProvider<TelegramBot> telegramBotProvider,
                                     TelegramProperties telegramProperties) {
        this.userModelPreferenceService = userModelPreferenceService;
        this.coreCommonProperties = coreCommonProperties;
        this.telegramBotProvider = telegramBotProvider;
        this.telegramProperties = telegramProperties;
    }

    /**
     * Sends a persistent 2-button keyboard with current model and context usage.
     * Only sends if model-enabled=true in config.
     *
     * @param chatId telegram chat id
     * @param userId internal user id (for model preference lookup)
     * @param thread current conversation thread (may be null, shows "—" for context then)
     */
    public void sendKeyboard(Long chatId, Long userId, ConversationThread thread) {
        if (!telegramProperties.getCommands().isModelEnabled()) {
            return;
        }
        try {
            String modelLabel = buildModelLabel(userId);
            String contextLabel = buildContextLabel(thread);

            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton(modelLabel));
            row.add(new KeyboardButton(contextLabel));

            ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
            markup.setResizeKeyboard(true);
            markup.setIsPersistent(true);

            String statusText = modelLabel + "  ·  " + contextLabel;
            SendMessage msg = new SendMessage(chatId.toString(), statusText);
            msg.setReplyMarkup(markup);
            telegramBotProvider.getObject().execute(msg);
        } catch (Exception e) {
            log.warn("Failed to send persistent keyboard to chat {}: {}", chatId, e.getMessage());
        }
    }

    private String buildModelLabel(Long userId) {
        return userModelPreferenceService.getPreferredModel(userId)
                .map(m -> TelegramCommand.MODEL_KEYBOARD_PREFIX + " " + m)
                .orElse(TelegramCommand.MODEL_KEYBOARD_PREFIX + " Auto");
    }

    private String buildContextLabel(ConversationThread thread) {
        if (thread == null) {
            return TelegramCommand.CONTEXT_KEYBOARD_PREFIX + " —";
        }
        long totalTokens = thread.getTotalTokens() != null ? thread.getTotalTokens() : 0L;
        int maxContextTokens = coreCommonProperties.getSummarization().getMaxContextTokens();
        double threshold = coreCommonProperties.getSummarization().getSummaryTriggerThreshold();
        long triggerAt = (long) (maxContextTokens * threshold);
        int pct = triggerAt > 0 ? (int) Math.min(100, totalTokens * 100 / triggerAt) : 0;
        return TelegramCommand.CONTEXT_KEYBOARD_PREFIX + " " + pct + "%";
    }
}
