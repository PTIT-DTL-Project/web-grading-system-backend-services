-- V3__2026-10-22__init_ai_agent_tables.sql
-- Initializes AI Agent core entities: conversations, messages, runs, tasks, operations, audit

CREATE TABLE IF NOT EXISTS ai_conversation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    title VARCHAR(255),
    active_task_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_conversation_owner ON ai_conversation(owner_id);

CREATE TABLE IF NOT EXISTS ai_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    run_id UUID,
    role VARCHAR(20) NOT NULL, -- 'user', 'assistant', 'tool', 'system'
    content TEXT,
    tool_call_id VARCHAR(100),
    tool_name VARCHAR(100),
    tool_arguments JSONB,
    tool_result JSONB,
    client_message_id VARCHAR(100),
    sequence_no INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_message_conversation ON ai_message(conversation_id, sequence_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_message_client_id ON ai_message(conversation_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS ai_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    parent_run_id UUID,
    checkpoint_json JSONB,
    version INTEGER NOT NULL DEFAULT 1,
    limits_json JSONB,
    usage_json JSONB,
    error_code VARCHAR(50),
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_run_conversation ON ai_run(conversation_id);
CREATE INDEX IF NOT EXISTS idx_run_status ON ai_run(status) WHERE status IN ('QUEUED', 'RUNNING');

CREATE TABLE IF NOT EXISTS ai_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL,
    type VARCHAR(50), -- 'CREATE_ASSIGNMENT', 'QUERY', 'EXPLAIN'
    goal_json JSONB,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    state_json JSONB,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_task_conversation ON ai_task(conversation_id);

CREATE TABLE IF NOT EXISTS ai_conversation_summary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    through_message_id UUID NOT NULL,
    summary TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_summary_conversation ON ai_conversation_summary(conversation_id);

CREATE TABLE IF NOT EXISTS ai_operation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID REFERENCES ai_task(id) ON DELETE SET NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    command_type VARCHAR(50) NOT NULL,
    request_hash VARCHAR(64),
    payload_json JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    result_ref JSONB,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_check_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_operation_task ON ai_operation(task_id);

CREATE TABLE IF NOT EXISTS ai_tool_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES ai_run(id) ON DELETE CASCADE,
    tool_call_id VARCHAR(100),
    tool_name VARCHAR(100) NOT NULL,
    arguments_redacted JSONB,
    result_redacted JSONB,
    status VARCHAR(20) NOT NULL,
    latency_ms INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_tool_exec_run ON ai_tool_execution(run_id);

CREATE TABLE IF NOT EXISTS ai_pending_action (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID REFERENCES ai_task(id) ON DELETE CASCADE,
    run_id UUID REFERENCES ai_run(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL, -- 'QUESTION', 'CONFIRM'
    question_json JSONB,
    options_json JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_pending_task ON ai_pending_action(task_id) WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS ai_run_event (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES ai_run(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    type VARCHAR(50) NOT NULL,
    payload_json JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_run_event_run ON ai_run_event(run_id, sequence);
