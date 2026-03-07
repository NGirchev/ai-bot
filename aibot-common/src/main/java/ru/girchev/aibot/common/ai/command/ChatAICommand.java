package ru.girchev.aibot.common.ai.command;

import ru.girchev.aibot.common.ai.ModelType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public record ChatAICommand(
        Set<ModelType> modelTypes,
        double temp,
        int maxTokens,
        String systemRole,
        String userRole,
        boolean stream,
        Map<String, String> metadata,
        Map<String, Object> body) implements AICommand {

    public ChatAICommand(Set<ModelType> modelTypes, double temp, int maxTokens, String systemRole, String userRole) {
        this(modelTypes, temp, maxTokens, systemRole, userRole, false, new HashMap<>(), new HashMap<>());
    }

    public ChatAICommand(Set<ModelType> modelTypes, double temp, int maxTokens, String systemRole, String userRole, Map<String, String> metadata) {
        this(modelTypes, temp, maxTokens, systemRole, userRole, false, metadata, new HashMap<>());
    }

    @Override
    public AIBotChatOptions options() {
        return new AIBotChatOptions(
                temp,
                maxTokens,
                systemRole,
                userRole,
                stream,
                body
        );
    }
}
