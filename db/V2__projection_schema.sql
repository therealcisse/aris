CREATE TABLE IF NOT EXISTS projection_offset (
  name TEXT NOT NULL,
  version TEXT NOT NULL,
  namespace INT NOT NULL,
  offset BIGINT NOT NULL,
  PRIMARY KEY(name, version, namespace)
);

CREATE TABLE IF NOT EXISTS projection_management (
  name TEXT NOT NULL,
  version TEXT NOT NULL,
  namespace INT NOT NULL,
  stopped BOOLEAN NOT NULL,
  PRIMARY KEY(name, version, namespace)
);

CREATE INDEX IF NOT EXISTS idx_projection_offset_namespace ON projection_offset(namespace);
CREATE INDEX IF NOT EXISTS idx_projection_management_namespace ON projection_management(namespace);
