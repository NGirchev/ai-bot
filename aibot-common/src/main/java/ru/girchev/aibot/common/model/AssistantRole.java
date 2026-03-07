package ru.girchev.aibot.common.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;

/**
 * Entity для хранения ролей ассистента с версионированием.
 * Каждый пользователь может иметь несколько версий роли,
 * одна из которых является активной (текущей).
 */
@Entity
@Table(name = "assistant_role", indexes = {
        @Index(name = "idx_assistant_role_user_id", columnList = "user_id"),
        @Index(name = "idx_assistant_role_user_active", columnList = "user_id, is_active"),
        @Index(name = "idx_assistant_role_content_hash", columnList = "content_hash")
})
@Getter
@Setter
@ToString(exclude = "user")
@NoArgsConstructor
public class AssistantRole extends AbstractEntity<Long> {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Пользователь, которому принадлежит эта роль
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    /**
     * Содержание роли (системный промпт)
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
    
    /**
     * Хэш содержания для быстрого поиска дубликатов
     */
    @Column(name = "content_hash", nullable = false)
    private String contentHash;
    
    /**
     * Версия роли для данного пользователя
     */
    @Column(name = "version", nullable = false)
    private Integer version;
    
    /**
     * Признак активной (текущей) роли пользователя
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
    
    /**
     * Дата создания роли
     */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    
    /**
     * Дата последнего использования роли
     */
    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;
    
    /**
     * Количество запросов, использующих эту роль
     */
    @Column(name = "usage_count", nullable = false)
    private Long usageCount;
    
    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        lastUsedAt = OffsetDateTime.now();
        if (usageCount == null) {
            usageCount = 0L;
        }
        if (isActive == null) {
            isActive = false;
        }
        // Вычисляем хэш содержания
        if (content != null && contentHash == null) {
            contentHash = String.valueOf(content.hashCode());
        }
    }
    
    /**
     * Увеличивает счетчик использования роли
     */
    public void incrementUsageCount() {
        this.usageCount++;
        this.lastUsedAt = OffsetDateTime.now();
    }
}

