ALTER TABLE ai_conversation_summary ADD COLUMN role_scope VARCHAR(100) NOT NULL DEFAULT '';
CREATE INDEX idx_summary_role_scope ON ai_conversation_summary(conversation_id, role_scope, created_at DESC);

-- Older assistant replies were saved without run_id. Associate each with the
-- preceding user turn so historical reads can apply the same role boundary.
UPDATE ai_message AS reply
SET run_id = (
    SELECT request.run_id FROM ai_message AS request
    WHERE request.conversation_id = reply.conversation_id
      AND request.role = 'user' AND request.sequence_no < reply.sequence_no
    ORDER BY request.sequence_no DESC LIMIT 1
)
WHERE reply.role = 'assistant' AND reply.run_id IS NULL;
