package io.github.ngirchev.opendaimon.common.exception;

import io.github.ngirchev.opendaimon.common.ai.ModelCapabilities;
import lombok.Getter;

import java.util.Set;

/**
 * Thrown when a user-selected model lacks a required capability (e.g. VISION for image messages).
 * Presentation layer should use {@link #getModelId()} and {@link #getMissingCapabilities()}
 * with MessageSource key {@code common.error.model.unsupported.capability} for localized user message.
 */
@Getter
public class UnsupportedModelCapabilityException extends RuntimeException {

    private final String modelId;
    private final Set<ModelCapabilities> missingCapabilities;

    public UnsupportedModelCapabilityException(String message) {
        super(message);
        this.modelId = null;
        this.missingCapabilities = Set.of();
    }

    /**
     * Preferred constructor for presentation-layer localization.
     */
    public UnsupportedModelCapabilityException(String modelId, Set<ModelCapabilities> missingCapabilities) {
        super("Model \"" + modelId + "\" is missing capabilities: " + missingCapabilities);
        this.modelId = modelId;
        this.missingCapabilities = missingCapabilities;
    }
}
