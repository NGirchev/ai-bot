package io.github.ngirchev.opendaimon.common.ai.command;

import io.github.ngirchev.opendaimon.common.ai.ModelCapabilities;
import io.github.ngirchev.opendaimon.common.model.Attachment;
import io.github.ngirchev.opendaimon.common.model.AttachmentType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI command with a user-selected model that MUST be used as-is,
 * bypassing all capability-based model selection logic.
 * <p>
 * Capabilities are intentionally absent — the model is known explicitly,
 * so capability-based filtering is irrelevant.
 */
public record FixedModelChatAICommand(
        String fixedModelId,
        double temp,
        int maxTokens,
        Integer maxReasoningTokens,
        String systemRole,
        String userRole,
        boolean stream,
        Map<String, String> metadata,
        Map<String, Object> body,
        List<Attachment> attachments) implements AICommand {

    @Override
    public Set<ModelCapabilities> modelCapabilities() {
        return Set.of();
    }

    public boolean hasImageAttachments() {
        return attachments != null && attachments.stream()
                .anyMatch(a -> a.type() == AttachmentType.IMAGE);
    }

    @Override
    public OpenDaimonChatOptions options() {
        Map<String, Object> optionsBody = body != null ? new HashMap<>(body) : new HashMap<>();
        if (maxReasoningTokens != null) {
            optionsBody.put("reasoning", Map.of("max_tokens", maxReasoningTokens));
        }
        return new OpenDaimonChatOptions(temp, maxTokens, systemRole, userRole, stream, optionsBody);
    }
}
