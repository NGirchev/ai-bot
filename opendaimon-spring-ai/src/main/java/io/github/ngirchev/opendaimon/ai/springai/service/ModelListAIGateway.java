package io.github.ngirchev.opendaimon.ai.springai.service;

import io.github.ngirchev.opendaimon.ai.springai.config.SpringAIModelConfig;
import io.github.ngirchev.opendaimon.ai.springai.retry.SpringAIModelRegistry;
import io.github.ngirchev.opendaimon.bulkhead.model.UserPriority;
import io.github.ngirchev.opendaimon.common.ai.command.AICommand;
import io.github.ngirchev.opendaimon.common.ai.command.ModelListAICommand;
import io.github.ngirchev.opendaimon.common.ai.model.ModelInfo;
import io.github.ngirchev.opendaimon.common.ai.response.AIResponse;
import io.github.ngirchev.opendaimon.common.ai.response.ModelListAIResponse;
import io.github.ngirchev.opendaimon.common.service.AIGateway;
import io.github.ngirchev.opendaimon.common.service.AIGatewayRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
public class ModelListAIGateway implements AIGateway {

    private final SpringAIModelRegistry springAIModelRegistry;
    private final AIGatewayRegistry aiGatewayRegistry;

    public ModelListAIGateway(SpringAIModelRegistry springAIModelRegistry,
                              AIGatewayRegistry aiGatewayRegistry) {
        this.springAIModelRegistry = springAIModelRegistry;
        this.aiGatewayRegistry = aiGatewayRegistry;
    }

    @PostConstruct
    public void init() {
        aiGatewayRegistry.registerAiGateway(this);
    }

    @Override
    public boolean supports(AICommand command) {
        return command instanceof ModelListAICommand;
    }

    @Override
    public AIResponse generateResponse(AICommand command) {
        UserPriority userPriority = resolveUserPriority(command);
        List<SpringAIModelConfig> candidates = springAIModelRegistry.getAllModels(userPriority);
        List<ModelInfo> models = candidates.stream()
                .map(cfg -> new ModelInfo(cfg.getName(), new LinkedHashSet<>(cfg.getCapabilities())))
                .toList();
        log.info("ModelListAIGateway: returning {} models for userPriority={}", models.size(), userPriority);
        return new ModelListAIResponse(models);
    }

    @Override
    public AIResponse generateResponse(Map<String, Object> request) {
        throw new UnsupportedOperationException("ModelListAIGateway only handles typed AICommand");
    }

    private UserPriority resolveUserPriority(AICommand command) {
        if (command == null || command.metadata() == null) {
            return null;
        }
        String raw = command.metadata().get(AICommand.USER_PRIORITY_FIELD);
        if (raw == null) {
            return null;
        }
        try {
            return UserPriority.valueOf(raw);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown userPriority in command metadata: {}", raw);
            return null;
        }
    }
}
