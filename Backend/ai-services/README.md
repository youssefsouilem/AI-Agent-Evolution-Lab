# Shared AI services

Place each independently runnable AI service in its own subfolder. This is the single AI-services location in this repository; do not create a second root-level `ai-services` folder.

| Folder | Owner | Runtime / port |
| --- | --- | --- |
| `langgraph-orchestrator/` | M3 / Souleima | Python FastAPI + LangGraph, 8093 locally; internal in Compose |

Each teammate can add their own named folder without changing another service. Include a README, pinned dependencies, tests and a Dockerfile when applicable. Keep secrets, virtual environments, generated models and runtime databases out of Git. Do not share a Python virtual environment between services.

M3 setup: [engine README](langgraph-orchestrator/README.md) and [M3 integration guide](../../docs/m3/README.md).
