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

