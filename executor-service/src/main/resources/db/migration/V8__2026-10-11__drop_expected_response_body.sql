-- Phase A3 hygiene: drop the never-populated expected_response_body column
-- (zero writers/readers repo-wide; docs record it as unpopulated).
ALTER TABLE grading_step_results DROP COLUMN IF EXISTS expected_response_body;
