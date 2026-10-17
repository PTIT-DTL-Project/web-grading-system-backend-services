ALTER TABLE grading_step_results
ADD COLUMN IF NOT EXISTS db_query_count INTEGER,
ADD COLUMN IF NOT EXISTS db_query_logs TEXT,
ADD COLUMN IF NOT EXISTS stack_trace TEXT;

ALTER TABLE grading_jobs
ADD COLUMN IF NOT EXISTS app_container_logs TEXT,
ADD COLUMN IF NOT EXISTS peak_memory_mb INTEGER,
ADD COLUMN IF NOT EXISTS peak_cpu_percent NUMERIC(5,2);
