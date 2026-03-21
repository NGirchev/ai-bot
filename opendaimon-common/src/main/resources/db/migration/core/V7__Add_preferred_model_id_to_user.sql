ALTER TABLE "user"
    ADD COLUMN IF NOT EXISTS preferred_model_id VARCHAR(255);
