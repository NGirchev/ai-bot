package io.github.ngirchev.opendaimon.common.ai.factory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import io.github.ngirchev.opendaimon.bulkhead.model.UserPriority;
import io.github.ngirchev.opendaimon.bulkhead.service.IUserPriorityService;
import io.github.ngirchev.opendaimon.common.ai.ModelCapabilities;
import io.github.ngirchev.opendaimon.common.ai.ModelDescriptionCache;
import io.github.ngirchev.opendaimon.common.exception.UnsupportedModelCapabilityException;
import io.github.ngirchev.opendaimon.common.ai.command.AICommand;
import io.github.ngirchev.opendaimon.common.ai.command.ChatAICommand;
import io.github.ngirchev.opendaimon.common.ai.command.FixedModelChatAICommand;
import org.springframework.util.StringUtils;
import io.github.ngirchev.opendaimon.common.command.ICommand;
import io.github.ngirchev.opendaimon.common.command.IChatCommand;
import io.github.ngirchev.opendaimon.common.model.Attachment;
import io.github.ngirchev.opendaimon.common.model.AttachmentType;

import java.util.*;

import static io.github.ngirchev.opendaimon.common.ai.LlmParamNames.MAX_PRICE;
import static io.github.ngirchev.opendaimon.common.ai.command.AICommand.LANGUAGE_CODE_FIELD;
import static io.github.ngirchev.opendaimon.common.ai.command.AICommand.PREFERRED_MODEL_ID_FIELD;
import static io.github.ngirchev.opendaimon.common.ai.command.AICommand.ROLE_FIELD;
import static io.github.ngirchev.opendaimon.common.ai.ModelCapabilities.*;

@Slf4j
public class DefaultAICommandFactory implements AICommandFactory<AICommand, ICommand<?>> {

    private final IUserPriorityService userPriorityService;
    private final int maxOutputTokens;
    private final Integer maxReasoningTokens;
    private final ModelDescriptionCache modelDescriptionCache;

    public DefaultAICommandFactory(IUserPriorityService userPriorityService, int maxOutputTokens, Integer maxReasoningTokens) {
        this(userPriorityService, maxOutputTokens, maxReasoningTokens, null);
    }

    public DefaultAICommandFactory(IUserPriorityService userPriorityService, int maxOutputTokens, Integer maxReasoningTokens, ModelDescriptionCache modelDescriptionCache) {
        this.userPriorityService = userPriorityService;
        this.maxOutputTokens = maxOutputTokens;
        this.maxReasoningTokens = maxReasoningTokens;
        this.modelDescriptionCache = modelDescriptionCache;
    }

    @Override
    public int priority() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public boolean supports(ICommand<?> input, Map<String, String> metadata) {
        return true;
    }

    @Override
    public AICommand createCommand(ICommand<?> command, Map<String, String> metadata) {
        if (command instanceof IChatCommand<?> chatCommand) {
            if (chatCommand.userText() == null) {
                throw new IllegalStateException("User text is required for message command");
            }
            metadata = new HashMap<>(metadata != null ? metadata : Map.of());
            List<Attachment> attachments = chatCommand.attachments() != null
                    ? chatCommand.attachments()
                    : List.of();
            String attachmentTypes = attachments.stream().map(a -> a.type().toString()).toList().toString();
            UserPriority priority = Optional.ofNullable(userPriorityService.getUserPriority(command.userId()))
                    .orElse(UserPriority.REGULAR);
            log.info("Creating ChatAICommand: userText='{}', attachmentsCount={}, attachmentTypes={}, priority={}",
                    chatCommand.userText(), attachments.size(), attachmentTypes, priority);
            metadata.put(AICommand.USER_PRIORITY_FIELD, priority.name());
            Map<String, Object> body = new HashMap<>();

            // Base modelTypes depending on priority
            Set<ModelCapabilities> baseModelCapabilities = switch (priority) {
                case ADMIN -> Set.of(AUTO);
                case VIP -> {
                    body.put(MAX_PRICE, 0);
                    yield Set.of(CHAT, TOOL_CALLING, WEB);
                }
                default -> Set.of(CHAT);
            };

            // Add VISION dynamically if there are images
            Set<ModelCapabilities> modelCapabilities = addVisionIfNeeded(baseModelCapabilities, attachments);

            // Temperature 0.35 for general assistant (recommended range: 0.3-0.4)
            String systemRole = resolveLanguagePlaceholder(metadata.get(ROLE_FIELD), metadata.get(LANGUAGE_CODE_FIELD));
            String fixedModelId = metadata.get(PREFERRED_MODEL_ID_FIELD);
            if (StringUtils.hasText(fixedModelId)) {
                Set<ModelCapabilities> fixedModelCapabilities;
                if (modelDescriptionCache != null) {
                    fixedModelCapabilities = modelDescriptionCache.getCapabilities(fixedModelId);
                    // For explicitly selected models only validate VISION — routing capabilities
                    // (TOOL_CALLING, WEB) are used for auto-selection only and must
                    // not be enforced against a model the user has deliberately chosen.
                    boolean needsVision = modelCapabilities.contains(VISION);
                    if (needsVision && !fixedModelCapabilities.contains(VISION)) {
                        throw new UnsupportedModelCapabilityException(fixedModelId, Set.of(VISION));
                    }
                } else {
                    fixedModelCapabilities = Set.of();
                }
                return new FixedModelChatAICommand(
                        fixedModelId,
                        fixedModelCapabilities,
                        0.35,
                        maxOutputTokens,
                        maxReasoningTokens,
                        systemRole,
                        chatCommand.userText(),
                        chatCommand.stream(),
                        metadata,
                        body,
                        attachments
                );
            } else {
                return new ChatAICommand(
                        modelCapabilities,
                        0.35,
                        maxOutputTokens,
                        maxReasoningTokens,
                        systemRole,
                        chatCommand.userText(),
                        chatCommand.stream(),
                        metadata,
                        body,
                        attachments
                );
            }
        } else {
            throw new IllegalArgumentException("Supported type is IChatCommand");
        }
    }

    /**
     * Replaces {language_code} placeholder in role content with the human-readable language name.
     * E.g. "ru" → "Russian", "en" → "English".
     */
    static String resolveLanguagePlaceholder(String role, String langCode) {
        if (role == null || !role.contains("{language_code}")) return role;
        String language = switch (langCode != null ? langCode.toLowerCase() : "") {
            case "ru" -> "Russian";
            case "en" -> "English";
            default -> langCode != null ? langCode : "English";
        };
        return role.replace("{language_code}", language);
    }

    /**
     * Adds ModelType.VISION if there are image attachments.
     */
    private Set<ModelCapabilities> addVisionIfNeeded(Set<ModelCapabilities> baseTypes, List<Attachment> attachments) {
        boolean hasImages = attachments.stream()
                .anyMatch(a -> a.type() == AttachmentType.IMAGE);
        
        if (hasImages) {
            Set<ModelCapabilities> withVision = new HashSet<>(baseTypes);
            withVision.add(ModelCapabilities.VISION);
            return withVision;
        }
        return baseTypes;
    }
}
