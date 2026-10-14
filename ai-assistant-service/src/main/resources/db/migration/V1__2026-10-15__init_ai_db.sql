CREATE TABLE IF NOT EXISTS ai_explanations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL,
    student_id UUID NOT NULL,
    category VARCHAR(30) NOT NULL DEFAULT 'FULL_ANALYSIS',
    diagnosis TEXT NOT NULL,
    suspected_location TEXT,
    actionable_hints JSONB NOT NULL,
    learning_keywords JSONB,
    telemetry_audit JSONB,
    overall_summary TEXT,
    model_name VARCHAR(50),
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    feedback_rating SMALLINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_ai_explain_lookup ON ai_explanations(submission_id, category);
CREATE INDEX IF NOT EXISTS idx_ai_explain_student ON ai_explanations(student_id);
