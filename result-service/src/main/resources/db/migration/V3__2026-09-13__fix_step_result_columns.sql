-- Change actual_value and expected_value from jsonb to text
-- The executor-service sends plain strings (e.g. HTML responses) that are not valid JSON.
ALTER TABLE step_results ALTER COLUMN actual_value TYPE TEXT USING actual_value::text;
ALTER TABLE step_results ALTER COLUMN expected_value TYPE TEXT USING expected_value::text;
