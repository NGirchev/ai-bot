package ru.girchev.aibot.rest.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import ru.girchev.aibot.common.model.ConversationThread;
import ru.girchev.aibot.common.model.AIBotMessage;
import ru.girchev.aibot.common.model.MessageRole;
import ru.girchev.aibot.common.repository.ConversationThreadRepository;
import ru.girchev.aibot.common.repository.AIBotMessageRepository;
import ru.girchev.aibot.common.service.CommandSyncService;
import ru.girchev.aibot.common.service.ConversationThreadService;
import ru.girchev.aibot.rest.handler.RestChatCommand;
import ru.girchev.aibot.rest.handler.RestChatCommandType;
import ru.girchev.aibot.rest.dto.ChatRequestDto;
import ru.girchev.aibot.rest.dto.ChatResponseDto;
import ru.girchev.aibot.rest.dto.ChatSessionDto;
import ru.girchev.aibot.rest.dto.ChatMessageDto;
import ru.girchev.aibot.rest.model.RestUser;
import ru.girchev.aibot.rest.exception.UnauthorizedException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для работы с чатом через UI
 * Управляет сессиями (conversation threads) и сообщениями
 */
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final ConversationThreadRepository threadRepository;
    private final ConversationThreadService conversationThreadService;
    private final AIBotMessageRepository messageRepository;
    private final CommandSyncService commandSyncService;

    /**
     * Отправляет сообщение в новый чат (создает новую сессию)
     */
    @Transactional
    public <T> ChatResponseDto<T> sendMessageToNewChat(String message, RestUser user, HttpServletRequest request, boolean isStream) {
        // Закрываем текущий активный thread (если есть)
        threadRepository.findMostRecentActiveThread(user)
                .ifPresent(conversationThreadService::closeThread);

        // Создаем новый thread
        ConversationThread thread = conversationThreadService.createNewThread(user);

        // Отправляем сообщение
        return new ChatResponseDto<>(
                sendMessageInternal(thread.getThreadKey(), message, user, request, isStream),
                thread.getThreadKey()
        );
    }

    /**
     * Отправляет сообщение в существующую сессию
     */
    @Transactional
    public <T> ChatResponseDto<T> sendMessage(String sessionId, String message, RestUser user, HttpServletRequest request, boolean isStream) {
        // Находим thread по sessionId
        ConversationThread thread = threadRepository.findByThreadKey(sessionId)
                .orElseThrow(() -> new UnauthorizedException("Session not found: " + sessionId));

        // Проверяем, что thread принадлежит пользователю
        if (!thread.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Session does not belong to user");
        }

        // Активируем thread (закрывает текущий активный и активирует выбранный)
        conversationThreadService.activateThread(user, thread);

        // Отправляем сообщение
        return new ChatResponseDto<>(
                sendMessageInternal(thread.getThreadKey(), message, user, request, isStream),
                thread.getThreadKey()
        );
    }

    /**
     * Внутренний метод для отправки сообщения
     */
    private <T> T sendMessageInternal(String sessionId, String message, RestUser user, HttpServletRequest request, boolean isStream) {
        // Создаем ChatRequest и отправляем через существующий handler
        ChatRequestDto chatRequestDto = new ChatRequestDto(message, null, null, user.getEmail());
        RestChatCommand command = new RestChatCommand(
                chatRequestDto,
                isStream ? RestChatCommandType.STREAM : RestChatCommandType.MESSAGE,
                request,
                user.getId()
        );

        return commandSyncService.syncAndHandle(command);
    }

    /**
     * Получает список всех сессий пользователя
     */
    @Transactional(readOnly = true)
    public List<ChatSessionDto> getSessions(RestUser user) {
        List<ConversationThread> threads = threadRepository.findByUserOrderByLastActivityAtDesc(user);

        return threads.stream()
                .map(thread -> new ChatSessionDto(
                        thread.getThreadKey(),
                        thread.getTitle() != null ? thread.getTitle() : "Untitled",
                        thread.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Получает историю сообщений для сессии
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getChatHistory(String sessionId, RestUser user) {
        ConversationThread thread = threadRepository.findByThreadKey(sessionId)
                .orElseThrow(() -> new UnauthorizedException("Session not found: " + sessionId));

        // Проверяем, что thread принадлежит пользователю
        if (!thread.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Session does not belong to user");
        }

        List<AIBotMessage> messages = messageRepository.findByThreadOrderBySequenceNumberAsc(thread);

        return messages.stream()
                .filter(msg -> msg.getRole() != MessageRole.SYSTEM) // Исключаем системные сообщения
                .map(msg -> new ChatMessageDto(
                        msg.getRole().name(),
                        msg.getContent()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Удаляет сессию
     */
    @Transactional
    public void deleteSession(String sessionId, RestUser user) {
        ConversationThread thread = threadRepository.findByThreadKey(sessionId)
                .orElseThrow(() -> new UnauthorizedException("Session not found: " + sessionId));

        // Проверяем, что thread принадлежит пользователю
        if (!thread.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Session does not belong to user");
        }

        // Удаляем все сообщения
        List<AIBotMessage> messages = messageRepository.findByThreadOrderBySequenceNumberAsc(thread);
        messageRepository.deleteAll(messages);

        // Удаляем thread
        threadRepository.delete(thread);

        log.info("Deleted session {} for user {}", sessionId, user.getEmail());
    }
}

