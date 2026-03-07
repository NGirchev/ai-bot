package ru.girchev.aibot.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.girchev.aibot.common.config.CoreCommonProperties;
import ru.girchev.aibot.common.model.AssistantRole;
import ru.girchev.aibot.common.model.ConversationThread;
import ru.girchev.aibot.common.model.AIBotMessage;
import ru.girchev.aibot.common.model.MessageRole;
import ru.girchev.aibot.common.model.User;
import ru.girchev.aibot.common.repository.AIBotMessageRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static ru.girchev.aibot.common.config.CoreCommonProperties.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationContextPropertiesBuilderServiceTest {

    @Mock
    private AIBotMessageRepository messageRepository;

    @Mock
    private TokenCounter tokenCounter;

    @Mock
    private CoreCommonProperties coreCommonProperties;

    @Mock
    private ConversationContextProperties context;

    @Mock
    private User user;

    private ConversationContextBuilderService conversationContextBuilderService;

    @BeforeEach
    void setUp() {
        when(coreCommonProperties.getConversationContext()).thenReturn(context);
        conversationContextBuilderService = new ConversationContextBuilderService(
            messageRepository,
            tokenCounter,
            coreCommonProperties
        );
    }

    @Test
    void whenBuildContextWithSystemPrompt_thenSystemPromptIsIncluded() {
        // Arrange
        ConversationThread thread = createEmptyThread();
        AssistantRole assistantRole = createAssistantRole("You are a helpful assistant.");
        String currentMessage = "Hello";

        when(context.getIncludeSystemPrompt()).thenReturn(true);
        when(context.getMaxContextTokens()).thenReturn(8000);
        when(context.getMaxResponseTokens()).thenReturn(4000);
        when(messageRepository.findByThreadOrderBySequenceNumberAsc(any())).thenReturn(new ArrayList<>());
        when(tokenCounter.estimateTokens("You are a helpful assistant.")).thenReturn(10);
        when(tokenCounter.estimateTokens(currentMessage)).thenReturn(2);

        // Act
        List<Map<String, String>> result = conversationContextBuilderService.buildContext(
            thread, currentMessage, assistantRole);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("system", result.get(0).get("role"));
        assertEquals("You are a helpful assistant.", result.get(0).get("content"));
        assertEquals("user", result.get(1).get("role"));
        assertEquals(currentMessage, result.get(1).get("content"));
    }

    @Test
    void whenBuildContextWithoutSystemPrompt_thenSystemPromptIsNotIncluded() {
        // Arrange
        ConversationThread thread = createEmptyThread();
        AssistantRole assistantRole = createAssistantRole("You are a helpful assistant.");
        String currentMessage = "Hello";

        when(context.getIncludeSystemPrompt()).thenReturn(false);
        when(context.getMaxContextTokens()).thenReturn(8000);
        when(context.getMaxResponseTokens()).thenReturn(4000);
        when(messageRepository.findByThreadOrderBySequenceNumberAsc(any())).thenReturn(new ArrayList<>());
        when(tokenCounter.estimateTokens(currentMessage)).thenReturn(2);

        // Act
        List<Map<String, String>> result = conversationContextBuilderService.buildContext(
            thread, currentMessage, assistantRole);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("user", result.get(0).get("role"));
        assertEquals(currentMessage, result.get(0).get("content"));
    }

    @Test
    void whenBuildContextWithSummary_thenSummaryIsIncluded() {
        // Arrange
        ConversationThread thread = createEmptyThread();
        thread.setSummary("Previous conversation summary");
        thread.setMemoryBullets(List.of("Fact 1", "Fact 2"));
        AssistantRole assistantRole = createAssistantRole("You are a helpful assistant.");
        String currentMessage = "Hello";

        when(context.getIncludeSystemPrompt()).thenReturn(true);
        when(context.getMaxContextTokens()).thenReturn(8000);
        when(context.getMaxResponseTokens()).thenReturn(4000);
        when(messageRepository.findByThreadOrderBySequenceNumberAsc(any())).thenReturn(new ArrayList<>());
        when(tokenCounter.estimateTokens("You are a helpful assistant.")).thenReturn(10);
        when(tokenCounter.estimateTokens(anyString())).thenReturn(20); // для summary
        when(tokenCounter.estimateTokens(currentMessage)).thenReturn(2);

        // Act
        List<Map<String, String>> result = conversationContextBuilderService.buildContext(
            thread, currentMessage, assistantRole);

        // Assert
        assertNotNull(result);
        assertTrue(result.size() >= 2);
        // Проверяем, что есть summary message
        boolean hasSummary = result.stream()
            .anyMatch(m -> m.get("role").equals("system") && 
                         m.get("content").contains("Краткое содержание"));
        assertTrue(hasSummary);
    }

    @Test
    void whenBuildContextWithHistory_thenHistoryIsIncluded() {
        // Arrange
        ConversationThread thread = createEmptyThread();
        AssistantRole assistantRole = createAssistantRole("You are a helpful assistant.");
        String currentMessage = "Third message";

        AIBotMessage userMsg1 = createUserMessage("First message");
        AIBotMessage assistantMsg1 = createAssistantMessage("First response");
        AIBotMessage userMsg2 = createUserMessage("Second message");
        AIBotMessage assistantMsg2 = createAssistantMessage("Second response");

        when(context.getIncludeSystemPrompt()).thenReturn(true);
        when(context.getMaxContextTokens()).thenReturn(8000);
        when(context.getMaxResponseTokens()).thenReturn(4000);
        when(messageRepository.findByThreadOrderBySequenceNumberAsc(thread))
            .thenReturn(List.of(userMsg1, assistantMsg1, userMsg2, assistantMsg2));
        when(tokenCounter.estimateTokens("You are a helpful assistant.")).thenReturn(10);
        when(tokenCounter.estimateTokens("First message")).thenReturn(3);
        when(tokenCounter.estimateTokens("First response")).thenReturn(5);
        when(tokenCounter.estimateTokens("Second message")).thenReturn(3);
        when(tokenCounter.estimateTokens("Second response")).thenReturn(5);
        when(tokenCounter.estimateTokens(currentMessage)).thenReturn(3);

        // Act
        List<Map<String, String>> result = conversationContextBuilderService.buildContext(
            thread, currentMessage, assistantRole);

        // Assert
        assertNotNull(result);
        // Должно быть: system, user1, assistant1, user2, assistant2, current user
        assertEquals(6, result.size());
        assertEquals("system", result.get(0).get("role"));
        assertEquals("user", result.get(1).get("role"));
        assertEquals("First message", result.get(1).get("content"));
        assertEquals("assistant", result.get(2).get("role"));
        assertEquals("First response", result.get(2).get("content"));
        assertEquals("user", result.get(3).get("role"));
        assertEquals("Second message", result.get(3).get("content"));
        assertEquals("assistant", result.get(4).get("role"));
        assertEquals("Second response", result.get(4).get("content"));
        assertEquals("user", result.get(5).get("role"));
        assertEquals(currentMessage, result.get(5).get("content"));
    }

    @Test
    void whenTokenBudgetExceeded_thenHistoryIsTruncated() {
        // Arrange
        ConversationThread thread = createEmptyThread();
        AssistantRole assistantRole = createAssistantRole("You are a helpful assistant.");
        String currentMessage = "Current message";

        AIBotMessage userMsg1 = createUserMessage("First message");
        AIBotMessage userMsg2 = createUserMessage("Second message");

        when(context.getIncludeSystemPrompt()).thenReturn(true);
        when(context.getMaxContextTokens()).thenReturn(100); // Маленький лимит
        when(context.getMaxResponseTokens()).thenReturn(50);
        when(messageRepository.findByThreadOrderBySequenceNumberAsc(thread))
            .thenReturn(List.of(userMsg1, userMsg2));
        when(tokenCounter.estimateTokens("You are a helpful assistant.")).thenReturn(20);
        when(tokenCounter.estimateTokens("First message")).thenReturn(50); // Превышает лимит
        when(tokenCounter.estimateTokens("Second message")).thenReturn(50);
        when(tokenCounter.estimateTokens(currentMessage)).thenReturn(10);

        // Act
        List<Map<String, String>> result = conversationContextBuilderService.buildContext(
            thread, currentMessage, assistantRole);

        // Assert
        assertNotNull(result);
        // Должен быть только system prompt и current message, история не должна добавиться
        assertEquals(2, result.size());
        assertEquals("system", result.get(0).get("role"));
        assertEquals("user", result.get(1).get("role"));
        assertEquals(currentMessage, result.get(1).get("content"));
    }

    @Test
    void whenThreadHasNoSummary_thenSummaryIsNotIncluded() {
        // Arrange
        ConversationThread thread = createEmptyThread();
        thread.setSummary(null);
        AssistantRole assistantRole = createAssistantRole("You are a helpful assistant.");
        String currentMessage = "Hello";

        when(context.getIncludeSystemPrompt()).thenReturn(true);
        when(context.getMaxContextTokens()).thenReturn(8000);
        when(context.getMaxResponseTokens()).thenReturn(4000);
        when(messageRepository.findByThreadOrderBySequenceNumberAsc(any())).thenReturn(new ArrayList<>());
        when(tokenCounter.estimateTokens("You are a helpful assistant.")).thenReturn(10);
        when(tokenCounter.estimateTokens(currentMessage)).thenReturn(2);

        // Act
        List<Map<String, String>> result = conversationContextBuilderService.buildContext(
            thread, currentMessage, assistantRole);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        // Не должно быть summary message
        boolean hasSummary = result.stream()
            .anyMatch(m -> m.get("content") != null && 
                         m.get("content").contains("Краткое содержание"));
        assertFalse(hasSummary);
    }

    @Test
    void whenServiceResponseHasNullResponseText_thenResponseIsSkipped() {
        // Arrange
        ConversationThread thread = createEmptyThread();
        AssistantRole assistantRole = createAssistantRole("You are a helpful assistant.");
        String currentMessage = "Current message";

        AIBotMessage userMsg = createUserMessage("First message");
        AIBotMessage assistantMsgWithNull = createAssistantMessage(null); // content = null

        when(context.getIncludeSystemPrompt()).thenReturn(true);
        when(context.getMaxContextTokens()).thenReturn(8000);
        when(context.getMaxResponseTokens()).thenReturn(4000);
        when(messageRepository.findByThreadOrderBySequenceNumberAsc(thread))
            .thenReturn(List.of(userMsg, assistantMsgWithNull));
        when(tokenCounter.estimateTokens("You are a helpful assistant.")).thenReturn(10);
        when(tokenCounter.estimateTokens("First message")).thenReturn(3);
        when(tokenCounter.estimateTokens(currentMessage)).thenReturn(3);

        // Act
        // Не должно быть NullPointerException - ответ с null должен быть пропущен
        List<Map<String, String>> result = conversationContextBuilderService.buildContext(
            thread, currentMessage, assistantRole);

        // Assert
        assertNotNull(result);
        // Должно быть: system, user (из истории), current user
        // Ответ с null должен быть пропущен, поэтому assistant сообщения не должно быть
        assertEquals(3, result.size());
        assertEquals("system", result.get(0).get("role"));
        assertEquals("user", result.get(1).get("role"));
        assertEquals("First message", result.get(1).get("content"));
        assertEquals("user", result.get(2).get("role"));
        assertEquals(currentMessage, result.get(2).get("content"));
        // Проверяем, что нет assistant сообщения с null
        boolean hasNullAssistantMessage = result.stream()
            .anyMatch(m -> "assistant".equals(m.get("role")) && 
                         (m.get("content") == null || m.get("content").isEmpty()));
        assertFalse(hasNullAssistantMessage, "Should not have assistant message with null or empty content");
    }

    // Вспомогательные методы для создания тестовых объектов
    private ConversationThread createEmptyThread() {
        ConversationThread thread = new ConversationThread();
        thread.setId(1L);
        thread.setThreadKey("test-thread-key");
        thread.setSummary(null);
        thread.setMemoryBullets(new ArrayList<>());
        return thread;
    }

    private AssistantRole createAssistantRole(String content) {
        AssistantRole role = new AssistantRole();
        role.setId(1L);
        role.setContent(content);
        return role;
    }

    private AIBotMessage createUserMessage(String text) {
        AIBotMessage message = new AIBotMessage();
        message.setId(1L);
        message.setRole(MessageRole.USER);
        message.setContent(text);
        message.setUser(user);
        return message;
    }

    private AIBotMessage createAssistantMessage(String text) {
        AIBotMessage message = new AIBotMessage();
        message.setId(2L);
        message.setRole(MessageRole.ASSISTANT);
        message.setContent(text);
        message.setUser(user);
        return message;
    }
}

