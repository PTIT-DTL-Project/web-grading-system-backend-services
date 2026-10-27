-- Product decision 2026-10-27: grading strategy is no longer selectable; every
-- assignment runs STUDENT_DOCKER_COMPOSE. Backfill legacy rows so the executor
-- stops taking the lecturer-compose branch (DockerComposePatcher).
-- docker_compose_template is left untouched to avoid losing lecturer-authored data.
UPDATE assignments
SET grading_strategy = 'STUDENT_DOCKER_COMPOSE'
WHERE grading_strategy = 'LECTURER_DOCKER_COMPOSE';
