package ru.girchev.aibot.common.ai.factory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.girchev.aibot.bulkhead.model.UserPriority;
import ru.girchev.aibot.bulkhead.service.IUserPriorityService;
import ru.girchev.aibot.common.ai.ModelType;
import ru.girchev.aibot.common.ai.command.AICommand;
import ru.girchev.aibot.common.ai.command.ChatAICommand;
import ru.girchev.aibot.common.command.IChatCommand;
import ru.girchev.aibot.common.command.ICommandType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static ru.girchev.aibot.common.ai.LlmParamNames.MAX_PRICE;
import static ru.girchev.aibot.common.ai.LlmParamNames.MODEL;

@ExtendWith(MockitoExtension.class)
class DefaultAICommandFactoryTest {

    @Mock
    private IUserPriorityService userPriorityService;

    @Test
    void whenAdmin_thenUsesAutoModelAndOpenRouterAuto() {
        DefaultAICommandFactory factory = new DefaultAICommandFactory(userPriorityService);
        when(userPriorityService.getUserPriority(1L)).thenReturn(UserPriority.ADMIN);

        AICommand command = factory.createCommand(new TestChatCommand(1L, "hi", false), Map.of());

        assertInstanceOf(ChatAICommand.class, command);
        ChatAICommand chatCommand = (ChatAICommand) command;
        assertEquals(1, chatCommand.modelTypes().size(), "ADMIN должен использовать только AUTO");
        assertTrue(chatCommand.modelTypes().contains(ModelType.AUTO));
    }

    @Test
    void whenVip_thenUsesAutoAndAddsMaxPrice() {
        DefaultAICommandFactory factory = new DefaultAICommandFactory(userPriorityService);
        when(userPriorityService.getUserPriority(2L)).thenReturn(UserPriority.VIP);

        AICommand command = factory.createCommand(new TestChatCommand(2L, "hi", false), Map.of());

        assertInstanceOf(ChatAICommand.class, command);
        ChatAICommand chatCommand = (ChatAICommand) command;
        assertEquals(1, chatCommand.modelTypes().size(), "VIP должен использовать AUTO");
        assertTrue(chatCommand.modelTypes().contains(ModelType.AUTO));
        assertEquals(0, chatCommand.body().get(MAX_PRICE));
    }

    @Test
    void whenRegular_thenUsesChatOnlyWithoutOverrides() {
        DefaultAICommandFactory factory = new DefaultAICommandFactory(userPriorityService);
        when(userPriorityService.getUserPriority(3L)).thenReturn(UserPriority.REGULAR);

        AICommand command = factory.createCommand(new TestChatCommand(3L, "hi", false), Map.of());

        assertInstanceOf(ChatAICommand.class, command);
        ChatAICommand chatCommand = (ChatAICommand) command;
        assertEquals(1, chatCommand.modelTypes().size());
        assertTrue(chatCommand.modelTypes().contains(ModelType.CHAT));
        assertTrue(chatCommand.body().isEmpty());
    }

    private record TestChatCommand(Long userId, String userText, boolean stream) implements IChatCommand<ICommandType> {

        @Override
        public ICommandType commandType() {
            return null;
        }
    }
}
