-- V1__init_schema.sql — Assignment Service
-- Schema theo docs/design/design-db-v1.0.md

-- ============================================================
-- classes
-- ============================================================
CREATE TABLE IF NOT EXISTS classes (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    semester VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_classes_owner ON classes(owner_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_classes_owner_name_sem ON classes(owner_id, name, semester) WHERE deleted_at IS NULL;

-- ============================================================
-- class_students
-- ============================================================
CREATE TABLE IF NOT EXISTS class_students (
    id UUID PRIMARY KEY,
    class_id UUID NOT NULL REFERENCES classes(id),
    student_code VARCHAR(50) NOT NULL,
    student_name VARCHAR(200) NOT NULL,
    email VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_class_students_class ON class_students(class_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_class_students_code ON class_students(class_id, student_code) WHERE deleted_at IS NULL;

-- ============================================================
-- assignments
-- ============================================================
CREATE TABLE IF NOT EXISTS assignments (
    id UUID PRIMARY KEY,
    class_id UUID NOT NULL REFERENCES classes(id),
    owner_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    grading_strategy VARCHAR(30) NOT NULL DEFAULT 'STUDENT_DOCKER_COMPOSE',
    docker_compose_template TEXT,
    docker_compose_port INT NOT NULL DEFAULT 8080,
    startup_timeout_ms INT NOT NULL DEFAULT 60000,
    execution_timeout_ms INT NOT NULL DEFAULT 300000,
    max_memory_mb INT NOT NULL DEFAULT 256,
    max_cpu REAL NOT NULL DEFAULT 0.5,
    published BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_assignments_class ON assignments(class_id);
CREATE INDEX IF NOT EXISTS idx_assignments_owner ON assignments(owner_id);
CREATE INDEX IF NOT EXISTS idx_assignments_published ON assignments(published) WHERE deleted_at IS NULL;

-- ============================================================
-- docker_images
-- ============================================================
CREATE TABLE IF NOT EXISTS docker_images (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

-- ============================================================
-- assignment_docker_images
-- ============================================================
CREATE TABLE IF NOT EXISTS assignment_docker_images (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES assignments(id),
    docker_image_id UUID NOT NULL REFERENCES docker_images(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_assign_docker_unique ON assignment_docker_images(assignment_id, docker_image_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_assign_docker_assign ON assignment_docker_images(assignment_id);

-- ============================================================
-- test_plans
-- ============================================================
CREATE TABLE IF NOT EXISTS test_plans (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES assignments(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    sequence_order INT NOT NULL DEFAULT 0,
    weight INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_test_plans_assignment ON test_plans(assignment_id);
CREATE INDEX IF NOT EXISTS idx_test_plans_order ON test_plans(assignment_id, sequence_order);

-- ============================================================
-- test_steps
-- ============================================================
CREATE TABLE IF NOT EXISTS test_steps (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES test_plans(id),
    step_order INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    config JSONB NOT NULL DEFAULT '{}',
    expected_result JSONB,
    weight INT NOT NULL DEFAULT 1,
    timeout_ms INT DEFAULT 30000,
    is_required BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_test_steps_plan ON test_steps(plan_id);
CREATE INDEX IF NOT EXISTS idx_test_steps_plan_order ON test_steps(plan_id, step_order);
CREATE INDEX IF NOT EXISTS idx_test_steps_type ON test_steps(step_type);

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