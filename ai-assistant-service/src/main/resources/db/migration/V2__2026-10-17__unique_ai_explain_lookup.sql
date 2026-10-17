-- Idempotency backstop for AI explanations: one active row per
-- (submission_id, category). Concurrent POST /explain requests race past the
-- service-level cache check; this index rejects the loser hard.
-- Partial (deleted_at IS NULL) so a soft-deleted row does not block a fresh
-- explanation, matching the entity's @SQLRestriction filter.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_explain_submission_category
    ON ai_explanations(submission_id, category)
    WHERE deleted_at IS NULL;
