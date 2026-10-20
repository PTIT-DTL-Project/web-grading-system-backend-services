-- Review: 2026-10-19 — Docker image management removed from course-service.
-- Students now submit .zip files directly; executor-service builds inline.
-- This migration drops the unused image tables and related columns.

-- Junction table first (FK dependency)
DROP TABLE IF EXISTS assignment_docker_images;
DROP TABLE IF EXISTS docker_images;

-- Remove image URL column from assignments if it exists
ALTER TABLE assignments DROP COLUMN IF EXISTS docker_image_urls;

-- Clean up any orphaned sequence/indexes
DROP INDEX IF EXISTS idx_assign_docker_unique;
DROP INDEX IF EXISTS idx_assign_docker_assign;
DROP INDEX IF EXISTS idx_docker_images_owner;
