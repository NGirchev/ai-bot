package io.github.ngirchev.opendaimon.common.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ngirchev.opendaimon.common.ai.AIGateways;
import io.github.ngirchev.opendaimon.common.ai.command.AICommand;
import io.github.ngirchev.opendaimon.common.ai.response.MapResponse;
import io.github.ngirchev.opendaimon.common.config.CoreCommonProperties;
import io.github.ngirchev.opendaimon.common.model.OpenDaimonMessage;
import io.github.ngirchev.opendaimon.common.model.ConversationThread;
import io.github.ngirchev.opendaimon.common.model.MessageRole;
import io.github.ngirchev.opendaimon.common.model.User;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SummarizationServiceTest {

    @Mock
    private ConversationThreadService threadService;

    @Mock
    private AIGatewayRegistry aiGatewayRegistry;

    @Mock
    private CoreCommonProperties coreCommonProperties;

    @Mock
    private CoreCommonProperties.SummarizationProperties summarization;

    @Mock
    private User user;

    private SummarizationService summarizationService;

    @BeforeEach
    void setUp() {
        when(coreCommonProperties.getSummarization()).thenReturn(summarization);
        when(summarization.getPrompt()).thenReturn("Summarize this conversation:");
        ObjectMapper objectMapper = new ObjectMapper();
        summarizationService = new SummarizationService(
            threadService,
            aiGatewayRegistry,
            coreCommonProperties,
            objectMapper
        );
    }

    @Test
    void whenSummarizeThreadWithMessages_thenUpdatesSummary() {
        ConversationThread thread = createThread(1000L);
        List<OpenDaimonMessage> messages = List.of(
            createUserMessage("Message 1"),
            createAssistantMessage("Response 1"),
            createUserMessage("Message 2"),
            createAssistantMessage("Response 2"));

        AIGateway mockGateway = mock(AIGateway.class);
        when(aiGatewayRegistry.getSupportedAiGateways(any())).thenReturn(List.of(mockGateway));
        when(mockGateway.generateResponse(any(AICommand.class)))
            .thenReturn(responseWithContent("{\"summary\": \"Sync summary\", \"memory_bullets\": [\"Bullet 1\"]}"));

        summarizationService.summarizeThread(thread, messages);

        verify(mockGateway).generateResponse(any(AICommand.class));
        verify(threadService).updateThreadSummary(eq(thread), eq("Sync summary"), anyList());
    }

    @Test
    void whenSummarizeThreadWithEmptyMessages_thenNoGatewayCall() {
        ConversationThread thread = createThread(1000L);

        summarizationService.summarizeThread(thread, List.of());

        verify(aiGatewayRegistry, never()).getSupportedAiGateways(any());
        verify(threadService, never()).updateThreadSummary(any(), any(), any());
    }

    @Test
    void whenModelReturnsNonJsonThenValidJson_thenRetrySucceeds() {
        ConversationThread thread = createThread(1000L);
        List<OpenDaimonMessage> messages = List.of(
            createUserMessage("Message 1"),
            createAssistantMessage("Response 1"),
            createUserMessage("Message 2"),
            createAssistantMessage("Response 2"));

        AIGateway mockGateway = mock(AIGateway.class);
        when(aiGatewayRegistry.getSupportedAiGateways(any())).thenReturn(List.of(mockGateway));
        String validJson = "{\"summary\": \"Test summary\", \"memory_bullets\": [\"Fact 1\", \"Fact 2\"]}";
        when(mockGateway.generateResponse(any(AICommand.class)))
            .thenReturn(responseWithContent("Dear, here is the summary..."))
            .thenReturn(responseWithContent(validJson));

        assertDoesNotThrow(() -> summarizationService.summarizeThread(thread, messages));

        verify(mockGateway, times(2)).generateResponse(any(AICommand.class));
        verify(threadService).updateThreadSummary(eq(thread), eq("Test summary"), anyList());
    }

    @Test
    void whenModelAlwaysReturnsNonJson_thenThrowsAfterRetries() {
        ConversationThread thread = createThread(1000L);
        List<OpenDaimonMessage> messages = List.of(
            createUserMessage("Message 1"),
            createAssistantMessage("Response 1"),
            createUserMessage("Message 2"),
            createAssistantMessage("Response 2"));

        AIGateway mockGateway = mock(AIGateway.class);
        when(aiGatewayRegistry.getSupportedAiGateways(any())).thenReturn(List.of(mockGateway));
        when(mockGateway.generateResponse(any(AICommand.class)))
            .thenReturn(responseWithContent("Dear, here is a brief summary..."));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
            () -> summarizationService.summarizeThread(thread, messages));

        verify(mockGateway, times(3)).generateResponse(any(AICommand.class));
        verify(threadService, never()).updateThreadSummary(any(), anyString(), anyList());
    }

    private static MapResponse responseWithContent(String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("content", content);
        Map<String, Object> choice = new HashMap<>();
        choice.put("message", message);
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("choices", List.of(choice));
        return new MapResponse(AIGateways.OPENROUTER, responseData);
    }

    private ConversationThread createThread(Long totalTokens) {
        ConversationThread thread = new ConversationThread();
        thread.setId(1L);
        thread.setThreadKey("test-thread-key");
        thread.setTotalTokens(totalTokens);
        thread.setSummary(null);
        thread.setMemoryBullets(new ArrayList<>());
        return thread;
    }

    private OpenDaimonMessage createUserMessage(String text) {
        OpenDaimonMessage message = new OpenDaimonMessage();
        message.setId(1L);
        message.setRole(MessageRole.USER);
        message.setContent(text);
        message.setUser(user);
        return message;
    }

    private OpenDaimonMessage createAssistantMessage(String text) {
        OpenDaimonMessage message = new OpenDaimonMessage();
        message.setId(2L);
        message.setRole(MessageRole.ASSISTANT);
        message.setContent(text);
        message.setUser(user);
        return message;
    }
}
