package ru.girchev.aibot.rest.handler;

import jakarta.servlet.http.HttpServletRequest;
import ru.girchev.aibot.common.command.IChatCommand;
import ru.girchev.aibot.rest.dto.ChatRequestDto;

public record RestChatCommand(
        ChatRequestDto chatRequestDto,
        RestChatCommandType commandType,
        HttpServletRequest request,
        Long userId
) implements IChatCommand<RestChatCommandType> {

    @Override
    public Long userId() {
        return userId != null ? userId : 0L;
    }

    @Override
    public String userText() {
        return chatRequestDto != null ? chatRequestDto.message() : null;
    }

    @Override
    public boolean stream() {
        return commandType == RestChatCommandType.STREAM;
    }
}