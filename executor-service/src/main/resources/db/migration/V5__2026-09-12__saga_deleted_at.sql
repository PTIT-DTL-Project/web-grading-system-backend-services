-- V5: add deleted_at back to saga tables (BaseEntity maps deletedAt -> deleted_at).
ALTER TABLE grading_sagas ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE grading_saga_steps ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
