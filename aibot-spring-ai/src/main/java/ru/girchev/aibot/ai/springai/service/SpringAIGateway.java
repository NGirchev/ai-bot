package ru.girchev.aibot.ai.springai.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.girchev.aibot.ai.springai.config.SpringAIModelConfig;
import ru.girchev.aibot.ai.springai.config.SpringAIProperties;
import ru.girchev.aibot.common.ai.ModelType;
import ru.girchev.aibot.common.ai.command.AIBotChatOptions;
import ru.girchev.aibot.common.ai.command.AICommand;
import ru.girchev.aibot.common.ai.response.AIResponse;
import ru.girchev.aibot.common.ai.response.SpringAIResponse;
import ru.girchev.aibot.common.service.AIGateway;
import ru.girchev.aibot.common.service.AIGatewayRegistry;

import java.util.*;

import static ru.girchev.aibot.common.ai.LlmParamNames.*;

@Getter
@Slf4j
@RequiredArgsConstructor
public class SpringAIGateway implements AIGateway {

    private final SpringAIProperties springAiProperties;
    private final AIGatewayRegistry aiGatewayRegistry;
    private final SpringAIModelType springAIModelType;
    private final SpringAIChatService chatService;

    @PostConstruct
    public void init() {
        aiGatewayRegistry.registerAiGateway(this);
    }

    @Override
    public boolean supports(AICommand command) {
        return springAIModelType.getByCapabilities(command.modelTypes()).isPresent();
    }

    @Override
    public AIResponse generateResponse(AICommand command) {
        if (Boolean.TRUE.equals(springAiProperties.getMock())) {
            return createMockResponse();
        }

        try {
            if (command.options() instanceof AIBotChatOptions chatOptions) {
                List<Message> messages = createMessages(chatOptions.body());
                log.trace("Messages size: {}", messages.size());
                
                // ВАЖНО: body с ключом MESSAGES может содержать историю из ConversationHistoryAICommandFactory,
                // но ТОЛЬКО если ai-bot.common.conversation-context.enabled=true.
                // Для local профиля (enabled=false) ConversationHistoryAICommandFactory отключена,
                // поэтому body.messages будет пустым и используется DefaultAICommandFactory.
                // Когда enabled=true, ConversationHistoryAICommandFactory добавляет историю + текущий User запрос в body.messages.
                // Поэтому нужно проверять дубликаты перед добавлением system/user сообщений.
                
                // Важно: текущие system/user должны быть добавлены всегда (как было раньше),
                // но без дублей, если ConversationHistoryAICommandFactory уже положила их в messages.
                if (StringUtils.hasText(chatOptions.systemRole())) {
                    String systemRole = chatOptions.systemRole();
                    boolean alreadyPresent = messages.stream()
                            .filter(SystemMessage.class::isInstance)
                            .map(SystemMessage.class::cast)
                            .anyMatch(m -> systemRole.equals(m.getText()));
                    if (!alreadyPresent) {
                        // System message should be first in the prompt.
                        messages.addFirst(new SystemMessage(systemRole));
                    }
                }
                if (StringUtils.hasText(chatOptions.userRole())) {
                    String userRole = chatOptions.userRole();
                    // Проверяем, есть ли уже User сообщение с таким же текстом в списке
                    boolean alreadyPresent = messages.stream()
                            .filter(UserMessage.class::isInstance)
                            .map(UserMessage.class::cast)
                            .anyMatch(m -> userRole.equals(m.getText()));
                    if (!alreadyPresent) {
                        messages.add(new UserMessage(userRole));
                    }
                }

                SpringAIModelConfig modelConfig = springAIModelType.getByCapabilities(command.modelTypes())
                        .or(() -> springAIModelType.getByCapabilities(Set.of(ModelType.AUTO)))
                        .orElseThrow(() -> new RuntimeException("No model found for capabilities: " + command.modelTypes()));

                if (chatOptions.stream()) {
                    return chatService.streamChat(
                            modelConfig,
                            command,
                            chatOptions,
                            messages);
                } else {
                    return chatService.callChat(
                            modelConfig,
                            command,
                            chatOptions,
                            messages
                    );
                }
            } else {
                throw new IllegalArgumentException();
            }
        } catch (WebClientResponseException e) {
            log.error("Error calling Spring AI ChatModel: {}", e.getMessage());
            throw new RuntimeException("Failed to generate response from Spring AI", e);
        } catch (Exception e) {
            log.error("Error calling Spring AI ChatModel: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate response from Spring AI", e);
        }
    }

    @Override
    public AIResponse generateResponse(Map<String, Object> requestBody) {
        if (Boolean.TRUE.equals(springAiProperties.getMock())) {
            return createMockResponse();
        }

        try {
            List<Message> messages = createMessages(requestBody);
            String modelName = extractModelFromMap(requestBody);
            SpringAIModelConfig modelConfig = modelName != null
                    ? springAIModelType.getByModelName(modelName).orElse(null)
                    : null;
            return chatService.callChatFromBody(
                    modelConfig,
                    requestBody,
                    requestBody.get(AICommand.THREAD_KEY_FIELD),
                    true,
                    messages
            );
        } catch (WebClientResponseException e) {
            log.error("Error calling Spring AI ChatModel: {}", e.getMessage());
            throw new RuntimeException("Failed to generate response from Spring AI", e);
        } catch (Exception e) {
            log.error("Error calling Spring AI ChatModel: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate response from Spring AI", e);
        }
    }

    private List<Message> createMessages(Map<String, Object> requestBody) {
        @SuppressWarnings("unchecked")
        var messagesList = ((List<Map<String, String>>) requestBody.get(MESSAGES));
        if (messagesList == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(messagesList.stream()
                .map(msg -> {
                    String role = msg.get(ROLE);
                    String content = msg.get(CONTENT);
                    var message = switch (role) {
                        case ROLE_SYSTEM -> new SystemMessage(content);
                        case ROLE_USER -> new UserMessage(content);
                        case ROLE_ASSISTANT -> new AssistantMessage(content);
                        default -> throw new IllegalArgumentException("Not supported");
                    };
                    return ((Message) message);
                })
                .toList());
    }

    private AIResponse createMockResponse() {
        return new SpringAIResponse(
                ChatResponse.builder()
                        .generations(List.of(new Generation(new AssistantMessage("Mocked response"))))
                        .build()
        );
    }

    /**
     * Извлекает имя модели из body.
     * Модель может быть указана в ключе MODEL или в OPTIONS.MODEL.
     *
     * @param body body из команды
     * @return имя модели или null, если не указана
     */
    private String extractModelFromMap(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return null;
        }

        // Проверяем прямой ключ MODEL
        Object model = body.get(MODEL);
        if (model instanceof String) {
            return (String) model;
        }

        // Проверяем вложенный OPTIONS.MODEL
        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) body.get(OPTIONS);
        if (options != null) {
            Object optionsModel = options.get(MODEL);
            if (optionsModel instanceof String) {
                return (String) optionsModel;
            }
        }

        return null;
    }

}
