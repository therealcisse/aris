-- Drop tables if they exist
DROP TABLE IF EXISTS snapshots CASCADE;
DROP TABLE IF EXISTS events CASCADE;
DROP TABLE IF EXISTS aggregates CASCADE;

-- Snapshots Table: id and version as composite primary key
CREATE TABLE IF NOT EXISTS snapshots (
  aggregate_id TEXT NOT NULL,
  version INT NOT NULL,
  timestamp INT NOT NULL,
  PRIMARY KEY (aggregate_id, version)
);

-- Aggregates Table: Ensure the version is stored as INT and create composite unique constraint
CREATE TABLE IF NOT EXISTS aggregates (
  id TEXT NOT NULL,
  version INT NOT NULL,      -- Store version as INT for numerical comparisons
  PRIMARY KEY (id),
  UNIQUE(id, version)           -- Ensure combination of id and version is unique
);

-- Events Table: Add primary key, index, and timestamp with default current time
CREATE TABLE IF NOT EXISTS events (
  aggregate_id TEXT PRIMARY KEY,       -- Add unique identifier for events
  discriminator TEXT NOT NULL,  -- Could be indexed if frequently queried
  payload BYTEA NOT NULL,
  timestamp INT,
  FOREIGN KEY (aggregate_id) REFERENCES aggregates(id)
);

-- Index for quick lookup of snapshots by timestamp
CREATE INDEX idx_snapshots_timestamp ON snapshots (timestamp);
-- Index on discriminator for faster queries
CREATE INDEX idx_events_discriminator ON events (discriminator);
-- Index on version for faster lookups by version in aggregates
CREATE INDEX idx_aggregates_version ON aggregates (version);
