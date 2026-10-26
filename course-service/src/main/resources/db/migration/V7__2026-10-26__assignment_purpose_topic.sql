-- Practice exercises are class-independent: their class_id is NULL and they are
-- visible to every student once published. Graded assignments keep their class.
ALTER TABLE assignments ALTER COLUMN class_id DROP NOT NULL;

ALTER TABLE assignments ADD COLUMN IF NOT EXISTS purpose VARCHAR(20) NOT NULL DEFAULT 'GRADED';
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS topic VARCHAR(255);

-- topic bắt buộc cho mọi bài tập: backfill dòng cũ rồi siết NOT NULL
UPDATE assignments SET topic = 'Chung' WHERE topic IS NULL OR btrim(topic) = '';
ALTER TABLE assignments ALTER COLUMN topic SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_assignments_owner_purpose
    ON assignments(owner_id, purpose) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_assignments_topic
    ON assignments(topic) WHERE deleted_at IS NULL;
