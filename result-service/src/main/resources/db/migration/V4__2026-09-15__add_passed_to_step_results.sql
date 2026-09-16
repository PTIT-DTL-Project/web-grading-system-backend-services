-- Restore passed column (V1's index referenced a column that did not exist then;
-- now V1 is restored with the index, so drop it before adding the column).
DROP INDEX IF EXISTS idx_step_results_passed;
ALTER TABLE step_results ADD COLUMN IF NOT EXISTS passed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_step_results_passed ON step_results(passed);
