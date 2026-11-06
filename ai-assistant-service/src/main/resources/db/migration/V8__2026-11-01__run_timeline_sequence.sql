ALTER TABLE ai_run ADD COLUMN next_event_sequence INTEGER NOT NULL DEFAULT 1;
UPDATE ai_run r SET next_event_sequence = COALESCE(
    (SELECT MAX(e.sequence) + 1 FROM ai_run_event e WHERE e.run_id = r.id), 1);
CREATE UNIQUE INDEX ux_run_event_run_sequence ON ai_run_event(run_id, sequence);
