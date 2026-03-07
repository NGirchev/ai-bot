-- =====================================================
-- Добавление поля messages_at_last_summarization в conversation_thread
-- Для отслеживания количества сообщений на момент последней суммаризации
-- =====================================================
ALTER TABLE conversation_thread 
ADD COLUMN IF NOT EXISTS messages_at_last_summarization INTEGER;

-- Для существующих записей с summary устанавливаем текущее количество сообщений
-- Для записей без summary оставляем NULL
UPDATE conversation_thread 
SET messages_at_last_summarization = total_messages 
WHERE summary IS NOT NULL AND summary != '';
