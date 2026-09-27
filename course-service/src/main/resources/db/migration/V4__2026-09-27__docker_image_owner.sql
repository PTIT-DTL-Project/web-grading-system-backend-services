-- Add owner_id to docker_images so image mutations are scoped to the owning lecturer
-- (mirrors assignments.owner_id / requireOwnedAssignment). Existing images are backfilled
-- to the system principal so the shared library stays usable as system defaults.
ALTER TABLE docker_images ADD COLUMN IF NOT EXISTS owner_id UUID;

UPDATE docker_images
SET owner_id = '00000000-0000-0000-0000-000000000000'
WHERE owner_id IS NULL;

-- SET DEFAULT first so an in-flight old pod's insert lands on the system principal
-- instead of failing during a rolling deploy; then enforce NOT NULL.
ALTER TABLE docker_images ALTER COLUMN owner_id SET DEFAULT '00000000-0000-0000-0000-000000000000';
ALTER TABLE docker_images ALTER COLUMN owner_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_docker_images_owner
    ON docker_images(owner_id) WHERE deleted_at IS NULL;
