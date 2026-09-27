-- V7__docker_image_state.sql -- Executor Service
-- Per-pod image warmth for the Axis-2 pre-pull scanner. One row per
-- (image_url, pod_id); a Deployment restart (new pod name) ages its
-- rows out via the updated_at prune in ImageScanner.
CREATE TABLE IF NOT EXISTS docker_image_state (
    id UUID PRIMARY KEY,
    image_url VARCHAR(500) NOT NULL,
    pod_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_checked_at TIMESTAMPTZ,
    last_pulled_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_docker_image_state_url_pod
    ON docker_image_state(image_url, pod_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_docker_image_state_updated
    ON docker_image_state(updated_at) WHERE deleted_at IS NULL;
