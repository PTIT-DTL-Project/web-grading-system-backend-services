-- V1__init_schema.sql — Executor Service
-- Schema theo docs/design/design-db-v1.0.md

-- ============================================================
-- grading_jobs
-- ============================================================
CREATE TABLE IF NOT EXISTS grading_jobs (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL,
    assignment_id UUID NOT NULL,
    student_id UUID NOT NULL,
    plan_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_grading_jobs_submission ON grading_jobs(submission_id);
CREATE INDEX IF NOT EXISTS idx_grading_jobs_status ON grading_jobs(status);
CREATE INDEX IF NOT EXISTS idx_grading_jobs_created ON grading_jobs(created_at);

-- ============================================================
-- grading_step_results
-- ============================================================
CREATE TABLE IF NOT EXISTS grading_step_results (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES grading_jobs(id),
    plan_id UUID NOT NULL,
    step_id UUID NOT NULL,
    step_order INT NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    actual_status_code INT,
    request_url TEXT,
    request_headers JSONB,
    request_body TEXT,
    response_status_code INT,
    response_headers JSONB,
    response_body TEXT,
    expected_status_code INT,
    expected_response_body TEXT,
    extracted_variables JSONB,
    assertion_result JSONB,
    error_message TEXT,
    duration_ms INT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_grading_step_results_job ON grading_step_results(job_id);
CREATE INDEX IF NOT EXISTS idx_grading_step_results_step ON grading_step_results(step_id);
CREATE INDEX IF NOT EXISTS idx_grading_step_results_status ON grading_step_results(status);

-- ============================================================
-- grading_logs
-- ============================================================
CREATE TABLE IF NOT EXISTS grading_logs (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES grading_jobs(id),
    submission_id UUID NOT NULL,
    step VARCHAR(100),
    message TEXT NOT NULL,
    level VARCHAR(10) NOT NULL DEFAULT 'INFO',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_grading_logs_job ON grading_logs(job_id);
CREATE INDEX IF NOT EXISTS idx_grading_logs_submission ON grading_logs(submission_id);
CREATE INDEX IF NOT EXISTS idx_grading_logs_created ON grading_logs(created_at);

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