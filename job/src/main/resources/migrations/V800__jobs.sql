CREATE TABLE IF NOT EXISTS jobs (
    id BIGINT PRIMARY KEY,
    tag TEXT NOT NULL,
    total BYTEA NOT NULL,
    status BYTEA NOT NULL,
    created BIGINT NOT NULL,
    modified BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_jobs_tag ON jobs(tag);
CREATE INDEX IF NOT EXISTS idx_jobs_created ON jobs(created);
CREATE INDEX IF NOT EXISTS idx_jobs_modified ON jobs(modified);

--  Event log

CREATE TABLE IF NOT EXISTS job_log (
  version BIGINT NOT NULL PRIMARY KEY,
  aggregate_id BIGINT NOT NULL,
  discriminator TEXT NOT NULL,
  namespace INT NOT NULL,
  reference BIGINT,
  parent_id BIGINT,
  grand_parent_id BIGINT,
  props JSONB NOT NULL DEFAULT '{}'::jsonb,
  payload BYTEA NOT NULL
);

CREATE INDEX idx_job_log_discriminator ON job_log (discriminator);
CREATE INDEX idx_job_log_aggregate_id ON job_log (aggregate_id);
CREATE INDEX idx_job_log_namespace ON job_log (namespace);
CREATE INDEX idx_job_log_reference ON job_log (reference);
CREATE INDEX idx_job_log_parent_id ON job_log (parent_id);
CREATE INDEX idx_job_log_grand_parent_id ON job_log (grand_parent_id);

CREATE INDEX idx_job_log_props_all_keys ON job_log USING GIN (props);

