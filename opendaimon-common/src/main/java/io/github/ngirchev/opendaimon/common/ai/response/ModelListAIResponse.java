package io.github.ngirchev.opendaimon.common.ai.response;

import io.github.ngirchev.opendaimon.common.ai.AIGateways;
import io.github.ngirchev.opendaimon.common.ai.model.ModelInfo;

import java.util.List;
import java.util.Map;

public record ModelListAIResponse(List<ModelInfo> models) implements AIResponse {

    @Override
    public AIGateways gatewaySource() {
        return AIGateways.SPRINGAI;
    }

    @Override
    public Map<String, Object> toMap() {
        return Map.of("models", models);
    }
}
