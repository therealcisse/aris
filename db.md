# Database Migrations

This project stores all SQL migration scripts in the `db` directory at the repository root. The scripts follow the standard [Flyway](https://flywaydb.org) naming convention.

## Running Migrations

1. **Download Flyway Command Line**

   Grab the standalone Flyway command line package from the [Flyway downloads page](https://flywaydb.org/download). Unzip it somewhere on your machine.

2. **Set Connection Parameters**

   Provide the database connection details when executing Flyway. Below is an example using a PostgreSQL database:

   ```bash
   flyway \
     -url=jdbc:postgresql://<host>:<port>/<database> \
     -user=<user> \
     -password=<password> \
     -locations=filesystem:db \
     migrate
   ```

   The `-locations` option points Flyway to the `db` folder that contains the migration scripts.

3. **Migration Order**

   Flyway executes scripts in lexicographical order based on their version prefix. The migrations included here are:

   - `V1__event_store_schema.sql` – creates the event store tables.
   - `V2__projection_schema.sql` – creates tables used by the projection module.

   Additional scripts should continue the version sequence (e.g. `V3__...`).

That's it! Running the command above will apply all pending migrations in order.
