-- Exact skip exclusion for max-per-plan scoring (2026-10-10): a skipped step
-- used to persist as passed=false/score=0, indistinguishable from a real
-- failure, so decomposing a FULL run dragged plan subtotals down.
-- Historical rows keep FALSE = the old semantics (their skip info is
-- unrecoverable); new reports from executor-service carry the flag.
ALTER TABLE step_results ADD COLUMN IF NOT EXISTS skipped BOOLEAN NOT NULL DEFAULT FALSE;
