# Production Deployment Checklist

Static frontend is served by Spring Boot (`src/main/resources/static/`), so backend and frontend deploy as a single artifact — no separate frontend hosting needed.

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
- [ ] Smoke test after deploy: register → create project → generate diagram → AI-assisted edit → share → invite member → repository analysis

## 6. Post-launch

- [ ] Confirm live URL is reachable and add it to `README.md`
- [ ] Add live URL to CV / LinkedIn project entry
