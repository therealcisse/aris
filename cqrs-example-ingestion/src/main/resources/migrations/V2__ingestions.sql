-- Drop tables if they exist
DROP TABLE IF EXISTS ingestion CASCADE;

-- ingestions Table: id and version as composite primary key
CREATE TABLE IF NOT EXISTS ingestions (
  id TEXT NOT NULL,
  status JSONB NOT NULL,
  timestamp BIGINT NOT NULL,
  PRIMARY KEY (id)
);

-- Index for quick lookup of ingestions by timestamp
CREATE INDEX idx_ingestions_timestamp ON ingestions (timestamp);
