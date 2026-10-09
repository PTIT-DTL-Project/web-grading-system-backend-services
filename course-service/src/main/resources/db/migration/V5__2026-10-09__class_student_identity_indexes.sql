-- Indexes for student identity linking + enrollment lookups, which run on every
-- student read (Review: 2026-10-09, Pullfrog).
--
-- idx_class_students_user: findAllByStudentUserId (enrollment scope on all reads).
-- idx_class_students_email_unlinked: linkStudentIdentity's
--   WHERE student_user_id IS NULL AND lower(email) = ? AND deleted_at IS NULL.
--   Partial + expression index so the per-read link UPDATE never scans the table.
CREATE INDEX IF NOT EXISTS idx_class_students_user
    ON class_students(student_user_id);

CREATE INDEX IF NOT EXISTS idx_class_students_email_unlinked
    ON class_students(lower(email)) WHERE student_user_id IS NULL AND deleted_at IS NULL;
