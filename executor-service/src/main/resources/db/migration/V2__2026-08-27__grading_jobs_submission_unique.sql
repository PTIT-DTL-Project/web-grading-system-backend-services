-- Idempotency backstop for grading-jobs: one job per submission.
-- Consumer may redeliver (Kafka at-least-once); this index rejects duplicates hard.
CREATE UNIQUE INDEX IF NOT EXISTS uk_grading_jobs_submission ON grading_jobs(submission_id);