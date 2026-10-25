CREATE INDEX idx_ai_message_search_scope ON ai_message(conversation_id, sequence_no DESC)
WHERE role IN ('user', 'assistant') AND content IS NOT NULL;
