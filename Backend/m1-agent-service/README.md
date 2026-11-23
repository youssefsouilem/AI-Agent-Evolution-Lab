# M1 - Agent Management

Spring Boot service for agent ownership, immutable versions, execution, history, and durable agent events.

## Run

Development (simulated identity; never evidence of security):

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:OPENAI_API_KEY = "your-key"
$env:M1_DEMO_SEED = "true"
.\mvnw.cmd spring-boot:run
```

Production uses a stateless Keycloak JWT resource server. Configure `KEYCLOAK_ISSUER_URI` to the realm issuer and set the variables below before starting the container. `OPENAI_API_KEY` is injected at runtime only; do not put it in a file or image.

| Variable | Default | Purpose |
|---|---|---|
| `OPENAI_API_KEY` | `missing-key` | OpenAI key; missing/placeholder makes its models unavailable (`MODEL_NOT_AVAILABLE`); assist reports `PROVIDER_NOT_CONFIGURED` |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/ai-lab` | JWT issuer |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `TOOLS_SERVICE_URL` | `http://localhost:8082` | M2 tool service |
| `EUREKA_ENABLED` | `false` | Enable Eureka registration |
| `M1_DEMO_SEED` | `false` | Seed three dev demo agents |
| `M1_CORS_ALLOWED_ORIGINS` | empty | Comma-separated browser origins allowed to call `/api/**` |

Other useful settings include `m1.outbox.poll-interval-ms` (2000), `m1.outbox.max-attempts` (10), `m1.outbox.confirm-timeout-ms` (5000), `m1.events.legacy-aliases` (temporary `true`), `m1.executions.stale-after-minutes` (5), `m1.quota.user-max-agents` (5), `m1.llm.strict-model-check` (`true`), and `m1.assist.max-per-hour` (10). `timeoutSeconds` is limited to 5–120 seconds. CORS is configured for `/api/**`; it is disabled for browser origins by default until `M1_CORS_ALLOWED_ORIGINS` is set, and then exposes `X-Total-Count`. The non-secret `m1.llm.providers` catalogue is in `application.yml`: providers use `id`, `label`, `enabled`, `apiKeyEnv`, and `models`; models use `id`, `label`, and `recommended`. `OPENAI_API_KEY` is currently the only wired provider key. Anthropic and Gemini are placeholders; their key environment-variable names are `ANTHROPIC_API_KEY` and `GEMINI_API_KEY`. Each `apiKeyEnv` stores a variable name, never a key value.

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
| `POST /api/agents/assist` | 200 | 400, 401, 429, 502 |
| `GET /api/admin/users` | 200 | 401, 403 |
| `PUT /api/admin/users/{ownerId}/quota` | 200 | 400, 401, 403 |
| `POST /api/admin/agents/{id}/archive` | 204 | 400, 401, 403, 404 |
| `DELETE /api/admin/agents/{id}?reason=` | 204 | 400, 401, 403, 404 |
| `GET /api/admin/audit?page=0&size=20` | 200 | 400, 401, 403 |
| `GET /api/admin/outbox` | 200 | 401, 403 |
| `POST /api/admin/outbox/{uuid}/retry` | 204 | 401, 403, 404, 409 |
| `GET /actuator/health` | 200 | — |

Deleting an agent deletes its stored execution history.

## Execution errors

`AGENT_QUOTA_EXCEEDED` is 403 and includes `limit` and `usage`; `ADMIN_READ_ONLY` is 403; `MODEL_NOT_AVAILABLE` is 422. `RATE_LIMITED` is 429; `PROVIDER_NOT_CONFIGURED` and `ASSIST_INVALID_OUTPUT` are 502. Existing execution errors `PROVIDER_NOT_CONFIGURED`, `PROVIDER_AUTH_FAILED`, `PROVIDER_RATE_LIMITED`, `MODEL_NOT_AVAILABLE`, `PROVIDER_UNAVAILABLE`, `EMPTY_RESPONSE`, `TOOL_SERVICE_UNAVAILABLE` use 502; `TIMEOUT` and `TOOL_TIMEOUT` use 504; `TOOL_LIMIT_EXCEEDED` uses 422; unexpected errors use `INTERNAL_ERROR` (500). Execution failures return a structured `execution` object; unknown token metrics are `null`. Returned/stored error messages are sanitized.

## Durable events

Agent changes, versions, and finalized executions insert an outbox row in the same transaction. The scheduled dispatcher publishes oldest-ready events with correlated publisher confirms and mandatory returns; failures retry with exponential backoff and stable event IDs (at-least-once delivery). Dispatch holds a pessimistic row lock through confirmation, so concurrent instances cannot publish the same row simultaneously. Consumers must deduplicate on `eventId`.

The `events` health contributor reports pending/failed counts; `DOWN` means failed events exist. Admins can inspect/retry failures at `/api/admin/outbox`. Payloads never contain user input, output, context, or credentials. Canonical keys are `eventType`, `producer`, and `occurredAt`. Legacy aliases `event`, `source`, and `timestamp` are temporary and controlled by `m1.events.legacy-aliases`; coordinate their removal with M7.

## Tests and OpenAPI export

Run the suite with `.\mvnw.cmd clean verify`. The real-provider smoke test is opt-in via `OPENAI_API_KEY` and is skipped in ordinary builds.

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
  '/api/admin/agents/{id}', '/api/admin/audit', '/api/admin/outbox',
  '/api/admin/outbox/{id}/retry', '/actuator/health')
$required | ForEach-Object { if (-not $spec.paths.PSObject.Properties.Name.Contains($_)) { throw "Missing route: $_" } }
```

Review the generated JSON, run the path check above, then keep the exported file at `Backend/shared-contracts/openapi/agent-service.json`.

## Add a provider

`LlmGateway` is the extension point; `SpringAiLlmGateway` is the current Spring AI/OpenAI implementation. Before promising Gemini, Grok, or another provider, the team must decide whether it is in scope and verify its supported Spring AI integration and feature parity.
