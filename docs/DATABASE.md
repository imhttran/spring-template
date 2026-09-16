# Database (PostgreSQL)

Everything this project does with PostgreSQL, in one page.

## Connection

One variable: `DATABASE_URL` (`.env.dev` already provides the dev default; a
personal root `.env` overrides it).

```
postgres://postgres:postgres@localhost:5432/template-db?sslmode=disable
```

The database is named **`template-db`** (it was `rust_template` before the
Spring migration, so an existing `.env` / `.env.example` needs updating). The
hyphen is legal in a Postgres name but SQL has to quote it, e.g. `DROP DATABASE
"template-db"`.

- `manage.sh` (reset-database, backend startup check) reads `.env` first, then
  `.env.dev`. `set-role` is the exception: it reads `DATABASE_URL` from the
  environment and deliberately ignores the `.env` files.
- Changing host/port/db means changing only this URL — no code changes. Under
  `docker compose` the API connects to `db:5432` instead of `localhost`.

## Setting up a local instance (macOS)

**Option 1 — Homebrew service (recommended): survives reboots**

```bash
brew install postgresql@16
brew services start postgresql@16      # stop with: brew services stop postgresql@16
createuser -s postgres; psql -d postgres -c "ALTER USER postgres PASSWORD 'postgres';"
createdb "template-db"
```

**Option 2 — throwaway instance (no service installed): lost on reboot**

```bash
initdb -D /tmp/spring-template-pg -A trust
pg_ctl -D /tmp/spring-template-pg -l /tmp/spring-template-pg.log start
psql -d postgres -c "CREATE USER postgres WITH PASSWORD 'postgres' SUPERUSER;"
createdb "template-db" -U postgres
```

Check state anytime: `pg_isready -h localhost` (this is what `manage.sh` runs
before launching the backend).

## Schema: how it's managed

Flyway, applied automatically when the Spring API boots:

- Migration files live in `backend/src/main/resources/db/migration/` — one
  `V1__init.sql` holds the whole schema (users, profiles, the mail queue, 2FA).
  Flyway reads that location on every boot and records what it applied in a
  `flyway_schema_history` table — a second boot is a no-op, so there's no
  separate migrate step.
- `spring.flyway.baseline-on-migrate=true` matters when Flyway points at a
  database an earlier migration runner already touched. That runner kept its own
  `schema_migrations` table; without a Flyway history Flyway would normally
  refuse to start. Baselining records a starting point instead, and since every
  migration is `IF NOT EXISTS`, either path is a no-op against an
  already-migrated database.
- Schema changes: add a `V2__*.sql` file. There is no list to keep in sync — the
  filename is the registration.
- Consolidating migration files (`V2__2fa.sql` folded into `V1__init.sql`) means
  a database that recorded the old pair has a history entry Flyway can't resolve
  locally, and validation fails with "applied migration not resolved". Reset the
  schema (`./manage.sh db:reset`, or the manual reset below) and let the
  consolidated file apply — dev and test databases hold nothing worth keeping.

Tables:

| Table           | Purpose                                                     |
| --------------- | ----------------------------------------------------------- |
| `users`         | accounts: email, scrypt password, role, verify/reset tokens |
| `user_profiles` | one-time registration details (`ON DELETE CASCADE`)         |
| `email_queue`   | outbound mail (drained by the `@Scheduled` worker)          |
| `user_devices`  | trusted 2FA devices that skip the login code                |
| `login_codes`   | pending 2FA codes (expiry, attempts, resends)               |

The `email_queue` column is named `"to"`, which is a reserved word, so queries
have to quote it.

Dev seed (`DevAdminSeeder`, only when `NODE_ENV=development`): upserts
`admin@mail.com` / `Password1234!` plus their profile, so the dev admin isn't
blocked by onboarding gates.

## Day-to-day operations

| Task               | Command                                                                                 |
| ------------------ | --------------------------------------------------------------------------------------- |
| Status             | `pg_isready -h localhost` or `./manage.sh` → 5                                          |
| Reset **all** data | `./manage.sh` → 9 (drops and recreates the `public` schema)                             |
| Manual reset       | `psql "$DATABASE_URL" -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'`           |
| Re-seed            | `./manage.sh` → 11 (drop schema, restart the backend so Flyway re-applies and re-seeds) |
| Look around        | `psql "template-db" -U postgres` → `\dt`, `\d users`                                    |
| Promote a user     | `./manage.sh` → 8 (`java -jar build/libs/app.jar set-role`)                             |

`manage.sh` option 9 asks for lowercase `yes` since `DROP SCHEMA public
CASCADE` destroys all data, including the Flyway history — the next boot
re-applies every migration. It reads the same `DATABASE_URL` chain described
above.

## Tests

The DB-backed integration tests need a reachable Postgres and are skipped
otherwise (the 27 unit tests still run):

```bash
createdb "template-db-test"        # once
cd backend
TEST_DATABASE_URL="postgres://postgres:postgres@localhost:5432/template-db-test?sslmode=disable" ./gradlew test
```

The test context points `app.database-url` at `TEST_DATABASE_URL` and boots
Flyway against it, so the schema is applied on the first run. Tests create
unique-email users per run and clean up after themselves, so they never reset
the database. `./manage.sh` → 6 passes `TEST_DATABASE_URL` through when it's set
in your environment.

## Production

Any managed PostgreSQL (RDS, Cloud SQL, Neon, a Docker container) works: set
`DATABASE_URL` in the environment (`NODE_ENV=production` loads no `.env.dev`,
and only the backend's server environment matters — the frontend never touches
Postgres). Migrations apply on first boot against an empty database. Set a real
`JWT_SECRET` of at least 32 bytes.
