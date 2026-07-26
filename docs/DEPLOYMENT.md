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

## 7. Render deployment checklist (prepared only — deployment NOT started)

Chosen host: **Render**. This section is preparation/documentation only; no Render resources have been created and no deploy has been triggered.

### 7.1 Render web service configuration

- [ ] Create a new **Web Service** on Render, connected to this repo/branch (`release/v3.0.0`, tag `v3.0.3`)
- [ ] Runtime: **Docker** (use the repo's existing `Dockerfile` — multi-stage `eclipse-temurin:21-jdk-alpine` build → `21-jre-alpine` runtime, already non-root); do not use Render's native Java buildpack, since the Dockerfile is already correct and tested
- [ ] Region: pick nearest to expected users (affects latency to the separate Render Postgres instance too — keep both in the same region)
- [ ] Health check path: none configured today (`Dockerfile` has no `HEALTHCHECK`); Render's own HTTP health check can point at `/` or a real health endpoint if one exists — confirm before relying on it for zero-downtime deploys
- [ ] Instance type/plan: see §7.9 (memory) before picking — CoreNLP's pipeline load is the binding constraint, not CPU

### 7.2 PostgreSQL setup

- [ ] Create a Render **PostgreSQL** managed instance (separate resource from the web service)
- [ ] Note Render's internal connection string is `postgres://user:pass@host/db` — must be converted to JDBC form `jdbc:postgresql://host:5432/db` per the existing `DATABASE_URL` clarification in §2 above; do not paste Render's URL directly into `DATABASE_URL`
- [ ] Use Render's **internal** database URL (private network) for the web service, not the external one, to avoid extra latency/egress and to keep the DB off the public internet
- [ ] Confirm Postgres version — repo's local reference (`docker-compose.yml`, and the native Postgres this session verified migrations against) is v16; Render offers specific major versions, pick 16 or newer for parity

### 7.3 Environment variables (Render dashboard → Environment)

| Variable | Value | Notes |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://<render-internal-host>/<db>` | converted from Render's native URI, see §7.2 |
| `DATABASE_USERNAME` | from Render Postgres credentials | |
| `DATABASE_PASSWORD` | from Render Postgres credentials | mark as a Render "secret" env var, not plain |
| `OPENAI_API_KEY` | real key, or omit for Ollama/degraded fallback | see §3 and §7.8 |
| `SESSION_COOKIE_SECURE` | leave **unset** (defaults to `true`) | do NOT copy `docker-compose.yml`'s `"false"` dev value in here — see §0 |
| `SESSION_TIMEOUT` | optional, default `30m` | |
| `JAVA_TOOL_OPTIONS` / `JAVA_OPTS` | e.g. `-Xmx<N>m` | required to bound heap explicitly on Render's fixed-memory instances — see §7.9 |
| `SPRING_PROFILES_ACTIVE` | leave **unset** | must NOT be `dev` in production — see §7.5 |

### 7.4 Dockerfile verification

- [x] Reviewed this session (see §0's Dockerfile review): multi-stage build, non-root `app` user, exposes 8080 — no changes needed for Render, which auto-detects `EXPOSE 8080`
- [ ] Confirm Render's build machine has enough memory/time to run `./mvnw -B clean package` inside the build stage (Maven + CoreNLP dependency resolution/download can be slow on first build — Render's build timeout applies)
- [ ] No `HEALTHCHECK` instruction exists — decide whether to add one or rely solely on Render's platform-level health check

### 7.5 Spring production profile

- [ ] Confirm `SPRING_PROFILES_ACTIVE` is unset on the Render service (uses default `application.properties` → Postgres, `mock-enabled=false`, Swagger disabled, `ddl-auto=validate`) — do not set it to `dev`
- [ ] No code changes required here; this is purely a Render dashboard configuration check

### 7.6 Flyway migrations

- [ ] On first boot, Flyway will run `common/` + `postgresql/` migrations (V1–V8) automatically against the fresh Render Postgres instance (`spring.flyway.enabled=true`, `baseline-on-migrate=true`, `baseline-version=0`) — this matches exactly what was verified against real PostgreSQL 16 this session (see §0), so no additional migration risk is expected
- [ ] Watch the first-boot Render logs for Flyway output to confirm all 8 migrations apply cleanly before considering the deploy successful

### 7.7 HTTPS / session cookie verification

- [ ] Render terminates TLS at its edge and serves all web services over HTTPS by default — `server.servlet.session.cookie.secure=true` (the production default) will work out of the box; no extra config needed
- [ ] After first deploy, verify in browser dev tools that the session cookie has the `Secure` flag set and login/session persistence works over the `https://*.onrender.com` URL

### 7.8 OpenAI optional fallback

- [ ] Decide before going live: set a real `OPENAI_API_KEY`, or accept that `DiagramGenerationService` silently falls back to the rule-based generator when the key is missing/invalid (see §0's OpenAI fallback behavior) — this is a product decision, not a technical blocker either way
- [ ] If accepting the fallback, consider whether to monitor/alert on fallback rate (not currently instrumented)

### 7.9 Memory considerations — Spring Boot + Stanford CoreNLP

**This is the most significant risk for a Render deploy and needs a plan before picking an instance size.**

- `NaturalLanguageParser` (`src/main/java/.../service/generation/parser/NaturalLanguageParser.java`) eagerly loads a Stanford CoreNLP pipeline in `@PostConstruct` with annotators `tokenize,ssplit,pos,lemma,ner,depparse` — this loads the POS tagger, NER models, and a full dependency parser into memory **at application startup**, before the app can serve any request.
- The `stanford-corenlp:4.5.7:models` classifier dependency in `pom.xml` bundles large pretrained models; combined with the loaded pipeline objects, this pipeline alone typically needs on the order of **1–1.5+ GB of heap** in addition to the JVM's own baseline and Spring Boot's footprint. This is a well-known characteristic of CoreNLP with `ner`+`depparse` enabled, not specific to this app.
- Render's smallest paid web service plans (e.g. Starter, 512MB) are very unlikely to be sufficient — startup will likely OOM-kill the container during `@PostConstruct` before the app finishes booting. This should be verified empirically (deploy attempt with logs watched) rather than assumed, but budget for at least a **Standard-tier plan (2GB RAM) or higher** as the starting point.
- Mitigations to consider (all deferred — no code changes made, per "do not change business logic"):
  - Set an explicit `-Xmx` via `JAVA_TOOL_OPTIONS` sized to leave headroom below Render's hard memory limit (JVM getting OOM-killed by the OS is worse than a controlled `OutOfMemoryError` with a heap dump).
  - If cost is a concern, evaluate whether `depparse` (the heaviest annotator) is actually required for the app's classification/generation quality, or whether it could be made lazy/optional — this would be a code change and is out of scope for this deployment-prep pass.
  - Confirm Render's build stage (separate machine/limits from the runtime instance) also has enough memory to compile and package the CoreNLP-dependent JAR.

## 8. Post-launch

- [ ] Confirm live URL is reachable and add it to `README.md`
- [ ] Add live URL to CV / LinkedIn project entry
