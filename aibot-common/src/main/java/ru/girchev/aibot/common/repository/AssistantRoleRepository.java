package ru.girchev.aibot.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.girchev.aibot.common.model.AssistantRole;
import ru.girchev.aibot.common.model.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssistantRoleRepository extends JpaRepository<AssistantRole, Long> {
    
    /**
     * Находит активную роль для пользователя
     */
    @Query("SELECT ar FROM AssistantRole ar WHERE ar.user = :user AND ar.isActive = true")
    Optional<AssistantRole> findActiveByUser(@Param("user") User user);
    
    /**
     * Находит все роли пользователя отсортированные по версии
     */
    @Query("SELECT ar FROM AssistantRole ar WHERE ar.user = :user ORDER BY ar.version DESC")
    List<AssistantRole> findAllByUserOrderByVersionDesc(@Param("user") User user);
    
    /**
     * Находит роль по пользователю и версии
     */
    @Query("SELECT ar FROM AssistantRole ar WHERE ar.user = :user AND ar.version = :version")
    Optional<AssistantRole> findByUserAndVersion(@Param("user") User user, @Param("version") Integer version);
    
    /**
     * Находит роль по пользователю и хэшу содержания
     */
    @Query("SELECT ar FROM AssistantRole ar WHERE ar.user = :user AND ar.contentHash = :contentHash")
    Optional<AssistantRole> findByUserAndContentHash(@Param("user") User user, @Param("contentHash") String contentHash);
    
    /**
     * Получает максимальную версию роли для пользователя
     */
    @Query("SELECT COALESCE(MAX(ar.version), 0) FROM AssistantRole ar WHERE ar.user = :user")
    Integer findMaxVersionByUser(@Param("user") User user);
    
    /**
     * Деактивирует все роли пользователя
     */
    @Modifying
    @Query("UPDATE AssistantRole ar SET ar.isActive = false WHERE ar.user = :user")
    void deactivateAllByUser(@Param("user") User user);
    
    /**
     * Находит неиспользуемые роли (не активные и без запросов за указанный период)
     */
    @Query("SELECT ar FROM AssistantRole ar WHERE ar.isActive = false " +
           "AND ar.usageCount = 0 " +
           "AND ar.lastUsedAt < :thresholdDate")
    List<AssistantRole> findUnusedRoles(@Param("thresholdDate") OffsetDateTime thresholdDate);
    
    /**
     * Находит роли с низким использованием
     */
    @Query("SELECT ar FROM AssistantRole ar WHERE ar.isActive = false " +
           "AND ar.usageCount > 0 " +
           "AND ar.lastUsedAt < :thresholdDate " +
           "ORDER BY ar.lastUsedAt ASC")
    List<AssistantRole> findLowUsageRoles(@Param("thresholdDate") OffsetDateTime thresholdDate);
}

