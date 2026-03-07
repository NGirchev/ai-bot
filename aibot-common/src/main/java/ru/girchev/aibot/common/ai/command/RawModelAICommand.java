package ru.girchev.aibot.common.ai.command;

import ru.girchev.aibot.common.ai.ModelType;

import java.util.Map;
import java.util.Set;

public record RawModelAICommand(
        String modelTypeRaw,
        double temp,
        int maxTokens,
        String systemRole,
        String userRole,
        boolean stream,
        Map<String, String> metadata,
        Map<String, Object> body) implements AICommand {

    @Override
    public Set<ModelType> modelTypes() {
        return Set.of(ModelType.RAW_TYPE);
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
