package ru.girchev.aibot.common.service;

import ru.girchev.aibot.common.ai.command.AICommand;
import ru.girchev.aibot.common.ai.response.AIResponse;

import java.util.Map;

public interface AIGateway {
    boolean supports(AICommand command);
    AIResponse generateResponse(AICommand command);
    AIResponse generateResponse(Map<String, Object> request);
}
