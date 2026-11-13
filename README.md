# AI Agent Evolution Lab

One-sentence pitch: platform to create, orchestrate, evaluate, optimize
and supervise AI agents.

## Problem & solution
 
**Problem.** In a company, several AI agents can coexist without clear coordination. Teams need to know how to configure them, make them collaborate, measure their quality, and detect unusual technical behaviour *before* a result is published to the user.
 
**Solution.** One platform where a user can:
 
- create and version AI agents (M1) and secure the tools they may use (M2);
- build multi-agent missions with an AI Router and a LangGraph workflow (M3);
- benchmark agent quality with reproducible test sets (M4);
- search better agent configurations with Optuna (M5);
- get an anomaly diagnostic before the final answer is shown (M6);
- keep a full technical audit trail of events (M7).
**Demo use case: customer complaint handling.** An agent analyses a complaint, another drafts a reply, and a Reviewer agent checks it. Before display, M6 checks the execution metrics, and M3 makes the final decision. M4 and M5 are separate activities and are not triggered for every complaint.

## Architecture
 
![Architecture](Documentation/Diagrams/Architecture%20globale.png)
 
**Stack:** Spring Boot microservices · React · REST / OpenFeign · RabbitMQ · Keycloak · Eureka · Config Server · API Gateway · Docker Compose
 
- **React** is the single web interface for users and administrators.
- **API Gateway** routes requests and checks the JWT issued by **Keycloak**.
- **Eureka** handles service discovery and **Config Server** centralizes configuration.
- Business calls between services use **REST / OpenFeign**.
- Events are published to **RabbitMQ** and consumed by **M7**, which stores them in **MongoDB**.
- Each service owns its database. No service reads another service's database.

### Service-to-service calls
 
| Caller → Callee | Purpose | Mode |
|---|---|---|
| M1 → M2 | Check access to an MCP tool, execute it | REST / OpenFeign |
| M3 → M1 | Agent catalogue, individual executions, result metrics | REST / OpenFeign |
| M3 → M6 | Pre-answer anomaly diagnostic (response in the same HTTP call) | REST / OpenFeign |
| M6 → M1 (optional) | Extra technical data, never an automatic re-run | REST / OpenFeign |
| M4 → M1 | Run agents on benchmark cases with temporary parameters | REST / OpenFeign |
| M5 → M4 | Request a score for a candidate configuration | REST / OpenFeign |
| M5 → M1 | Catalogue, versions, save a validated configuration | REST / OpenFeign |
| M1–M6 → RabbitMQ → M7 | Business and technical events | Asynchronous |
 

## Team & responsibilities
 
| Member | Service | Responsibility |
|---|---|---|
| Ouday Chebbi | M1: Agents | Create, configure, version and run agents | 
| Anaghim Dareaoui | M2: MCP & Security | Tools, permissions, access control and refusals |
| Souleima Belghith | M3: Missions | Missions, AI Router, LangGraph orchestration, final decision |
| Maram Ben Rhim | M4: Benchmark & Safety | Test sets, evaluation runs, reproducible scores |
| Roua Ouerfelli | M5: Optuna Optimization | Candidate configurations, studies, trials, best configuration |
| Youssef Souilem | M6: Anomaly Detection | Execution metrics analysis, severity, pre-answer diagnostic |
| Common (owner: [name]) | M7: Event Logging | RabbitMQ consumer, MongoDB storage, event history | 
 
## Services, ports and databases
| Service | Port | Database | Main data |
|---|---|---|---|
| `config-server` | 8888 | - | Centralized configuration |
| `eureka-server` | 8761 | - | Service registry |
| `api-gateway` | 8080 | - | Routing + JWT check |
| `m1-agent-service` | 8081 | H2 (file mode) | Agent, AgentVersion, executions |
| `m2-mcp-security-service` | 8082 | MySQL | Tool, Permission, AccessPolicy |
| `m3-mission-service` | 8083 | MySQL | Mission, MissionStep, MissionParticipant, MissionExecution |
| `m4-benchmark-service` | 8084 | MySQL | Benchmark, TestCase, BenchmarkRun, Score |
| `m5-optimization-service` | 8085 | PostgreSQL | OptimizationStudy, Trial, BestConfiguration |
| `m6-anomaly-service` | 8086 | H2 (file mode) | AnomalyCheck, Alert, ModelVersion |
| `m7-event-logging-service` | 8087 | MongoDB | EventLog (flexible payload + timestamp) |
| `frontend` (React) | 5173 | - | Web interface |

**Infrastructure containers (Docker Compose)**
 
| Component | Port(s) | Purpose |
|---|---|---|
| Keycloak | 8180 | Authentication, Users / Admin roles |
| RabbitMQ | 5672 (broker), 15672 (management UI) | Event transport to M7 |
| MySQL | 3306 | Databases of M2, M3, M4 |
| PostgreSQL | 5432 | Database of M5 |
| MongoDB | 27017 | Event store of M7 |
 


## Getting started
### Prerequisites
Java 17+, Maven, Node 18+, Docker, Python 3.11 (if used)
### Run
 
```bash
# 1. Clone
git clone https://github.com/<organization-or-user>/ai-agent-evolution-lab.git
cd ai-agent-evolution-lab
 
# 2. Configure environment (never commit .env)
cp .env.example .env
# edit .env: LLM API key, Keycloak admin, database passwords
 
# 3. Start infrastructure (Keycloak, RabbitMQ, MySQL, PostgreSQL, MongoDB)
docker compose up -d
 
# 4. Start the backend in this order
cd Backend/config-server && mvn spring-boot:run          # wait until it is up
cd Backend/eureka-server && mvn spring-boot:run
cd Backend/m1-agent-service && mvn spring-boot:run       # then M2 ... M7
cd Backend/api-gateway && mvn spring-boot:run
 
# 5. Start the frontend
cd Frontend && npm install && npm run dev
```
 
Start order: **config-server → eureka-server → M1 to M7 → api-gateway → frontend**.
## Demo scenario: customer complaint
 
1. **Login** through Keycloak as a User.
2. **M1:** create the agents `Analyzer`, `Writer` and `Reviewer` and pick an LLM for each.
3. **M2:** register a tool and set its permission. An unauthorized call is refused and logged (`TOOL_ACCESS_DENIED`).
4. **M3:** build a mission (manual or `AUTOMATIC_AI` selection) and submit the complaint. M3 calls M1 for each step.
5. **M6:** before the answer is shown, M3 sends the execution metrics to `POST /api/anomalies/detect`.
6. **M3 decision:** `NORMAL` or minor → `COMPLETED` with the final answer (and a warning if needed). `CRITICAL` → `FAILED` with a clear message. M6 timeout → fallback policy.
7. **M4:** run a benchmark on a test set (for example 70/100) and read the score report.
8. **M5:** launch an Optuna study that calls M4 for each trial and returns a better configuration (for example 85/100).
9. **M7:** open the event journal and filter by source (`AGENT_EXECUTED`, `MISSION_COMPLETED`, `ANOMALY_DETECTED`, ...).
Example M3 → M6 request:
 
```json
POST /api/anomalies/detect
{ "executionId": 45, "agentId": 12, "durationMs": 15000, "tokensUsed": 3500, "status": "COMPLETED" }
```
 
Example M6 → M3 response:
 
```json
{ "anomalyDetected": true, "severity": "CRITICAL", "reason": "EXECUTION_FAILURE", "blocking": true }
```
 
---
 
## Failure behaviour
 
| Failure | Expected behaviour |
|---|---|
| M1 unreachable | M3 shows a controlled error and never reports `COMPLETED` |
| M6 unreachable | M3 applies a timeout and the documented fallback policy |
| M7 unreachable | Business response does not depend on M7; events are queued and retried |
| M4 / M5 unavailable | Missions continue; evaluations and optimizations are deferred |
 
RabbitMQ is configured with acknowledgements, retries and persistent messages. Consumers are idempotent (by `eventId`).
 
---
 
## Repository structure
 
```
ai-agent-evolution-lab/
├── Backend/
│   ├── config-server/
│   ├── eureka-server/
│   ├── api-gateway/
│   ├── m1-agent-service/
│   ├── m2-mcp-security-service/
│   ├── m3-mission-service/
│   ├── m4-benchmark-service/
│   ├── m5-optimization-service/
│   ├── m6-anomaly-service/
│   └── m7-event-logging-service/
├── Frontend/
└── Documentation/
│   ├── Presentation/
│   ├── Diagrams/
│   ├── api-contracts.md
│   └── events-contract.md
├── .github/                  (PR template, issue templates, CODEOWNERS)
├── docker-compose.yml
├── .env.example
├── CONTRIBUTING.md
└── README.md
```
 
Each service folder contains its own `README.md` (endpoints, database, how to run, owner).
 
---
 
## Contributing
See CONTRIBUTING.md
