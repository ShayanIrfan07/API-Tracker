# API Tracker

Realtime API health monitoring backend built with Java and Spring Boot. The service registers APIs, checks them on a schedule, evaluates UP/DOWN status, opens alerts, sends email, and creates Jira tickets.

[![CI](https://github.com/ShayanIrfan07/API-Tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/ShayanIrfan07/API-Tracker/actions/workflows/ci.yml)
[![CD](https://github.com/ShayanIrfan07/API-Tracker/actions/workflows/cd.yml/badge.svg)](https://github.com/ShayanIrfan07/API-Tracker/actions/workflows/cd.yml)

## Requirements

- Java 21+
- Maven 3.9+
- Spring Boot **4.1.0**
- Docker / Docker Compose
- PostgreSQL 16 (via Docker Compose)

## Start PostgreSQL

```powershell
docker compose up -d
```

If port `5432` is already in use:

```powershell
$env:POSTGRES_PORT = "5433"
docker compose up -d
$env:DB_URL = "jdbc:postgresql://localhost:5433/api_tracker"
```

If port `8080` is busy, set `$env:SERVER_PORT = "8081"` before starting the app.

## Run application

```powershell
mvn spring-boot:run
```

See `.env.example` for configuration. Do not commit real secrets.

### Docker image (CD)

On every push to `main`, GitHub Actions builds and publishes:

- Container image: `ghcr.io/shayanirfan07/api-tracker:latest` (also `sha-…` tags)
- JAR build artifact in the Actions run

Tagged releases (`v1.0.0`, etc.) also create a GitHub Release with the JAR attached.

Pull and run with Compose (app profile):

```powershell
docker pull ghcr.io/shayanirfan07/api-tracker:latest
docker compose --profile app up -d
```

If the package is private, authenticate first:

```powershell
echo $env:GITHUB_TOKEN | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

Workflows: [CI](https://github.com/ShayanIrfan07/API-Tracker/actions/workflows/ci.yml) · [CD](https://github.com/ShayanIrfan07/API-Tracker/actions/workflows/cd.yml)

## Dashboard (Phase 4)

Open in a browser:

- Default: [http://localhost:8080](http://localhost:8080)
- If you set `SERVER_PORT=8081`: [http://localhost:8081](http://localhost:8081)

Paste your `API_TRACKER_API_KEY` in the top bar (if configured), then use the dashboard to view status, open alerts, register APIs, run checks, and inspect history.

### Login (Phase 6)

Default credentials (override with env vars):

- Username: `admin`
- Password: `admin`

Click **Login** on the dashboard. The UI stores a JWT in session storage. API key remains available under **Advanced**.

`POST /api/v1/auth/login`

```json
{ "username": "admin", "password": "admin" }
```

Then call APIs with `Authorization: Bearer <accessToken>`.

## Health check

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/monitored-apis` | Create monitored API |
| GET | `/api/v1/monitored-apis` | List APIs |
| GET | `/api/v1/monitored-apis/{id}` | Get one API |
| PUT | `/api/v1/monitored-apis/{id}` | Update configuration |
| DELETE | `/api/v1/monitored-apis/{id}` | Disable API |
| POST | `/api/v1/monitored-apis/{id}/check-now` | Run immediate check |
| GET | `/api/v1/monitored-apis/{id}/checks` | Check history |
| GET | `/api/v1/alerts` | List alerts (`?status=OPEN\|RESOLVED`) |
| GET | `/api/v1/alerts/{id}` | Get alert detail |
| GET | `/api/v1/summary` | Fleet uptime/latency summary (`?hours=24`, max 168) |
| GET | `/api/v1/monitored-apis/{id}/summary` | Per-API uptime/latency summary |

### Create example

```powershell
$body = @{
  name = "Payments Health"
  baseUrl = "https://api.example.com"
  path = "/health"
  httpMethod = "GET"
  expectedStatusCode = 200
  timeoutMs = 3000
  intervalSeconds = 60
  failureThreshold = 3
  successThreshold = 2
  ownerEmail = "oncall@example.com"
  enabled = $true
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/monitored-apis `
  -ContentType "application/json" `
  -Headers @{ "X-API-Key" = $env:API_TRACKER_API_KEY } `
  -Body $body
```

## Phase 3 behavior

On transition to **DOWN**:
1. Open one alert per outage (deduped)
2. Create Jira issue (if enabled/configured) and store `jira_issue_key` on the alert
3. Email `ownerEmail` (if enabled/configured)

On transition to **UP** (from DOWN):
1. Resolve the open alert
2. Comment on the correlated Jira issue (does **not** auto-close)
3. Email recovery notice

If Jira/email are disabled, local alerts still work and notification attempts are logged.

### Email (local Mailpit)

```powershell
docker compose up -d mailpit
```

Defaults: `MAIL_HOST=localhost`, `MAIL_PORT=1025`, auth/starttls off.  
Open the inbox at [http://localhost:8025](http://localhost:8025). Set `ownerEmail` on each monitored API.

Emails do **not** go to Gmail while using Mailpit — only the Mailpit UI.

### Jira correlation

- Issue description and recovery comment include **Alert ID** and **API ID**
- Issue labels: `api-tracker`, `api-<uuid>`, `alert-<uuid>`
- Alert API returns `jiraIssueKey` + `jiraIssueUrl` (`{JIRA_BASE_URL}/browse/{key}`)
- Create/comment attempts are written to `notification_log` with channel `JIRA`
- If an open alert exists without a Jira key, the next DOWN transition retries create (no duplicate alert)

## Phase 5 hardening

- **DEGRADED** status when checks succeed but latency exceeds optional `latencyThresholdMs`
- Nightly purge of old `check_result` rows (`CHECK_RETENTION_DAYS`, default 30)
- Micrometer metrics: `api_tracker_checks_total`, `api_tracker_check_latency`, status transitions, alerts opened
- Metrics endpoint: `/actuator/metrics` (API-key protected when configured)
- `REQUIRE_API_KEY=true` blocks open access if the server key is blank
- **Rate limiting** (per client IP): API `120/min`, login `20/min`, check-now `30/min` (returns HTTP 429). Toggle with `RATE_LIMIT_ENABLED`
## Phase 7 uptime reporting

- Fleet summary: `GET /api/v1/summary?hours=24`
- Per-API summary: `GET /api/v1/monitored-apis/{id}/summary?hours=24`
- Window clamped to 1–168 hours
- Dashboard shows 24h uptime % and average latency (fleet + per API)
- Mail health indicator follows `MAIL_ENABLED` so unused SMTP does not mark `/actuator/health` DOWN

## Environment variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/api_tracker` |
| `DB_USERNAME` | DB user | `api_tracker` |
| `DB_PASSWORD` | DB password | `api_tracker` |
| `API_TRACKER_API_KEY` | Admin API key | empty (dev open access) |
| `REQUIRE_API_KEY` | Reject blank server API key | `false` |
| `ADMIN_USERNAME` | Dashboard JWT login user | `admin` |
| `ADMIN_PASSWORD` | Dashboard JWT login password | `admin` |
| `JWT_SECRET` | HMAC secret for JWT signing | change-me... |
| `JWT_EXPIRATION_MS` | Token lifetime | `86400000` |
| `RATE_LIMIT_ENABLED` | Enable API rate limiting | `true` |
| `RATE_LIMIT_API_PER_MIN` | General `/api/**` limit per IP | `120` |
| `RATE_LIMIT_LOGIN_PER_MIN` | Login attempts per IP | `20` |
| `RATE_LIMIT_CHECK_NOW_PER_MIN` | Manual check-now per IP | `30` |
| `CHECK_RETENTION_ENABLED` | Purge old check results | `true` |
| `CHECK_RETENTION_DAYS` | Retention window | `30` |
| `MAIL_ENABLED` | Enable email | `true` |
| `MAIL_FROM` | From address | `noreply@apitracker.local` |
| `MAIL_HOST` / `MAIL_PORT` | SMTP host/port | `localhost` / `1025` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials | empty (Mailpit) |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | SMTP auth flags | `false` / `false` |
| `JIRA_ENABLED` | Enable Jira | `false` |
| `JIRA_BASE_URL` | Jira site URL | empty |
| `JIRA_USERNAME` | Jira user/email | empty |
| `JIRA_API_TOKEN` | Jira API token | empty |
| `JIRA_PROJECT_KEY` | Project key | empty |

## Tests

```powershell
mvn test
```

## Security notes

- Dashboard UI (`/`) is public; REST APIs remain protected
- `/actuator/health` is public
- Admin APIs use `X-API-Key` auth
- Blank API key = local development convenience mode unless `REQUIRE_API_KEY=true`
