CREATE TABLE ai_stored_tool_result (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES ai_run(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL,
    role_scope VARCHAR(100) NOT NULL,
    full_result TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_stored_tool_result_expiry ON ai_stored_tool_result(expires_at);
