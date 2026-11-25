# Shared infrastructure

This folder is shared by all team members. Service-specific Compose files are additive: do not replace the shared Compose file with a personal one.

- `docker-compose.yml`: existing M1 and Keycloak configuration, preserved unchanged.
- `compose.m3.yml`: M3 Spring Boot, its MySQL database, LangGraph and an optional local RabbitMQ broker.
- `.env.m3.example`: non-secret example for the M3 Compose variables; copy to `.env.m3` locally and edit the placeholders. Never commit `.env.m3`.
- `config-repository/m3-mission-service.properties`: M3 sample for the team's Config Server repository; its presence here does not start Config Server.
- `keycloak/`: existing shared realm and instructions, preserved unchanged.

## Start M3 only

From this folder (with Docker Desktop running):

```powershell
# First setup only: do not overwrite an existing private .env.m3.
Copy-Item .env.m3.example .env.m3
# Edit .env.m3: replace password and internal-token placeholders.
docker compose --env-file .env.m3 -f compose.m3.yml config --quiet
docker compose --env-file .env.m3 -f compose.m3.yml up --build -d
```

M3 API: http://localhost:8083. MySQL: localhost:3307. LangGraph is internal to Docker. Stop an existing local M3 on 8083 before starting this container. The Compose database is separate from the original local MySQL database; no data was migrated.

Inspect or stop without deleting the stored data:

```powershell
docker compose --env-file .env.m3 -f compose.m3.yml ps
docker compose --env-file .env.m3 -f compose.m3.yml logs -f m3 m3-orchestrator
docker compose --env-file .env.m3 -f compose.m3.yml down
```

## Combine with the existing team services

Use the same Compose project and file list consistently for start, logs and stop:

```powershell
docker compose --env-file .env.m3 -f docker-compose.yml -f compose.m3.yml config --quiet
docker compose --env-file .env.m3 -f docker-compose.yml -f compose.m3.yml up --build -d
```

Configure the existing M1/Keycloak services according to their own guide. M3 Compose inputs have an `M3_` prefix so enabling M3 discovery or changing its credentials does not change M1 settings. Inside the Java/Python containers the original application environment-variable names are retained.

M3 defaults to mock agents with external integrations disabled. For actual M1 integration configure both `M3_M1_ENABLED=true` and `M3_M1_MODE=real`, plus the URL/token required by the agreed contract. When using the combined network, `M3_M1_BASE_URL=http://agent-service:8081` reaches the existing M1 Compose service. This organization change does not certify API-contract compatibility or provide login credentials.

For optional local events, set `M3_EVENTS_ENABLED=true`, set the broker credentials and use `--profile events` before `up`. If the team already has a shared broker, leave the profile disabled and set `M3_RABBITMQ_HOST` to that broker with its credentials. Do not start a second broker on the same host ports. M7 exchange/binding contracts still need agreement.

No frontend container is added: the shared Frontend has no Dockerfile. Start the team's frontend separately; no frontend files were replaced during the M3 migration.

## Adding another member's work

Add `compose.mX.yml` with unique service and volume names, `.env.mX.example` with example values, and a service-specific configuration under `config-repository/` when needed. Document the run command and required ports here. Reuse shared Keycloak, discovery, Config Server and message broker rather than overwriting them or silently creating conflicting copies. No Eureka or Config Server implementation is added by this migration.

Docker is not installed on the migration workstation: the YAML and build paths are checked statically, but images and containers have not been built or started here.
