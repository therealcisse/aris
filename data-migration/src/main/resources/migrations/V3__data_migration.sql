-- Drop tables if they exist
DROP TABLE IF EXISTS migrations CASCADE;

CREATE TABLE IF NOT EXISTS migrations (
  id TEXT NOT NULL,
  state BYTEA NOT NULL,
  timestamp BIGINT NOT NULL,
  PRIMARY KEY (id)
);

-- Index for quick lookup of migrations by timestamp
CREATE INDEX idx_ingestions_timestamp ON migrations (timestamp);
