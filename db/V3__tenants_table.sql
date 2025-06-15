CREATE TABLE IF NOT EXISTS tenants (
  namespace TEXT NOT NULL,
  id INT NOT NULL,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  created BIGINT NOT NULL,
  status TEXT NOT NULL,
  PRIMARY KEY(namespace, id)
);
