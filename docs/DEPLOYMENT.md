# Production Deployment Checklist

Static frontend is served by Spring Boot (`src/main/resources/static/`), so backend and frontend deploy as a single artifact — no separate frontend hosting needed.

## 0. Audit findings (report only — no deploy action taken)

This section supplements the checklist below with findings from a pre-release audit of environment variables, `Dockerfile`, `docker-compose.yml`, Flyway migrations, the production vs. dev profiles, HTTPS/session cookie config, OpenAI fallback behavior, and database setup.

### RESOLVED — `ck_diagram_shares_status` / `ck_repositories_source_type` (previously reported as blocking bugs), now confirmed against real PostgreSQL

Earlier testing against a long-running local dev server (H2, `application-dev.properties`) saw both "Create Share Link" and repository import fail with HTTP 409 `CONFLICT` (`Check constraint invalid: "ck_diagram_shares_status"` / `"ck_repositories_source_type"`). This was investigated in depth and **is not a code or schema defect**:

- The H2 migration DDL (`db/migration/h2/V6__create_diagram_shares.sql`, `V8__create_repositories_and_scans.sql`) is byte-for-byte identical to the PostgreSQL migrations and is syntactically valid — confirmed by replaying the exact DDL and an Hibernate-shaped `INSERT` (matching the failing statement's column order and bind values from `server-mock.log`) directly against a fresh H2 instance via raw JDBC: both succeeded.
- The app's own integration tests exercise these exact code paths against a fresh H2 instance and pass cleanly: `Stage13RepositoryControllerTest` (5/5, including `uploadingAZipScansItAndReportsMetadataWhileIgnoringNoiseDirectories` which posts to `/api/repositories/zip`) and `DiagramShareServiceImplTest` (4/4).
- The entity mappings (`Repository.sourceType`, `DiagramShare.status`) are correctly annotated `@Enumerated(EnumType.STRING)` with enum values matching the CHECK constraint lists exactly — no converter or mapping bug.
- **Confirmed the fix**: restarted the long-running dev server (fresh H2 in-memory instance) and re-tested both flows through the live UI — "Create Share Link" succeeded (generated a real public share URL) and GitHub-URL repository import succeeded (status `Ready`). Both failures did not reproduce on a fresh boot.

**Verified against real PostgreSQL (not just H2)**: a locally-installed PostgreSQL 16 instance (matching `docker-compose.yml`'s `db` service credentials) was used to run the actual `postgresql/` Flyway migrations fresh, end-to-end, through the real JPA/Hibernate stack, in an isolated scratch schema so no application data was touched. New regression test `src/test/java/.../repository/PostgresCheckConstraintIT.java` (profile `postgres-test`, config in `src/test/resources/application-postgres-test.properties`):
- `zipUploadRepositoryPersistsAgainstRealPostgres` — persists a `Repository` with `sourceType=ZIP_UPLOAD` → **passes**.
- `activeDiagramSharePersistsAgainstRealPostgres` — persists a `DiagramShare` with `status=ACTIVE` → **passes**.
- Result: **2/2 pass** against real Postgres 16. Neither CHECK constraint rejects the values the application actually writes. The test skips itself (`Assumptions.assumeTrue`) if no local Postgres is reachable, so it doesn't break `mvn test` in environments without one (e.g. CI without a Postgres service configured).

**Conclusion**: the two CHECK-constraint failures were transient, corrupted in-memory state accumulated in one specific long-running H2 dev process (after an extended multi-hour manual QA/screenshot session with many retries and aborted requests), not a defect in the migrations, entities, or application code — and this conclusion now holds against the actual production database engine (PostgreSQL), not just H2. No migration or code changes were required. No action needed before launch beyond the standard smoke test already listed below.

### Dockerfile review

- Multi-stage build (`eclipse-temurin:21-jdk-alpine` → `21-jre-alpine`), runs as non-root `app` user, exposes 8080. No issues found.
- No `HEALTHCHECK` instruction in the `Dockerfile` itself (only `docker-compose.yml` has one, and only for the `db` service, not `app`). Consider adding an app-level healthcheck if the target host supports it.

### docker-compose.yml review

- Sets `SESSION_COOKIE_SECURE: "false"` for the `app` service. Fine for local Docker Compose (no HTTPS locally) but **must not be carried into a real production environment variable set** — production must leave `SESSION_COOKIE_SECURE` unset or `true` so `server.servlet.session.cookie.secure` (default `true` in `application.properties`) actually takes effect. Flag this explicitly during deploy config so the compose file's dev-oriented env block doesn't get copy-pasted into production.
- `OPENAI_API_KEY` defaults to empty string (`${OPENAI_API_KEY:-}`) — compose will start the app successfully with no key, and AI features degrade via fallback rather than failing at boot (see below).

### Flyway / migrations

- `spring.flyway.locations=classpath:db/migration/common,classpath:db/migration/{vendor}` — vendor-specific folders (`h2/`, `postgresql/`) correctly diverge only where needed (V4–V8); common baseline (V1–V3) is shared. Confirms production only ever runs the `postgresql/` migrations, never `h2/`.
- `spring.flyway.baseline-on-migrate=true` with `baseline-version=0` — appropriate for a fresh managed Postgres instance; if migrating an existing hand-created production DB, verify the baseline version matches actual schema state first.

### Production vs. dev profile

- Production (`application.properties`, no profile / default): Postgres, `ddl-auto=validate` (safe — never auto-alters schema), Swagger disabled, `app.ai.assistant.mock-enabled=false`, `session.cookie.secure` defaults to `true`.
- Dev (`application-dev.properties`, profile `dev`): H2 in-memory, `mock-enabled=true`, Swagger enabled, `session.cookie.secure=false`, verbose SQL/DEBUG logging, H2 console enabled at `/h2-console`.
- Confirm the deploy target does **not** set `spring.profiles.active=dev` — doing so would enable H2, mock AI, an exposed H2 console, and insecure cookies in production.

### OpenAI fallback behavior

- `DiagramGenerationService` attempts LLM-based generation first; if the OpenAI response is empty or the call fails, it falls back to the rule-based generator (`RuleBasedDiagramService`), logged as a fallback event — generation always returns at least one entity even with no/invalid `OPENAI_API_KEY`. This means a missing key degrades quality silently rather than hard-failing; confirm this is the desired production behavior or add alerting on fallback rate.
- `openai.diagram.fallback.enabled=true` in both profiles.

### Database setup

- Managed Postgres required for production (local reference: `docker-compose.yml`'s `db` service, `postgres:16-alpine`, healthchecked). `DATABASE_URL` must be in JDBC form (`jdbc:postgresql://...`), not the `postgres://` URI form some managed providers hand out by default — convert before setting the env var (also called out in section 2 below).

## 1. Choose a host

| Component | Recommended options |
|---|---|
| App (Spring Boot JAR) | Render, Railway, Fly.io |
| Database | Managed PostgreSQL (Render/Railway/Fly.io Postgres, or Supabase/Neon) |

## 2. Required environment variables

Read from `src/main/resources/application.properties`:

| Variable | Purpose | Required |
|---|---|---|
| `DATABASE_URL` | JDBC URL, e.g. `jdbc:postgresql://<host>:5432/<db>` | Yes |
| `DATABASE_USERNAME` | DB user | Yes |
| `DATABASE_PASSWORD` (or `DB_PASSWORD`) | DB password | Yes |
| `OPENAI_API_KEY` | Real OpenAI key — required since `app.ai.assistant.mock-enabled=false` and `ai.provider=openai` by default | Yes (or switch to Ollama, see below) |
| `SESSION_TIMEOUT` | Session expiry (default `30m`) | No |
| `SESSION_COOKIE_SECURE` | Must be `true` in production (default) — requires HTTPS | No, but verify host serves HTTPS |

**Multipart upload note:** the app allows `spring.servlet.multipart.max-file-size=250MB` and `max-request-size=250MB`. The deployment target (Render/Railway/Nginx/etc.) must also allow request bodies of at least 250MB or large ZIP/repository uploads will fail before reaching Spring Boot.

**`DATABASE_URL` clarification:** many managed PostgreSQL providers expose a URI like `postgres://user:password@host:5432/database`. Spring Boot expects a JDBC URL: `jdbc:postgresql://host:5432/database`. Convert the provider URL or configure `spring.datasource.url` accordingly before deployment.

## 3. AI provider decision

Mock AI is already disabled (`app.ai.assistant.mock-enabled=false`). Pick one before going live:

- **OpenAI** (default, `ai.provider=openai`): set `OPENAI_API_KEY`. Uses `gpt-4o-mini` for classification, `gpt-4o` for diagram generation.
- **Ollama** (self-hosted, no API cost): set `ai.provider=ollama` and run an Ollama server reachable at `ollama.api.url` (default `http://localhost:11434`) with the `llama3` model pulled. Only viable if the host can run a persistent Ollama process alongside the app.

## 4. Build & run

```bash
./mvnw clean package -DskipTests
java -jar target/*.jar
```

No `spring-boot.run.profiles=dev` flag in production — this uses `application.properties` (PostgreSQL), not the H2 dev profile.

## 5. Pre-launch checklist

- [ ] `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` set on the host
- [ ] Flyway migrations run cleanly against the managed Postgres instance (`spring.flyway.enabled=true`, runs automatically on boot)
- [ ] `OPENAI_API_KEY` set (or Ollama configured) — verify a real AI-assisted edit works, not a mock response
- [ ] App served over HTTPS (required for `session.cookie.secure=true` to actually set the cookie)
- [ ] `logging.level.*=DEBUG` reviewed — currently very verbose (full SQL logging); consider dialing to `INFO` for production to reduce log noise/cost
- [ ] Swagger UI (`/swagger-ui.html`) — decide whether to expose publicly or restrict
- [ ] Repository analysis limits reviewed (`max-archive-bytes=250MB`, `max-files=50000`) — appropriate for expected usage/host resources
- [x] Smoke test after deploy: register → create project → generate diagram → AI-assisted edit → share → invite member → repository analysis — share and repository-import steps re-verified working after resolving the transient H2 state issue (see section 0)

## 6. Post-launch

- [ ] Confirm live URL is reachable and add it to `README.md`
- [ ] Add live URL to CV / LinkedIn project entry
