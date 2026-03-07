package ru.girchev.aibot.rest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import ru.girchev.aibot.rest.dto.*;
import ru.girchev.aibot.rest.exception.UnauthorizedException;
import ru.girchev.aibot.rest.service.ChatService;
import ru.girchev.aibot.rest.service.RestAuthorizationService;

import java.util.List;

/**
 * Контроллер для работы с сессиями чата через UI
 * Управляет сессиями и сообщениями
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/session")
@RequiredArgsConstructor
@Tag(name = "Session Controller", description = "Endpoints for chat session interactions")
public class SessionController {

    private final ChatService chatService;
    private final RestAuthorizationService restAuthorizationService;

    private static final String SESSION_EMAIL_KEY = "userEmail";

    @PostMapping
    @Operation(summary = "Send message to new chat", description = "Creates a new chat session and sends a message")
    public ResponseEntity<ChatResponseDto<String>> sendMessageToNewChat(
            @RequestBody ChatRequestDto request,
            HttpServletRequest httpRequest,
            HttpSession session) {
        return ResponseEntity.ok(
                chatService.sendMessageToNewChat(
                        request.message(),
                        restAuthorizationService.authorize(getEmailFromSessionOrRequest(session, request.email())),
                        httpRequest,
                        false)
        );
    }

    @PostMapping("/{sessionId}")
    @Operation(summary = "Send message to existing session", description = "Sends a message to an existing chat session")
    public ResponseEntity<ChatResponseDto<String>> sendMessage(
            @PathVariable String sessionId,
            @RequestBody ChatRequestDto request,
            HttpServletRequest httpRequest,
            HttpSession session) {
        return ResponseEntity.ok(chatService.sendMessage(
                sessionId,
                request.message(),
                restAuthorizationService.authorize(getEmailFromSessionOrRequest(session, request.email())),
                httpRequest,
                false)
        );
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendMessageToNewChatStream(
            @RequestBody ChatRequestDto request,
            HttpServletRequest httpRequest,
            HttpSession session) {
        // Получаем email из сессии или из запроса (для обратной совместимости с REST API)
        String email = getEmailFromSessionOrRequest(session, request.email());
        var user = restAuthorizationService.authorize(email);
        ChatResponseDto<Flux<String>> response = chatService.sendMessageToNewChat(request.message(), user, httpRequest, true);
        String sessionId = response.sessionId();
        // Отправляем sessionId в первом событии с типом "metadata"
        ServerSentEvent<String> sessionEvent = ServerSentEvent.<String>builder()
                .event("metadata")
                .data("{\"sessionId\":\"" + sessionId + "\"}")
                .build();
        // Затем отправляем поток сообщений с типом "message" (или без типа для обычного контента)
        // НЕ используем delayElements - отправляем данные сразу, как только они приходят
        return Flux.concat(
                Flux.just(sessionEvent),
                response.message()
                        // превращаем в SSE (без event type - это обычный контент)
                        .map(ch -> ServerSentEvent.builder(ch).build())
        );
    }

    @PostMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendMessageStream(
            @PathVariable String sessionId,
            @RequestBody ChatRequestDto request,
            HttpServletRequest httpRequest,
            HttpSession session) {
        // Получаем email из сессии или из запроса (для обратной совместимости с REST API)
        String email = getEmailFromSessionOrRequest(session, request.email());
        var user = restAuthorizationService.authorize(email);
        ChatResponseDto<Flux<String>> response = chatService.sendMessage(sessionId, request.message(), user, httpRequest, true);
        // НЕ используем delayElements - отправляем данные сразу, как только они приходят
        return response.message()
                // превращаем в SSE
                .map(ch -> ServerSentEvent.builder(ch).build());
    }

    @GetMapping
    @Operation(summary = "Get all sessions", description = "Returns list of all chat sessions for the user")
    public ResponseEntity<List<ChatSessionDto>> getSessions(
            @RequestParam(value = "email", required = false) String email,
            HttpSession session) {
        // Получаем email из сессии или из параметра запроса (для обратной совместимости с REST API)
        String userEmail = getEmailFromSessionOrRequest(session, email);
        var user = restAuthorizationService.authorize(userEmail);
        List<ChatSessionDto> sessions = chatService.getSessions(user);
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/{sessionId}/messages")
    @Operation(summary = "Get session messages", description = "Returns chat history for a specific session")
    public ResponseEntity<ChatHistoryResponseDto> getSessionMessages(
            @PathVariable String sessionId,
            @RequestParam(value = "email", required = false) String email,
            HttpSession session) {
        // Получаем email из сессии или из параметра запроса (для обратной совместимости с REST API)
        String userEmail = getEmailFromSessionOrRequest(session, email);
        var user = restAuthorizationService.authorize(userEmail);
        List<ChatMessageDto> messages = chatService.getChatHistory(sessionId, user);
        return ResponseEntity.ok(new ChatHistoryResponseDto(sessionId, messages));
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Delete session", description = "Deletes a chat session and all its messages")
    public ResponseEntity<Void> deleteSession(
            @PathVariable String sessionId,
            @RequestParam(value = "email", required = false) String email,
            HttpSession session) {
        // Получаем email из сессии или из параметра запроса (для обратной совместимости с REST API)
        String userEmail = getEmailFromSessionOrRequest(session, email);
        var user = restAuthorizationService.authorize(userEmail);
        chatService.deleteSession(sessionId, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * Получает email из сессии или из запроса (для обратной совместимости с REST API)
     */
    private String getEmailFromSessionOrRequest(HttpSession session, String emailFromRequest) {
        // Сначала пытаемся получить из сессии (для UI)
        if (session != null) {
            try {
                String emailFromSession = (String) session.getAttribute(SESSION_EMAIL_KEY);
                if (emailFromSession != null && !emailFromSession.isBlank()) {
                    return emailFromSession;
                }
            } catch (IllegalStateException e) {
                // Сессия истекла или невалидна - игнорируем и продолжаем
                log.debug("Session is invalid or expired, trying request parameter");
            }
        }

        // Если в сессии нет, используем из запроса (для REST API)
        if (emailFromRequest != null && !emailFromRequest.isBlank()) {
            return emailFromRequest;
        }

        // Если нигде нет - выбрасываем исключение
        throw new UnauthorizedException("Email required");
    }
}

