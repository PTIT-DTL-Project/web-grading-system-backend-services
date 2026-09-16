-- Restore passed column removed from V1; entity StepResult requires it (NOT NULL)
ALTER TABLE step_results ADD COLUMN IF NOT EXISTS passed BOOLEAN NOT NULL DEFAULT FALSE;

-- V1 type was JSONB, V3 converts to TEXT; ensure text after all migrations
-- (no-op if V3 already ran, covers fresh DBs where V1 was patched)
-- actual_value/expected_value already handled by V3, keep here as safety no-op
-- Index on passed (moved from V1 where column did not exist)
CREATE INDEX IF NOT EXISTS idx_step_results_passed ON step_results(passed);
