# M3 in the shared repository

## Layout

```text
Backend/
  m3-mission-service/                 # Java project already copied by Souleima
  ai-services/
    langgraph-orchestrator/           # M3 Python code, lockfile, tests, Dockerfile
Infrastructure/
  docker-compose.yml                 # Existing M1 / Keycloak, unchanged
  compose.m3.yml                     # Additive M3 services
  .env.m3.example                    # Example only; private values go in .env.m3
  config-repository/
    m3-mission-service.properties    # Config Server sample
  README.md
 docs/
  m3/README.md                        # This guide
```

The old project has duplicate/placeholder top-level directories. The populated Python source used by M3 is `Backend/ai-services/langgraph-orchestrator`; that is the source copied here. No duplicate root-level AI-services or infrastructure tree was created. Future AI services belong alongside this engine in their own folders.

## Run locally without Docker

1. Open `Backend/m3-mission-service` in IntelliJ and the Python engine in your editor.
2. Configure your own `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` for M3. The original private database configuration was not copied.
3. From `Backend/ai-services/langgraph-orchestrator`, create a fresh Python 3.12 virtual environment:

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.lock.txt
$env:M1_MODE='mock'
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8093
```

4. In another terminal, from `Backend/m3-mission-service`, with Maven installed:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Alternatively select the `demo` profile in IntelliJ. Demo disables external discovery/config/security/integrations and runs with simulated agents. Keep any original-project process on 8083/8093 stopped before running the clone. Health: http://localhost:8083/actuator/health and http://localhost:8093/health.

5. For the shared frontend, follow its own README. This migration does not replace it with the separate M3 frontend or merge React routes. Whether its mission screens are integrated must be checked separately.

## Docker and integration

Use [the shared infrastructure guide](../../Infrastructure/README.md) and its explicit `-f compose.m3.yml` commands. Older service documents mentioning a standalone `Infrastructure/docker-compose.yml` with frontend refer to the original personal layout; the shared guide takes precedence for this clone.

The Config Server sample is [here](../../Infrastructure/config-repository/m3-mission-service.properties). The sample is not automatically served until the team's Config Server uses that repository/search path. Eureka/Config servers remain team-owned.

## Existing M3 details

These remain with the Spring Boot service; no duplicate copies are maintained:

- [Execution design](../../Backend/m3-mission-service/EXECUTIONS.md)
- [M6 contract preparation](../../Backend/m3-mission-service/INTEGRATION-M6.md)
- [Local demonstration scenarios](../../Backend/m3-mission-service/DEMONSTRATION.md)
- [Previous audit results, 24 November 2026](../../Backend/m3-mission-service/BILAN_VERIFICATION_M3.md) — historical results, not a claim of a fresh audit of this clone.
- [Python setup and contracts](../../Backend/ai-services/langgraph-orchestrator/README.md)

## Migration scope

Copied M3 Python source/tests/build inputs; added M3-specific Compose, example environment and Config Server sample; added shared folder guides and ignored private M3 environment files, Python test cache and SQLite state.

Existing Java files, other microservices, shared frontend, Keycloak realm and existing Compose file were preserved. No secrets, database contents, checkpoints, virtual environments or build outputs were copied. No services were started, database queries run, Git commits made or changes pushed by this migration.
