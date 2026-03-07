package ru.girchev.aibot.rest.handler;

import ru.girchev.aibot.common.command.ICommandType;

public enum RestChatCommandType implements ICommandType {
    MESSAGE,
    STREAM
}
