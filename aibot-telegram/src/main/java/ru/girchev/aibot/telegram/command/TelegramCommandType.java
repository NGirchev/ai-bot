package ru.girchev.aibot.telegram.command;

import ru.girchev.aibot.common.command.ICommandType;

public record TelegramCommandType(String command) implements ICommandType {
} 