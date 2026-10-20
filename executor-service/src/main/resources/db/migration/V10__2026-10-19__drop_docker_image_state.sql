-- V10__drop_docker_image_state.sql -- Executor Service
-- The course-service image library (and its internal docker-images
-- endpoint) was removed, so executor's Axis-2 image pre-pull scanner
-- and grading-time ENSURE_IMAGES gate are gone. The per-pod warmth
-- table created in V7 is no longer read or written; drop it and its
-- indexes (indexes are dropped with the table).
DROP TABLE IF EXISTS docker_image_state;
