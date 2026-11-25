# M1 - Agent Management

Spring Boot service for agent ownership, immutable versions, execution, history, and durable agent events.

## Run

Development (simulated identity; never evidence of security):

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:M1_DEMO_SEED = "true"
.\mvnw.cmd spring-boot:run
```

Production uses a stateless Keycloak JWT resource server. Configure `KEYCLOAK_ISSUER_URI` to the realm issuer and set provider keys in the host/container environment before starting the service. Keys are read from the environment for provider calls; never add a key to source files, application YAML, HTTP examples, logs, or container images.

| Variable | Default | Purpose |
|---|---|---|
| `OPENAI_API_KEY` | unset | OpenAI; required for OpenAI models |
| `GEMINI_API_KEY` | unset | Google Gemini OpenAI-compatible endpoint (catalog default: Gemini 3.6 Flash) |
| `GROQ_API_KEY` | unset | Groq OpenAI-compatible endpoint |
| `OPENROUTER_API_KEY` | unset | OpenRouter OpenAI-compatible endpoint |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/ai-lab` | JWT issuer |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `TOOLS_SERVICE_URL` | `http://localhost:8082` | M2 tool service |
| `EUREKA_ENABLED` | `false` | Enable Eureka registration |
| `M1_DEMO_SEED` | `false` | Seed three dev demo agents |
| `M1_CORS_ALLOWED_ORIGINS` | empty outside dev; Vite origin in dev | Comma-separated browser origins allowed to call `/api/**` |

Other useful settings include `m1.outbox.poll-interval-ms` (2000), `m1.outbox.max-attempts` (10), `m1.outbox.confirm-timeout-ms` (5000), `m1.events.legacy-aliases` (temporary `true`), `m1.executions.stale-after-minutes` (5), `m1.quota.user-max-agents` (5), `m1.llm.strict-model-check` (`true`), `m1.llm.check-max-per-hour` (20), and `m1.assist.max-per-hour` (10). `timeoutSeconds` is limited to 5–120 seconds. CORS is configured for `/api/**`; it exposes `X-Total-Count`.

### Provider keys in IntelliJ

Set keys only in the IntelliJ run configuration's **Environment variables** field: open **Run → Edit Configurations…**, select the M1 Spring Boot configuration, and enter only the provider keys you use (for example `GROQ_API_KEY=<your key>;GEMINI_API_KEY=<your key>`). Keep real values in your local IDE configuration or secret manager, not in this project, `.env` files, YAML, `requests.http`, or logs. Do not share or commit the run configuration. Remove a key to make that provider show as unavailable.

The non-secret `m1.llm.providers` catalogue is configured in `application.yml`: each provider defines its id, label, enabled flag, key-variable name, OpenAI-compatible base URL, tool support, and model list. `GET /api/llm/models` returns provider ids, availability and public model metadata only. A version stores both provider and model; provide both when creating a version if model ids collide. Omitted providers are inferred only when exactly one available provider offers that model. Supported catalogue ids are `openai`, `gemini`, `groq`, and `openrouter`; Gemini defaults to `gemini-3.6-flash`. Saved Gemini 2.0 Flash versions are routed to Gemini 3.6 Flash when the replacement is configured. This configuration and fake-server tests do not establish that a real provider account, key, model entitlement, or live endpoint works.

## Keycloak token

Import `Infrastructure/keycloak/realm-ai-lab.json` as described in `Infrastructure/keycloak/README.md`, and sign in once to change Alice's temporary password. For a local manual test only, exchange the new password (kept in an environment variable) using:

```powershell
curl.exe -X POST "http://localhost:8180/realms/ai-lab/protocol/openid-connect/token" `
  -H "Content-Type: application/x-www-form-urlencoded" `
  --data-urlencode "client_id=ai-lab-frontend" --data-urlencode "grant_type=password" `
  --data-urlencode "username=alice" --data-urlencode "password=$env:ALICE_PASSWORD"
```

The sample users are forced to change their temporary passwords. Real frontend authentication should use authorization code + PKCE, not password grant.

## Ownership and security

The JWT `sub` is the owner ID. A missing agent and another user's agent both return 404, including version and history routes. `GET /api/agents?all=true` and cross-agent history `all=true` expose all agents only to `ROLE_ADMIN`; for other callers `all` is ignored. `/api/admin/**` requires `ROLE_ADMIN`. The dev profile uses `X-Dev-User` and `X-Dev-Role` headers (defaults `dev-user` and `ADMIN`); these headers are ignored outside `dev`. Startup logs `SECURITY DISABLED / dev identity simulation active`.

## Rôles, quotas et privilèges

| Route group / operation | USER on own agent | USER on another user's agent | ADMIN on own agent | ADMIN on another user's agent |
|---|---|---|---|---|
| Read agent, versions, tunable parameters, executions, stats | Allowed | 404 | Allowed | Allowed (supervision only) |
| Update/delete agent, create/activate version, update configuration, execute | Allowed | 404 | Allowed | 403 `ADMIN_READ_ONLY` |
| `GET /api/agents` | Own agents | — | Own agents by default | All only with `all=true` (optional `ownerId`) |
| `/api/admin/**` moderation and audit routes | 403 | 403 | Allowed | Allowed |

`POST /api/agents` has a default limit of 5 agents per USER, including archived agents. Admins have no creation limit. Administrators may set a per-user override from 1 to 100; `null` restores the default. Deleting an agent releases a slot. `/api/me` reports the current role, effective quota, usage, and supported capabilities.

## Routes

Collections are plain JSON arrays. History arrays include `X-Total-Count`.

| Method and path | Success | Other contract statuses |
|---|---:|---|
| `POST /api/agents` | 201 | 400 |
| `GET /api/agents[?all=true]` | 200 | — |
| `GET /api/agents/{id}` | 200 | 404 |
| `PUT /api/agents/{id}` | 200 | 400, 404 |
| `DELETE /api/agents/{id}` | 204 | 404 |
| `POST /api/agents/{id}/versions` | 201 | 400, 404, 422 |
| `GET /api/agents/{id}/versions` | 200 | 404 |
| `GET /api/agents/{id}/versions/{vid}` | 200 | 404 |
| `PUT /api/agents/{id}/versions/{vid}/activate` | 200 | 404 |
| `PUT /api/agents/{id}/versions/{vid}/configuration` | 200 | 404, 422 |
| `GET /api/agents/{id}/versions/{vid}/tunable-parameters` | 200 | 404 |
| `POST /api/agents/{id}/execute` (optional per-run `overrides`) | 200 | 400, 404, 422, 502, 504 |
| `GET /api/agents/{id}/executions?status=&versionId=&page=0&size=20` | 200 | 400, 404 |
| `GET /api/agents/{id}/executions/{executionId}` | 200 | 404 |
| `GET /api/agents/{id}/stats?days=7` | 200 | 400, 404 |
| `GET /api/executions?agentId=&status=&page=0&size=20&all=` | 200 | 400, 404 |
| `GET /api/me` | 200 | 401 |
| `GET /api/llm/models` | 200 | 401 |
| `POST /api/agents/assist` | 200 | 400, 401, 422, 429, 502 |
| `GET /api/admin/users` | 200 | 401, 403 |
| `PUT /api/admin/users/{ownerId}/quota` | 200 | 400, 401, 403 |
| `POST /api/admin/agents/{id}/archive` | 204 | 400, 401, 403, 404 |
| `DELETE /api/admin/agents/{id}?reason=` | 204 | 400, 401, 403, 404 |
| `GET /api/admin/audit?page=0&size=20` | 200 | 400, 401, 403 |
| `POST /api/admin/llm/{provider}/check` | 200 | 401, 403, 422, 429 |
| `GET /api/admin/outbox` | 200 | 401, 403 |
| `POST /api/admin/outbox/{uuid}/retry` | 204 | 401, 403, 404, 409 |
| `GET /actuator/health` | 200 | — |

Deleting an agent deletes its stored execution history.

## Execution errors

`AGENT_QUOTA_EXCEEDED` is 403 and includes `limit` and `usage`; `ADMIN_READ_ONLY` is 403; `MODEL_NOT_AVAILABLE` and `MODEL_AMBIGUOUS` are 422. `RATE_LIMITED` is 429; `PROVIDER_NOT_CONFIGURED` and `ASSIST_INVALID_OUTPUT` are 502. Execution errors `PROVIDER_NOT_CONFIGURED`, `PROVIDER_AUTH_FAILED`, `PROVIDER_RATE_LIMITED`, `MODEL_NOT_AVAILABLE`, `PROVIDER_UNAVAILABLE`, `EMPTY_RESPONSE`, `TOOL_SERVICE_UNAVAILABLE` use 502; `TIMEOUT` and `TOOL_TIMEOUT` use 504; `TOOL_LIMIT_EXCEEDED` uses 422; unexpected errors use `INTERNAL_ERROR` (500). Execution failures return a structured `execution` object; unknown token metrics are `null`. Returned/stored error messages are sanitized. The provider check returns only the provider, recommended model, success flag, latency, and classified error code; its audit contains no response body or key.

The agent-assist endpoint accepts optional `provider` and `model` fields alongside `description` and `language`. The editor sends its currently selected provider/model so assistant requests use the same LLM as the agent configuration. If both selection fields are omitted, the service chooses its first available recommended provider/model for backward compatibility.

## Durable events

Agent changes, versions, and finalized executions insert an outbox row in the same transaction. The scheduled dispatcher publishes oldest-ready events with correlated publisher confirms and mandatory returns; failures retry with exponential backoff and stable event IDs (at-least-once delivery). Dispatch holds a pessimistic row lock through confirmation, so concurrent instances cannot publish the same row simultaneously. Consumers must deduplicate on `eventId`.

The `events` health contributor reports pending/failed counts; `DOWN` means failed events exist. Admins can inspect/retry failures at `/api/admin/outbox`. Payloads never contain user input, output, context, or credentials. Canonical keys are `eventType`, `producer`, and `occurredAt`. Legacy aliases `event`, `source`, and `timestamp` are temporary and controlled by `m1.events.legacy-aliases`; coordinate their removal with M7.

## Tests and OpenAPI export

Run the suite with `.\mvnw.cmd clean verify`. The real-provider smoke test remains excluded unless both `M1_LLM_SMOKE_OPENAI=true` and `OPENAI_API_KEY` are present. Normal tests use a local fake OpenAI-compatible server and test-only placeholder values.

To re-export the OpenAPI contract after starting the service:

```powershell
New-Item -ItemType Directory -Force ..\shared-contracts\openapi | Out-Null
Invoke-WebRequest http://localhost:8081/v3/api-docs -OutFile ..\shared-contracts\openapi\agent-service.json
$spec = Get-Content ..\shared-contracts\openapi\agent-service.json -Raw | ConvertFrom-Json
$required = @('/api/agents', '/api/agents/{id}', '/api/agents/{id}/versions',
  '/api/agents/{id}/execute', '/api/agents/{id}/executions',
  '/api/agents/{id}/executions/{executionId}', '/api/agents/{id}/stats',
  '/api/executions', '/api/me', '/api/llm/models', '/api/agents/assist',
  '/api/admin/users', '/api/admin/users/{ownerId}/quota', '/api/admin/agents/{id}/archive',
  '/api/admin/agents/{id}', '/api/admin/audit', '/api/admin/llm/{provider}/check', '/api/admin/outbox',
  '/api/admin/outbox/{id}/retry', '/actuator/health')
$required | ForEach-Object { if (-not $spec.paths.PSObject.Properties.Name.Contains($_)) { throw "Missing route: $_" } }
```

Review the generated JSON, run the path check above, then keep the exported file at `Backend/shared-contracts/openapi/agent-service.json`.

## Add a provider

`OpenAiCompatibleLlmGateway` routes requests for the configured OpenAI-compatible providers, while `ProviderRouter` selects the gateway from the version's provider id. Add provider metadata and models under `m1.llm.providers` in `application.yml`; keep only the environment-variable name there, never the key. Confirm the provider's endpoint compatibility and tool support before enabling it. The local fake-server tests verify request routing and secret handling, not live provider availability or account entitlements.
