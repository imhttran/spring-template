# spring-template

Full-stack auth template: **Next.js → Spring Boot API → PostgreSQL**. The browser
only ever talks to Next.js; the API is proxied server-side and never exposed
directly.

```
Browser
   ↓
Next.js        frontend/        → :3000   React UI, routing, SSR/static gen, server components
   ↓
Spring API     backend/         → :8080   Spring MVC + JdbcClient + JWT + scrypt + email worker
   ↓
PostgreSQL     migrations apply on boot (Flyway)
```

## Quick Start

**Docker (primary path)** — no local Java, Node, or Postgres needed:

```bash
make up          # or: docker compose up
```

Four services come up: Postgres (host port **5433**, so it won't fight a local
instance on 5432), Mailpit (SMTP on 1025, web UI on 8025), the API on :8080, and
the frontend on :3000. Migrations run automatically on the API's first boot.

**Native** — needs Java 21, Node 20+, and a running PostgreSQL:

```bash
./manage.sh setup   # install dependencies (npm install, gradle build)
./manage.sh up      # start backend + frontend
```

`up` refuses to start if Postgres is down. `DATABASE_URL` must point at
**`template-db`**; the dev default is
`postgres://postgres:postgres@localhost:5432/template-db?sslmode=disable`, and
`./manage.sh` reads `.env` first, then `.env.dev`. If you have an older `.env`
the database name has changed since — see docs/DATABASE.md.

### Entry points

`make` and the interactive menu are both thin wrappers over `./manage.sh`, which
owns the steps — so there is one implementation of each:

| Task     | Docker (primary)                          | Native                          |
| -------- | ----------------------------------------- | ------------------------------- |
| start    | `make up` (`./manage.sh compose:up`)      | `make native-up` (menu [1])     |
| stop     | `make down`                               | `make native-down` (menu [4])   |
| logs     | `make logs WHAT=api`                      | `make native-logs WHAT=backend` |
| status   | `make status` — reports either path       | menu [5]                        |
| tests    | `make test`                               | menu [6]                        |
| build    | `make build` (API jar + frontend bundle)  |                                 |
| db reset | `make db-reset YES=1`                     | menu [9]                        |
| re-seed  | `make db-reseed YES=1`                    | menu [11]                       |
| role     | `make role EMAIL=you@mail.com ROLE=admin` | menu [8]                        |

`make help` lists the targets, `./manage.sh help` lists the subcommands, and
`./manage.sh` with no argument opens the menu.

Dev admin: **admin@mail.com** / **Password1234!** — the first login from a new
browser asks for a 2FA code; in development it's always `1234`, and the browser
is trusted afterwards. Without SMTP configured the mailer logs emails instead of
sending them; under compose, Mailpit collects them at http://localhost:8025.

Useful menu options beyond setup/start: [5] status, [6] tests, [9] reset DB,
[10] tail logs, [11] re-seed (drop DB + restart backend). Every one of them has a
subcommand equivalent (`./manage.sh help`).

## Docs

- **[docs/FEATURE.md](docs/FEATURE.md)** — what this build does
- **[docs/DATABASE.md](docs/DATABASE.md)** — install Postgres, Flyway schema, reset, tests
- **`.env.example`** — every config variable

Background, for how the backend got here (Go → Rust → Spring Boot, each one
replaced rather than run alongside): **[docs/SPRING_MIGRATION.md](docs/SPRING_MIGRATION.md)**
and **[docs/RUST_MIGRATION.md](docs/RUST_MIGRATION.md)**.

## Tests

`make test` (or `./manage.sh test`, or menu [6]) runs the backend tests
(`./gradlew test`) plus the frontend build. 39 tests total; the 12 DB-backed
integration tests need `TEST_DATABASE_URL` and skip without it, leaving the 27
unit tests. See docs/DATABASE.md for the test database.

## Roles

`client` < `staff` < `admin`. Grant via CLI only (no self-service promotion):

```bash
make role EMAIL=you@email.com ROLE=admin
# or: ./manage.sh role you@email.com admin  /  ./manage.sh → [8]
```

## API

19 endpoints under `/api/*` — see the controllers in
`backend/src/main/java/com/example/template/api/`:

- **Public auth** (8): signup, verify, resend-verification, forgot-password,
  reset-password, login, login/verify (2FA code), login/resend (2FA code)
- **Self-service, any signed-in user** (4): me, profile (get/save),
  change-password
- **Staff/admin** (7): list users, create user, delete, verify/unverify,
  change role, resend verification, reset password
