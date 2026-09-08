-- per-plan submission: one latest result per (student, assignment, plan)
-- plan_id nullable -> a "grade all plans" submission keeps its own latest row
ALTER TABLE results ADD COLUMN IF NOT EXISTS plan_weight INT;

DROP INDEX IF EXISTS idx_results_latest;
CREATE UNIQUE INDEX idx_results_latest ON results(student_id, assignment_id, plan_id) WHERE is_latest = true;
