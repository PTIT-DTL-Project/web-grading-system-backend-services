ALTER TABLE ai_run ADD COLUMN caller_email VARCHAR(255);
ALTER TABLE ai_run ADD COLUMN caller_roles VARCHAR(500);
ALTER TABLE ai_pending_action ADD COLUMN result_json JSONB;
