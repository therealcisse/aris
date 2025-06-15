CREATE TABLE IF NOT EXISTS events (
  version BIGINT PRIMARY KEY,
  aggregate_id BIGINT NOT NULL,
  discriminator TEXT NOT NULL,
  namespace INT NOT NULL,
  payload BYTEA NOT NULL,
  timestamp BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_events_discriminator ON events(discriminator);
CREATE INDEX IF NOT EXISTS idx_events_aggregate_id ON events(aggregate_id);
CREATE INDEX IF NOT EXISTS idx_events_discriminator_namespace ON events(discriminator, namespace);
CREATE INDEX IF NOT EXISTS idx_events_aggregate_disc_ns ON events(aggregate_id, discriminator, namespace);
CREATE INDEX IF NOT EXISTS idx_events_timestamp ON events(timestamp);

CREATE TABLE IF NOT EXISTS tags (
  version BIGINT NOT NULL,
  tag TEXT NOT NULL,
  PRIMARY KEY(version, tag),
  FOREIGN KEY(version) REFERENCES events(version) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tags_tag ON tags(tag);

CREATE TABLE IF NOT EXISTS snapshots (
  aggregate_id BIGINT PRIMARY KEY,
  version BIGINT NOT NULL
);
