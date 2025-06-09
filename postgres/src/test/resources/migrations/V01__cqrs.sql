-- Drop tables if they exist
DROP TABLE IF EXISTS snapshots CASCADE;
DROP TABLE IF EXISTS events CASCADE;

CREATE TABLE IF NOT EXISTS snapshots (
  aggregate_id BIGINT NOT NULL PRIMARY KEY,
  version BIGINT NOT NULL,
  UNIQUE (aggregate_id, version)
);

--  Event log

CREATE TABLE IF NOT EXISTS events (
  version BIGINT NOT NULL PRIMARY KEY,
  aggregate_id BIGINT NOT NULL,
  discriminator TEXT NOT NULL,
  namespace INT NOT NULL,
  payload BYTEA NOT NULL,
  timestamp BIGINT NOT NULL
);

CREATE INDEX idx_events_discriminator ON events (discriminator);
CREATE INDEX idx_events_aggregate_id ON events (aggregate_id);
CREATE INDEX idx_events_namespace ON events (namespace);
CREATE INDEX idx_events_timestamp ON events (timestamp);


