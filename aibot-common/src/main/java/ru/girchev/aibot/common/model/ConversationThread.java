package ru.girchev.aibot.common.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity для хранения conversation threads (бесед с AI).
 * Группирует запросы пользователя в логическую беседу с историей сообщений.
 */
@Entity
@Table(name = "conversation_thread", indexes = {
        @Index(name = "idx_conversation_thread_user_id", columnList = "user_id"),
        @Index(name = "idx_conversation_thread_thread_key", columnList = "thread_key"),
        @Index(name = "idx_conversation_thread_is_active", columnList = "is_active"),
        @Index(name = "idx_conversation_thread_last_activity", columnList = "last_activity_at"),
        @Index(name = "idx_conversation_thread_user_active", columnList = "user_id, is_active, last_activity_at")
})
@Getter
@Setter
@ToString(exclude = "user")
@NoArgsConstructor
public class ConversationThread extends AbstractEntity<Long> {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Пользователь, которому принадлежит этот thread
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    /**
     * Уникальный ключ thread (UUID)
     */
    @Column(name = "thread_key", nullable = false, unique = true)
    private String threadKey;
    
    /**
     * Опциональное название темы беседы
     */
    @Column(name = "title", length = 500)
    private String title;
    
    /**
     * Краткая сводка диалога (1-2 абзаца)
     */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;
    
    /**
     * Список ключевых фактов из диалога (memory bullets)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "memory_bullets", columnDefinition = "jsonb")
    private List<String> memoryBullets = new ArrayList<>();
    
    /**
     * Общее количество сообщений в thread
     */
    @Column(name = "total_messages")
    private Integer totalMessages = 0;
    
    /**
     * Количество сообщений на момент последней суммаризации.
     * Используется для отслеживания новых сообщений после суммаризации.
     * NULL означает, что суммаризация еще не выполнялась.
     */
    @Column(name = "messages_at_last_summarization")
    private Integer messagesAtLastSummarization;
    
    /**
     * Общее количество токенов (приблизительная оценка)
     */
    @Column(name = "total_tokens")
    private Long totalTokens = 0L;
    
    /**
     * Признак активного thread
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    /**
     * Дата последней активности в thread
     */
    @Column(name = "last_activity_at", nullable = false)
    private OffsetDateTime lastActivityAt;
    
    /**
     * Дата создания thread
     */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    
    /**
     * Дата закрытия thread
     */
    @Column(name = "closed_at")
    private OffsetDateTime closedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        lastActivityAt = OffsetDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
        if (totalMessages == null) {
            totalMessages = 0;
        }
        if (totalTokens == null) {
            totalTokens = 0L;
        }
        if (memoryBullets == null) {
            memoryBullets = new ArrayList<>();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastActivityAt = OffsetDateTime.now();
    }
}

