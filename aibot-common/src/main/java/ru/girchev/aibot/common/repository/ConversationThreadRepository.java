package ru.girchev.aibot.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.girchev.aibot.common.model.ConversationThread;
import ru.girchev.aibot.common.model.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationThreadRepository extends JpaRepository<ConversationThread, Long> {
    
    /**
     * Находит thread по уникальному ключу
     */
    Optional<ConversationThread> findByThreadKey(String threadKey);
    
    /**
     * Находит все активные threads пользователя, отсортированные по дате последней активности (новые первыми)
     */
    List<ConversationThread> findByUserAndIsActiveTrueOrderByLastActivityAtDesc(User user);
    
    /**
     * Находит все threads пользователя (активные и неактивные), отсортированные по дате последней активности (новые первыми)
     */
    List<ConversationThread> findByUserOrderByLastActivityAtDesc(User user);
    
    /**
     * Находит активные threads пользователя, которые неактивны дольше указанного времени
     */
    List<ConversationThread> findByUserAndIsActiveTrueAndLastActivityAtBefore(
            User user, OffsetDateTime before);
    
    /**
     * Находит самый свежий активный thread пользователя
     * Используем Spring Data JPA naming convention для автоматической генерации запроса
     */
    Optional<ConversationThread> findFirstByUserAndIsActiveTrueOrderByLastActivityAtDesc(User user);
    
    /**
     * Находит самый свежий активный thread пользователя (удобный метод-алиас)
     */
    default Optional<ConversationThread> findMostRecentActiveThread(User user) {
        return findFirstByUserAndIsActiveTrueOrderByLastActivityAtDesc(user);
    }
}

