-- V6: add trace_id to grading_jobs for tracking the original wgs-events traceId
ALTER TABLE grading_jobs ADD COLUMN IF NOT EXISTS trace_id VARCHAR(100);
