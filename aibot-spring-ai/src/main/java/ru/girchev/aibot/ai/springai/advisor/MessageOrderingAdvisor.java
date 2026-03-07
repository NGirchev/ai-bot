package ru.girchev.aibot.ai.springai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;

/**
 * Advisor для переупорядочивания сообщений в промпте.
 * 
 * Исправляет известную проблему Spring AI (issue #4170), когда MessageChatMemoryAdvisor
 * добавляет историю перед System сообщениями.
 * 
 * Этот advisor выполняется после MessageChatMemoryAdvisor и переупорядочивает сообщения:
 * 1. System сообщения (первыми) - включая текущий System и summary из истории
 * 2. Остальные сообщения в исходном порядке (история + новое user сообщение)
 * 
 * Правильный порядок: System -> summary(System) -> История -> User
 */
@Slf4j
public class MessageOrderingAdvisor implements BaseAdvisor {
    
    private static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100; // Выполняется после MessageChatMemoryAdvisor
    
    @Override
    public String getName() {
        return "MessageOrderingAdvisor";
    }
    
    @Override
    public int getOrder() {
        return ORDER;
    }
    
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        return reorderMessages(request);
    }
    
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        // Не изменяем ответ, просто возвращаем как есть
        return response;
    }
    
    /**
     * Переупорядочивает сообщения в запросе: System сообщения первыми, затем остальные.
     */
    private ChatClientRequest reorderMessages(ChatClientRequest request) {
        Prompt prompt = request.prompt();
        List<Message> messages = prompt.getInstructions();
        
        if (messages == null || messages.isEmpty()) {
            log.debug("No messages to reorder");
            return request;
        }
        
        // Разделяем сообщения по типам и находим границу между историей и текущими сообщениями
        List<Message> systemMessagesFromHistory = new ArrayList<>(); // System из истории (summary)
        List<Message> systemMessagesCurrent = new ArrayList<>(); // Текущие System сообщения
        List<Message> nonSystemMessages = new ArrayList<>();
        
        // Находим первое не-System сообщение - это граница между историей и текущими сообщениями
        int firstNonSystemIndex = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (!(messages.get(i) instanceof SystemMessage)) {
                firstNonSystemIndex = i;
                break;
            }
        }
        
        // Разделяем System сообщения: те, что до первого не-System - из истории, остальные - текущие
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message instanceof SystemMessage) {
                if (firstNonSystemIndex == -1 || i < firstNonSystemIndex) {
                    // System сообщения до первого не-System - это из истории (summary)
                    systemMessagesFromHistory.add(message);
                } else {
                    // System сообщения после первого не-System - это текущие
                    systemMessagesCurrent.add(message);
                }
            } else {
                nonSystemMessages.add(message);
            }
        }
        
        // Если нет System сообщений или нет не-System сообщений, возвращаем как есть
        if ((systemMessagesFromHistory.isEmpty() && systemMessagesCurrent.isEmpty()) || nonSystemMessages.isEmpty()) {
            log.debug("No reordering needed: systemMessagesFromHistory={}, systemMessagesCurrent={}, nonSystemMessages={}", 
                    systemMessagesFromHistory.size(), systemMessagesCurrent.size(), nonSystemMessages.size());
            return request;
        }
        
        // Формируем правильный порядок:
        // 1. Текущие System сообщения (первыми) - они были добавлены через promptBuilder.system()
        // 2. System сообщения из истории (summary) - они были добавлены через MessageChatMemoryAdvisor
        // 3. Остальные сообщения в исходном порядке (история + новое user сообщение)
        List<Message> reorderedMessages = new ArrayList<>();
        reorderedMessages.addAll(systemMessagesCurrent); // Текущие System первыми
        reorderedMessages.addAll(systemMessagesFromHistory); // Summary из истории вторыми
        reorderedMessages.addAll(nonSystemMessages); // Остальные сообщения
        
        log.debug("Reordered messages: {} current system messages, {} system from history, then {} non-system messages",
                systemMessagesCurrent.size(), systemMessagesFromHistory.size(), nonSystemMessages.size());
        
        // Создаем новый prompt с переупорядоченными сообщениями
        Prompt newPrompt = prompt.mutate()
                .messages(reorderedMessages)
                .build();
        
        // Создаем новый request с новым prompt
        return request.mutate()
                .prompt(newPrompt)
                .build();
    }
}
