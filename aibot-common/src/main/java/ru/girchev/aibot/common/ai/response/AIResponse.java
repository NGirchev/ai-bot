package ru.girchev.aibot.common.ai.response;

import ru.girchev.aibot.common.ai.AIGateways;

import java.util.Map;

public interface AIResponse {
    AIGateways gatewaySource();
    Map<String, Object> toMap();
}
