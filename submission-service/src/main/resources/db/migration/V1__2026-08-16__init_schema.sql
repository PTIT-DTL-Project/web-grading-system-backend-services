CREATE TABLE IF NOT EXISTS submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL,
    student_id UUID NOT NULL,
    plan_id UUID,
    rustfs_path TEXT NOT NULL,
    zip_file_name TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    latest BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_submissions_student ON submissions(student_id);
CREATE INDEX IF NOT EXISTS idx_submissions_assignment ON submissions(assignment_id);
CREATE INDEX IF NOT EXISTS idx_submissions_status ON submissions(status);
CREATE INDEX IF NOT EXISTS idx_submissions_assignment_student ON submissions(assignment_id, student_id);

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
