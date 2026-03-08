package io.github.ngirchev.aibot.rest.service;

import io.github.ngirchev.aibot.common.config.CoreCommonProperties;
import io.github.ngirchev.aibot.common.model.AIBotMessage;
import io.github.ngirchev.aibot.common.model.AssistantRole;
import io.github.ngirchev.aibot.common.model.RequestType;
import io.github.ngirchev.aibot.common.service.AIBotMessageService;
import io.github.ngirchev.aibot.rest.model.RestUser;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestMessageServiceTest {

    @Mock
    private AIBotMessageService messageService;
    @Mock
    private RestUserService restUserService;
    @Mock
    private CoreCommonProperties coreCommonProperties;
    @Mock
    private HttpServletRequest request;

    private RestMessageService service;

    @BeforeEach
    void setUp() {
        service = new RestMessageService(messageService, restUserService, coreCommonProperties);
    }

    @Nested
    @DisplayName("saveUserMessage")
    class SaveUserMessage {

        @Test
        void whenAssistantRoleContentProvided_usesItAndBuildsMetadata() {
            RestUser user = new RestUser();
            user.setEmail("user@test.com");
            AssistantRole role = new AssistantRole();
            role.setId(1L);
            when(restUserService.getOrCreateAssistantRole(eq(user), eq("Custom role"))).thenReturn(role);
            when(request.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
            when(request.getRequestURI()).thenReturn("/api/v1/session/123/message");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            AIBotMessage savedMessage = new AIBotMessage();
            when(messageService.saveUserMessage(eq(user), eq("Hello"), eq(RequestType.TEXT), eq(role), any(Map.class)))
                    .thenReturn(savedMessage);

            AIBotMessage result = service.saveUserMessage(user, "Hello", RequestType.TEXT, "Custom role", request);

            assertNotNull(result);
            assertSame(savedMessage, result);
            ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messageService).saveUserMessage(eq(user), eq("Hello"), eq(RequestType.TEXT), eq(role), metadataCaptor.capture());
            Map<String, Object> metadata = metadataCaptor.getValue();
            assertEquals("192.168.1.1", metadata.get("client_ip"));
            assertEquals("TestAgent/1.0", metadata.get("user_agent"));
            assertEquals("/api/v1/session/123/message", metadata.get("endpoint"));
        }

        @Test
        void whenAssistantRoleContentNull_usesDefaultFromProperties() {
            RestUser user = new RestUser();
            when(coreCommonProperties.getAssistantRole()).thenReturn("Default assistant");
            AssistantRole role = new AssistantRole();
            when(restUserService.getOrCreateAssistantRole(eq(user), eq("Default assistant"))).thenReturn(role);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("User-Agent")).thenReturn(null);
            when(request.getRequestURI()).thenReturn("/api/v1/session/msg");
            when(request.getRemoteAddr()).thenReturn("10.0.0.1");
            AIBotMessage savedMessage = new AIBotMessage();
            when(messageService.saveUserMessage(any(), any(), any(), any(AssistantRole.class), any(Map.class))).thenReturn(savedMessage);

            service.saveUserMessage(user, "Hi", RequestType.TEXT, null, request);

            verify(restUserService).getOrCreateAssistantRole(user, "Default assistant");
        }

        @Test
        void whenXForwardedForPresent_usesFirstValueAsClientIp() {
            RestUser user = new RestUser();
            when(coreCommonProperties.getAssistantRole()).thenReturn("Default");
            AssistantRole role = new AssistantRole();
            when(restUserService.getOrCreateAssistantRole(any(), any())).thenReturn(role);
            when(request.getHeader("X-Forwarded-For")).thenReturn("  proxy1, proxy2  ");
            when(request.getHeader("User-Agent")).thenReturn(null);
            when(request.getRequestURI()).thenReturn("/api");
            when(messageService.saveUserMessage(any(), any(), any(), any(AssistantRole.class), any())).thenReturn(new AIBotMessage());

            service.saveUserMessage(user, "Hi", RequestType.TEXT, null, request);

            ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messageService).saveUserMessage(any(), any(), any(), any(AssistantRole.class), metadataCaptor.capture());
            assertEquals("proxy1", metadataCaptor.getValue().get("client_ip"));
        }
    }

    @Nested
    @DisplayName("getClientIpFromMetadata / getUserAgentFromMetadata / getEndpointFromMetadata")
    class MetadataGetters {

        @Test
        void whenMetadataNull_returnsNull() {
            AIBotMessage message = new AIBotMessage();
            message.setMetadata(null);

            assertNull(service.getClientIpFromMetadata(message));
            assertNull(service.getUserAgentFromMetadata(message));
            assertNull(service.getEndpointFromMetadata(message));
        }

        @Test
        void whenMetadataHasValues_returnsThem() {
            AIBotMessage message = new AIBotMessage();
            message.setMetadata(Map.of(
                    "client_ip", "1.2.3.4",
                    "user_agent", "Mozilla/5.0",
                    "endpoint", "/api/v1/session/1/message"
            ));

            assertEquals("1.2.3.4", service.getClientIpFromMetadata(message));
            assertEquals("Mozilla/5.0", service.getUserAgentFromMetadata(message));
            assertEquals("/api/v1/session/1/message", service.getEndpointFromMetadata(message));
        }

        @Test
        void whenKeyMissing_returnsNull() {
            AIBotMessage message = new AIBotMessage();
            message.setMetadata(Map.of("other", "value"));

            assertNull(service.getClientIpFromMetadata(message));
            assertNull(service.getUserAgentFromMetadata(message));
            assertNull(service.getEndpointFromMetadata(message));
        }
    }
}
