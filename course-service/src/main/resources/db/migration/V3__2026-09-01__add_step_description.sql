-- lecturer-authored pedagogical text for a test step (problem-set note shown to students)
-- nullable: empty = FE falls back to auto-generated text from step config
ALTER TABLE test_steps ADD COLUMN IF NOT EXISTS description TEXT;
