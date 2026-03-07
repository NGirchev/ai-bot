package ru.girchev.aibot.telegram.command.handler;

/**
 * Маркерный интерфейс для обработчиков, которые могут предоставить описание поддерживаемой команды
 * (используется для формирования списка команд в /start).
 */
public interface TelegramSupportedCommandProvider {

    /**
     * @return строка вида "/command - описание" или null, если обработчик не должен отображаться в списке команд
     */
    String getSupportedCommandText();
}


