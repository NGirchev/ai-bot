package ru.girchev.aibot.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.girchev.aibot.common.model.ConversationThread;
import ru.girchev.aibot.common.model.AIBotMessage;
import ru.girchev.aibot.common.model.MessageRole;
import ru.girchev.aibot.common.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с сообщениями в диалогах.
 * Заменяет UserRequestRepository и ServiceResponseRepository.
 */
@Repository
public interface AIBotMessageRepository extends JpaRepository<AIBotMessage, Long> {
    
    /**
     * Находит все сообщения для thread, отсортированные по sequence number
     */
    @Query("SELECT m FROM Message m WHERE m.thread = :thread " +
           "ORDER BY m.sequenceNumber ASC")
    List<AIBotMessage> findByThreadOrderBySequenceNumberAsc(@Param("thread") ConversationThread thread);
    
    /**
     * Находит все сообщения для thread с sequenceNumber больше указанного значения, отсортированные по sequence number
     */
    @Query("SELECT m FROM Message m WHERE m.thread = :thread AND m.sequenceNumber > :minSequenceNumber " +
           "ORDER BY m.sequenceNumber ASC")
    List<AIBotMessage> findByThreadAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
            @Param("thread") ConversationThread thread,
            @Param("minSequenceNumber") Integer minSequenceNumber);
    
    /**
     * Находит все сообщения для thread с указанной ролью, отсортированные по sequence number
     */
    @Query("SELECT m FROM Message m WHERE m.thread = :thread AND m.role = :role " +
           "ORDER BY m.sequenceNumber ASC")
    List<AIBotMessage> findByThreadAndRoleOrderBySequenceNumberAsc(
            @Param("thread") ConversationThread thread,
            @Param("role") MessageRole role);
    
    /**
     * Подсчитывает количество сообщений в thread
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.thread = :thread")
    Integer countByThread(@Param("thread") ConversationThread thread);
    
    /**
     * Подсчитывает количество сообщений в thread с указанной ролью
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.thread = :thread AND m.role = :role")
    Integer countByThreadAndRole(
            @Param("thread") ConversationThread thread,
            @Param("role") MessageRole role);
    
    /**
     * Находит последнее сообщение в thread (максимальный sequence_number)
     */
    @Query("SELECT m FROM Message m WHERE m.thread = :thread " +
           "ORDER BY m.sequenceNumber DESC")
    List<AIBotMessage> findByThreadOrderBySequenceNumberDesc(@Param("thread") ConversationThread thread);
    
    /**
     * Находит последнее сообщение в thread (максимальный sequence_number)
     * Использует Top1 для получения первого результата
     */
    default Optional<AIBotMessage> findLastByThread(ConversationThread thread) {
        List<AIBotMessage> messages = findByThreadOrderBySequenceNumberDesc(thread);
        return messages.isEmpty() ? Optional.empty() : Optional.of(messages.get(0));
    }
    
    /**
     * Находит все сообщения пользователя, отсортированные по дате создания
     */
    @Query("SELECT m FROM Message m WHERE m.user = :user " +
           "ORDER BY m.createdAt DESC")
    List<AIBotMessage> findByUserOrderByCreatedAtDesc(@Param("user") User user);
    
    /**
     * Находит все сообщения пользователя в указанном thread
     */
    @Query("SELECT m FROM Message m WHERE m.user = :user AND m.thread = :thread " +
           "ORDER BY m.sequenceNumber ASC")
    List<AIBotMessage> findByUserAndThreadOrderBySequenceNumberAsc(
            @Param("user") User user,
            @Param("thread") ConversationThread thread);
    
}

