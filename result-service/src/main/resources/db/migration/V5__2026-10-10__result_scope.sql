-- Explicit run scope: FULL = whole-assignment run (plan_id IS NULL),
-- PLAN = single-plan run. Previously the NULL-vs-uuid plan_id convention was
-- the only marker, and demotion scoped per plan let a FULL run's row coexist
-- with an older per-plan latest row (double-counted average, 2026-10-10).
-- Backfill matches the old convention exactly: every NULL-plan row graded all
-- plans (executor planId=null runs every plan).
ALTER TABLE results ADD COLUMN IF NOT EXISTS scope VARCHAR(8);

UPDATE results SET scope = CASE WHEN plan_id IS NULL THEN 'FULL' ELSE 'PLAN' END
WHERE scope IS NULL;

ALTER TABLE results ALTER COLUMN scope SET NOT NULL;
