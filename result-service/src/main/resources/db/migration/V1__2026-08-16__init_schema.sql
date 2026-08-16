-- V1__init_schema.sql — Result Service
-- Schema theo docs/design/design-db-v1.0.md

-- ============================================================
-- results
-- ============================================================
CREATE TABLE IF NOT EXISTS results (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL,
    assignment_id UUID NOT NULL,
    student_id UUID NOT NULL,
    plan_id UUID,
    score DECIMAL(5,2) NOT NULL,
    max_score DECIMAL(5,2) NOT NULL DEFAULT 10.00,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    summary_log TEXT,
    is_latest BOOLEAN NOT NULL DEFAULT true,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_results_submission ON results(submission_id);
CREATE INDEX IF NOT EXISTS idx_results_student ON results(student_id);
CREATE INDEX IF NOT EXISTS idx_results_assignment ON results(assignment_id);
CREATE INDEX IF NOT EXISTS idx_results_latest ON results(student_id, assignment_id) WHERE is_latest = true;
CREATE INDEX IF NOT EXISTS idx_results_status ON results(status);

-- ============================================================
-- step_results
-- ============================================================
CREATE TABLE IF NOT EXISTS step_results (
    id UUID PRIMARY KEY,
    result_id UUID NOT NULL REFERENCES results(id),
    plan_id UUID,
    step_id UUID,
    step_order INT NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    passed BOOLEAN NOT NULL,
    weight INT NOT NULL DEFAULT 1,
    score DECIMAL(5,2) NOT NULL DEFAULT 0,
    actual_value JSONB,
    expected_value JSONB,
    error_message TEXT,
    duration_ms INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_step_results_result ON step_results(result_id);
CREATE INDEX IF NOT EXISTS idx_step_results_passed ON step_results(passed);

-- ============================================================
-- manual_scores
-- ============================================================
CREATE TABLE IF NOT EXISTS manual_scores (
    id UUID PRIMARY KEY,
    class_id UUID NOT NULL,
    student_code VARCHAR(50) NOT NULL,
    assignment_id UUID,
    score DECIMAL(5,2) NOT NULL,
    comment TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_manual_scores_class ON manual_scores(class_id);
CREATE INDEX IF NOT EXISTS idx_manual_scores_student ON manual_scores(class_id, student_code);

CREATE TABLE IF NOT EXISTS http_log (
    id UUID PRIMARY KEY,
    service_name VARCHAR(50) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    method VARCHAR(10) NOT NULL,
    url TEXT NOT NULL,
    port INT,
    request_headers JSONB,
    request_body TEXT,
    status_code INT,
    response_headers JSONB,
    response_body TEXT,
    duration_ms INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_http_log_created ON http_log(created_at);