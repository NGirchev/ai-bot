-- =====================================================
-- Создание таблицы для Spring AI Chat Memory (JDBC репозиторий)
-- =====================================================
-- Эта таблица используется стандартным JdbcChatMemoryRepository из Spring AI
-- для хранения истории сообщений чата

CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp" TIMESTAMP NOT NULL
);

-- Индекс для быстрого поиска сообщений по conversation_id и timestamp
CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
ON SPRING_AI_CHAT_MEMORY(conversation_id, "timestamp");

