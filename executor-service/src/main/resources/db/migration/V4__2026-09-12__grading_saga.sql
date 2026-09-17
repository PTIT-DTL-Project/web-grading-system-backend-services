-- V4__grading_saga.sql — Executor Service
-- Saga tracking: one row per grading attempt + one row per pipeline phase and
-- per executed grading step. Answers "which step of which plan is running"
-- without touching the result tables. Best-effort writes — grading never
-- blocks on these.

-- ============================================================
-- grading_sagas (one per grade() run; retries create new rows)
-- ============================================================
CREATE TABLE IF NOT EXISTS grading_sagas (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES grading_jobs(id),
    saga_type VARCHAR(50) NOT NULL DEFAULT 'GRADE_SUBMISSION',
    status VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    current_step VARCHAR(100),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_grading_sagas_job ON grading_sagas(job_id);
CREATE INDEX IF NOT EXISTS idx_grading_sagas_status ON grading_sagas(status);

-- ============================================================
-- grading_saga_steps (phase rows have NULL plan_id/step_id;
-- per-step rows carry both for plan/step analytics)
-- ============================================================
CREATE TABLE IF NOT EXISTS grading_saga_steps (
    id UUID PRIMARY KEY,
    saga_id UUID NOT NULL REFERENCES grading_sagas(id),
    step_name VARCHAR(300) NOT NULL,
    plan_id UUID,
    step_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_grading_saga_steps_saga ON grading_saga_steps(saga_id);
CREATE INDEX IF NOT EXISTS idx_grading_saga_steps_status ON grading_saga_steps(status);
CREATE INDEX IF NOT EXISTS idx_grading_saga_steps_plan ON grading_saga_steps(plan_id);
