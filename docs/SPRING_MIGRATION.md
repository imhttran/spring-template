# Spring Migration (legacy backend → Spring Boot backend)

Goal: replace the legacy backend with a Spring Boot one. **Next.js and PostgreSQL
are unchanged**, and the API contract (routes, status codes, JSON shapes) is
frozen — `frontend/next.config.ts`'s proxy and the ported test suite define it.
The Spring backend took the legacy one's place: same directory (`backend/`), same
port, same database. The legacy implementation is gone from the tree — git
history still has it.

```
Browser → Next.js (:3000) → Spring API (:8080, Spring MVC + JdbcClient + JWT + scrypt) → PostgreSQL
```

## Decisions

- **Database renamed** from `rust_template` to `template-db` (tests use
  `template-db-test`). Postgres allows the hyphen but SQL has to quote it, e.g.
  `ALTER DATABASE rust_template RENAME TO "template-db"` and `DROP DATABASE
"template-db"`.
- **Gradle (Kotlin DSL) with a committed wrapper** (9.7.1) — no system Gradle,
  so the build is the same in CI, in Docker, and on a laptop. The wrapper jar is
  committed on purpose; `.gitignore` is written not to swallow it.
- **Java 21 toolchain** — the build declares `languageVersion = 21`, so the JDK
  that launches Gradle (25 here) doesn't have to be the one that compiles. The
  Dockerfile builds on a JDK 21 image for the same reason.
- **`JdbcClient` raw SQL, not JPA or jOOQ** — the backend it replaces was
  sqlx with hand-written SQL; keeping the queries literal keeps the port
  mechanical and the generated SQL predictable.
- **Flyway instead of the hand-rolled migration runner** — the old `migrate`
  applied `00N_*.sql` and recorded them in `schema_migrations`. Flyway reads
  `classpath:db/migration` and keeps its own `flyway_schema_history`. Every
  migration is `IF NOT EXISTS` and `baseline-on-migrate` is on, so Flyway
  tolerates a database the old runner already touched.
- **No Spring Security** — auth is a `HandlerMethodArgumentResolver` for the
  `AuthUser` parameter (declaring the parameter _is_ the gate, the port of
  `requireAuth`), and session sliding is a `OncePerRequestFilter`. Spring
  Security's filter chain and DSL are a lot of machinery for two checks.
- **scrypt kept byte-compatible** — the hash format is `hex(salt):hex(key)`,
  N=16384/r=8/p=1, 16-byte salt, 64-byte key, and the salt input is the
  hex string itself. Existing password hashes have to verify unchanged, so there
  was no room to switch to BCrypt. A pinned test hashes against a Go-era value.
- **jjwt for HS256** — same `{email, exp, iat}` claims as the Rust backend, so
  a token from either backend validates on the other during the overlap.
- **One Gradle module** with `api` / `service` / `repository` packages (plus
  `config` and `cli`). The old backend was one crate with modules; splitting
  into Gradle subprojects would buy nothing here.
- **One entry point, three ways in** — `Makefile` and the interactive menu are
  thin wrappers over `manage.sh` subcommands, so each step has exactly one
  implementation. Make was kept deliberately thin: the bundled macOS GNU Make is
  3.81, which has no `.ONESHELL`, no `.RECIPEPREFIX` and no `!=`, so anything
  with state (pid files, port waits, destructive confirmations) stays in the
  script. The Makefile is a discoverable namespace (`make help`), not a second
  build system.
- Config comes from environment variables only. No Spring profiles — `NODE_ENV`
  picks dev behavior, the same way it did for the Rust backend.

## Class map

| Rust (removed)                  | Spring (backend/)                             |
| ------------------------------- | --------------------------------------------- |
| axum                            | Spring MVC (`@RestController`)                |
| sqlx (`PgPool`)                 | `JdbcClient` + HikariCP                       |
| serde / serde_json              | Jackson                                       |
| jsonwebtoken (HS256)            | jjwt (HS256)                                  |
| scrypt crate                    | BouncyCastle `SCrypt`                         |
| lettre                          | `spring-boot-starter-mail` (`JavaMailSender`) |
| `tokio::time::interval` worker  | `@Scheduled(fixedDelay = 3000)`               |
| hand-rolled `.env` loader       | `EnvFiles` `EnvironmentPostProcessor`         |
| `raw_sql` migrations            | Flyway (`classpath:db/migration`)             |
| `set-role` subcommand           | `main()` intercept + `SetRoleCommand`         |
| `AppError` / `respond` / `fail` | `Api` helpers + `@RestControllerAdvice`       |
| `requireAuth` extractor         | `AuthUserArgumentResolver`                    |
| renewed-token middleware        | `SessionRenewalFilter`                        |

## Phases

- [x] **Phase 0 — scaffolding**: `backend-spring/` module (renamed to
      `backend/` at cutover), Gradle Kotlin DSL + committed wrapper (9.7.1),
      Java 21 toolchain, deps pinned, layered packages. _Exit: `./gradlew build`
      compiles clean._
- [x] **Phase 1 — plumbing**: `AppProperties` + `EnvFiles` (`.env` wins,
      `.env.dev` fills in), `JdbcClient`/HikariCP, Flyway with `V1__init.sql`,
      boot wiring. _Exit: boot applies the schema
      (`flyway_schema_history` = 1). At the time this was two files
      (`V1__init.sql` + `V2__2fa.sql`); they were folded into one at cutover._
- [x] **Phase 2 — auth**: `PasswordHasher` (byte-compatible scrypt),
      `JwtService`, `AuthUser` + `AuthUserArgumentResolver`,
      `SessionRenewalFilter`, `Api` response helpers + `ApiExceptionHandler`,
      and all of `AuthController` (signup, verify, resend-verification,
      forgot/reset password, login, 2FA verify/resend, me, change-password).
      _Exit: JwtService/PasswordHasher/Validators unit tests plus
      SignupAndLoginHappyPath, SignupWeakPassword, TwoFactorLogin,
      MeRequiresToken and SessionSlidesWhileActive pass._
- [x] **Phase 3 — profile**: `Profile` row record, `ProfileRepository`,
      `ProfileService`/`ProfileCommand`, `ProfileController`, camelCase
      serialization, unique-violation → "Profile already exists", one-time
      registration validation. _Exit: ProfileFlow and ProfileValidation pass._
- [x] **Phase 4 — admin**: `UsersController` + `UserAdminService`, role-scoped
      listing, self-delete and self-role blocks, loose boolean decode for the
      verification patch, `parseId` guard, 23505 mapping. _Exit: UsersRbac,
      DeleteOwnAccount and PatchRoleInvalidValue pass._
- [x] **Phase 5 — email queue + mailer**: `EmailQueueRepository` (quoted `"to"`
      column), `EmailQueueService` (bounded retries), `EmailWorker`
      (`@Scheduled`, 3s), `Mailer` (`JavaMailSender` over SMTP, logs the email
      when `SMTP_HOST` is unset, CRLF header guard). _Exit:
      EmailQueueWorkerMarksSent passes; runtime smoke logs the email and flips
      the row to `sent`._
- [x] **Phase 6 — CLI + seed**: `set-role` intercepted in `main` before Spring
      starts (no `.env` loading, reads `DATABASE_URL` directly, non-zero exit on
      failure); `DevAdminSeeder` (development only, idempotent, pre-fills the
      profile). _Exit: SetRoleSubcommand and DevAdminSeed pass; CLI verified
      end-to-end (promote, restore, unknown user, usage)._
- [x] **Phase 7 — packaging + docs**: Dockerfiles and `.dockerignore` for both
      apps, `docker-compose.yml`, `manage.sh` → `./gradlew bootRun`/`test`/
      `build` and `java -jar build/libs/app.jar set-role`, pre-commit hook →
      Gradle, `.gitignore`, and README/DATABASE/FEATURE/SPRING_MIGRATION. _Exit:
      `docker compose config` parses clean; both the native and the compose path
      boot; suite green._

  `.env.example` was **not** touched — see the note below.

- [x] **Phase 8 — task runner**: `manage.sh` split into the interactive menu plus
      subcommands (`up`, `test`, `build`, `fmt`, `db:reset --yes`, `role`,
      `compose:*`, …), each exiting with its step's status so scripts and the
      Makefile can see failures; `Makefile` where every target delegates to
      exactly one subcommand. _Exit: `make help` lists every target,
      `make -n <target>` shows a 1:1 delegation, an unknown subcommand exits 2._
      Docker images were not buildable during this work (see below); they build
      now.
- [x] **Phase 9 — cutover**: the Rust backend deleted and the Spring app moved
      from `backend-spring/` to `backend/`; every path reference updated
      (`.githooks/pre-commit`, `manage.sh`, `.gitignore` — including the dropped
      Rust `target/` entry — `docker-compose.yml`, README/DATABASE); the schema
      consolidated into a single `V1__init.sql`; the Spring sources' references
      to the deleted implementation removed. _Exit: full suite green from
      `backend/`, `docker compose config` clean, app boots/migrates/seeds._

## Done

The conversion is complete: the Spring backend serves the same 19-endpoint API
on the same port (:8080) against the same database (`template-db`), verified
against the ported test suite (39 tests, 12 of them DB-backed). It lives at
`backend/` — the path the Rust backend used, now deleted along with the Go one
before it; both are in git history.

## Parity gotchas (the hard parts)

1. **Two response body shapes, not interchangeable** — the bare
   `{"message": …}` (`Api.msg`) and `{"success": false, "message": …}`
   (`Api.fail`). The bare form is what the auth gates, the profile form, the
   user-management mutations and the `parseId`/framework 400s return; the
   `success:false` form is what signup, login, password reset and admin user
   creation return. Same statuses can carry either shape, so tests assert exact
   bodies.
2. **Lenient request decoding** — a missing or unparsable body decodes to an
   empty request object, exactly like `serde_json::from_slice(...)
.unwrap_or_default()`; route-level validation is what produces the 400. That
   means handlers take `@RequestBody(required = false) byte[]` and decode via
   `Api.decode`, never a typed `@RequestBody` DTO (Spring would 400 on the
   malformed JSON itself).
3. **`X-Renewed-Token` only on 2xx** — the renewed token rides on successful
   responses and never on errors, so a failed request can't slide a session.
   The filter applies it the moment the status is known, not after the body
   flushes.
4. **`JWT_SECRET` must be at least 32 bytes** — the app checks this while
   building `JwtService` and refuses to start on a shorter secret, because a
   short HMAC key is brute-forceable. The built-in dev fallback is long enough;
   compose sets a 32+ byte value. Note this is stricter than the Rust backend,
   which signed with a secret of any length — a deployment carrying a short
   `JWT_SECRET` has to lengthen it. The dev fallback value also differs, so
   sessions minted on the fallback are not interchangeable between the two.
5. **The role gate lives in the controllers** — deliberately, because the
   original checked the role _before_ parsing the path id. Order is visible:
   `DELETE /api/users/abc` as a non-admin is a 403, not a 400.
6. **`created_at` serialises as an ISO-8601 UTC instant** — the admin user list
   carries `createdAt` as e.g. `2026-01-01T00:00:00Z`. Jackson's timestamp
   output is disabled, so an `Instant` renders the way chrono's `DateTime<Utc>`
   did.
7. **The `"to"` column is quoted** — `to` is reserved in SQL, so `email_queue`
   queries write `"to"`. Same reason the database name is quoted in SQL.
8. **`baseline-on-migrate`** — pointing Flyway at a database an earlier runner
   already migrated records a baseline instead of refusing to start, and every
   migration file is `IF NOT EXISTS`, so either path is a no-op.
9. **Enumeration-safe endpoints** — signup and forgot-password still return
   generic messages; the DB-level duplicate detection maps to the same response
   as a successful call.

## Env loading (unchanged contract)

Root `.env` always wins (existing env vars are never overwritten); `.env.dev`
fills in only when `NODE_ENV` is unset or `development`; files are resolved from
the working directory, then its parent. Ported into `EnvFiles` as an
`EnvironmentPostProcessor`. `set-role` is the one exception — it deliberately
loads no `.env` files.

## Not verified in this environment

The Docker path parses (`docker compose config`) and both Dockerfiles are
reviewed, and both images build (`docker compose build`). Note that building
needs working Docker Hub credentials: a stale macOS keychain entry
(`credsStore: osxkeychain`) makes every pull fail with `401 Unauthorized:
incorrect username or password`, including `docker pull hello-world`. Fix with
`docker logout` (anonymous pulls) or `docker login`, then `make up`.

`.env.example` documents the current DSN
(`postgres://…/template-db?sslmode=disable`).

Any personal `.env` / `.env.dev` needs the same value, and the local database has
to exist (`createdb "template-db"`).
