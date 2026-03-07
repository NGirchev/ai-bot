package ru.girchev.aibot.common.ai.factory;

import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import ru.girchev.aibot.bulkhead.model.UserPriority;
import ru.girchev.aibot.bulkhead.service.IUserPriorityService;
import ru.girchev.aibot.common.ai.command.AICommand;
import ru.girchev.aibot.common.ai.command.ChatAICommand;
import ru.girchev.aibot.common.command.ICommand;
import ru.girchev.aibot.common.command.IChatCommand;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ru.girchev.aibot.common.ai.LlmParamNames.MAX_PRICE;
import static ru.girchev.aibot.common.ai.command.AICommand.ROLE_FIELD;
import static ru.girchev.aibot.common.ai.ModelType.*;

@RequiredArgsConstructor
public class DefaultAICommandFactory implements AICommandFactory<AICommand, ICommand<?>> {

    private final IUserPriorityService userPriorityService;

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
            UserPriority priority = Optional.ofNullable(userPriorityService.getUserPriority(command.userId()))
                    .orElse(UserPriority.REGULAR);
            Map<String, Object> body = new HashMap<>();
            var modelTypes = switch (priority) {
                case ADMIN -> Set.of(AUTO);
                case VIP -> {
                    body.put(MAX_PRICE, 0);
//                    yield Set.of(CHAT, TOOL_CALLING);
                    yield Set.of(AUTO);
                }
                default -> Set.of(CHAT);
            };
            // Температура 0.35 для бытового ассистента (рекомендуемый диапазон: 0.3-0.4)
            return new ChatAICommand(
                    modelTypes,
                    0.35,
                    1000,
                    metadata.get(ROLE_FIELD),
                    chatCommand.userText(),
                    chatCommand.stream(),
                    metadata,
                    body);
        } else {
            throw new IllegalArgumentException("Supported type is IChatCommand");
        }
    }
}
