-- Drop tables if they exist
DROP TABLE IF EXISTS ingestion CASCADE;

-- ingestions Table: id and version as composite primary key
CREATE TABLE IF NOT EXISTS ingestions (
  id BIGINT NOT NULL,
  status BYTEA NOT NULL,
  timestamp BIGINT NOT NULL,
  PRIMARY KEY (id)
);

-- Index for quick lookup of ingestions by timestamp
CREATE INDEX idx_ingestions_timestamp ON ingestions (timestamp);

--  Event log

CREATE TABLE IF NOT EXISTS ingestion_log (
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

CREATE INDEX idx_ingestion_log_discriminator ON ingestion_log (discriminator);
CREATE INDEX idx_ingestion_log_aggregate_id ON ingestion_log (aggregate_id);
CREATE INDEX idx_ingestion_log_namespace ON ingestion_log (namespace);
CREATE INDEX idx_ingestion_log_reference ON ingestion_log (reference);
CREATE INDEX idx_ingestion_log_parent_id ON ingestion_log (parent_id);
CREATE INDEX idx_ingestion_log_grand_parent_id ON ingestion_log (grand_parent_id);

CREATE INDEX idx_ingestion_log_props_all_keys ON ingestion_log USING GIN (props);

