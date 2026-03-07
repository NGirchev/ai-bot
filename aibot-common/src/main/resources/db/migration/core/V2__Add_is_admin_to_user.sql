-- =====================================================
-- Добавление поля is_admin в таблицу user
-- =====================================================
ALTER TABLE "user" 
    ADD COLUMN IF NOT EXISTS is_admin BOOLEAN DEFAULT FALSE NOT NULL;

-- Индекс для быстрого поиска администраторов
CREATE INDEX IF NOT EXISTS idx_user_is_admin ON "user"(is_admin) WHERE is_admin = TRUE;

