-- Drop tables if they exist
DROP TABLE IF EXISTS snapshots CASCADE;
DROP TABLE IF EXISTS events CASCADE;
DROP TABLE IF EXISTS aggregates CASCADE;

CREATE TABLE IF NOT EXISTS snapshots (
  aggregate_id TEXT NOT NULL,
  version INT NOT NULL,
  PRIMARY KEY (aggregate_id, version)
);

CREATE TABLE IF NOT EXISTS events (
  version TEXT NOT NULL PRIMARY KEY,
  aggregate_id TEXT NOT NULL,
  discriminator TEXT NOT NULL,
  payload BYTEA NOT NULL
);

CREATE INDEX idx_events_discriminator ON events (discriminator);
CREATE INDEX idx_events_aggregate_id ON events (aggregate_id);
