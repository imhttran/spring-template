# Spring Migration (legacy backend → Spring Boot backend)

Why the backend looks the way it does. The current behavior lives in the other
docs; this one records the decisions and the traps that are easy to undo by
accident.

The Spring backend replaced a legacy one in the same place — same directory
(`backend/`), same port, same database — behind a frozen API contract: routes,
status codes and JSON shapes are unchanged, defined by `frontend/next.config.ts`'s
proxy and the ported test suite. The legacy implementation is gone from the tree;
git history still has it.

## Decisions

- **Gradle (Kotlin DSL) with a committed wrapper** (9.7.1) — no system Gradle, so
  the build is the same in CI, in Docker, and on a laptop. The wrapper jar is
  committed on purpose; `.gitignore` is written not to swallow it.
- **Java 21 toolchain** — the build declares `languageVersion = 21`, so the JDK
  that launches Gradle (25 here) doesn't have to be the one that compiles. The
  Dockerfile builds on a JDK 21 image for the same reason.
- **`JdbcClient` raw SQL, not JPA or jOOQ** — the backend it replaces was sqlx
  with hand-written SQL, so keeping the queries literal kept the port mechanical
  and the generated SQL predictable. It is also load-bearing: the
  `RETURNING`/`ON CONFLICT` patterns and the 23505 → "Email is already
  registered" mapping have no clean ORM equivalent, and they are part of the
  contract.
- **No Spring Security** — auth is a `HandlerMethodArgumentResolver` for the
  `AuthUser` parameter (declaring the parameter _is_ the gate), and session
  sliding is a `OncePerRequestFilter`. Spring Security's filter chain and DSL are
  a lot of machinery for two checks.
- **scrypt kept byte-compatible** — the hash format is `hex(salt):hex(key)`,
  N=16384/r=8/p=1, 16-byte salt, 64-byte key, and the salt input is the hex
  string itself. Existing password hashes have to verify unchanged, so there was
  no room to switch to BCrypt. A pinned test hashes against a Go-era value.
- **jjwt for HS256** — same `{email, exp, iat}` claims as the backend it
  replaced, so a token from either validates on the other during an overlap.
- **One Gradle module** with `api` / `service` / `repository` packages (plus
  `config` and `cli`). The old backend was one crate with modules; splitting into
  Gradle subprojects would buy nothing here.
- **Config from environment variables only, no Spring profiles** — `NODE_ENV`
  picks dev behavior, and `EnvFiles` resolves `.env` over `.env.dev`.
- **Flyway, not the old hand-rolled runner** — the schema and its history are
  documented in [DATABASE.md](DATABASE.md).

## Class map

| Legacy (removed)                | Spring (backend/)                             |
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

## Parity gotchas (the hard parts)

These are the places the obvious implementation is wrong. Each is pinned by a
test; changing one breaks behavior the contract guarantees.

1. **Two response body shapes, not interchangeable** — the bare
   `{"message": …}` (`Api.msg`) and `{"success": false, "message": …}`
   (`Api.fail`). The bare form is what the auth gates, the profile form, the
   user-management mutations and the `parseId`/framework 400s return; the
   `success:false` form is what signup, login, password reset and admin user
   creation return. Same statuses can carry either shape, so tests assert exact
   bodies.
2. **Lenient request decoding** — a missing or unparsable body decodes to an
   empty request object; route-level validation is what produces the 400. That
   means handlers take `@RequestBody(required = false) byte[]` and decode via
   `Api.decode`, never a typed `@RequestBody` DTO (Spring would 400 on the
   malformed JSON itself, before validation could).
3. **`X-Renewed-Token` only on 2xx** — the renewed token rides on successful
   responses and never on errors, so a failed request can't slide a session. The
   filter applies it the moment the status is known, not after the body flushes.
4. **`JWT_SECRET` must be at least 32 bytes** — the app checks this while
   building `JwtService` and refuses to start on a shorter secret, because a
   short HMAC key is brute-forceable. This is stricter than the backend it
   replaced, which signed with a secret of any length, so a deployment carrying a
   short `JWT_SECRET` has to lengthen it. The dev fallback value also differs, so
   sessions minted on the fallback are not interchangeable between the two.
5. **The role gate lives in the controllers** — deliberately, because the
   original checked the role _before_ parsing the path id. Order is visible:
   `DELETE /api/users/abc` as a non-admin is a 403, not a 400.
6. **`created_at` serialises as an ISO-8601 UTC instant** — the admin user list
   carries `createdAt` as e.g. `2026-01-01T00:00:00Z`. Jackson's timestamp output
   is disabled, so an `Instant` renders the way the legacy backend's
   `DateTime<Utc>` did.
7. **The `"to"` column is quoted** — `to` is reserved in SQL, so `email_queue`
   queries write `"to"`.
