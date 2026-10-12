-- Step-level grading evidence for verbose student logs (phase A2): per-assertion
-- verdicts, extracted vars, and the sent request. Historical rows keep NULLs =
-- "not captured" (rendered as absent in FE, never backfilled with guesses).
-- Read pattern is always WHERE result_id IN (covered by idx_step_results_result).
ALTER TABLE step_results ADD COLUMN IF NOT EXISTS assertion_details TEXT;
ALTER TABLE step_results ADD COLUMN IF NOT EXISTS extracted_vars TEXT;
ALTER TABLE step_results ADD COLUMN IF NOT EXISTS request_url TEXT;
ALTER TABLE step_results ADD COLUMN IF NOT EXISTS request_body TEXT;
ALTER TABLE step_results ADD COLUMN IF NOT EXISTS response_status INT;
