# M1 demonstration

1. Set `OPENAI_API_KEY` in the shell, then run `docker compose -f Infrastructure/docker-compose.yml up --build`; start M2 and RabbitMQ separately if they are available. The Keycloak sample realm imports on startup. For seeded dev data, run M1 with the `dev` profile and `M1_DEMO_SEED=true`; never use dev mode as evidence of security.
2. Sign in as a Keycloak user and use the React interface to create an agent. Set its name, description, and status.
3. Add a version with a French system prompt and configuration; set the model to `gpt-4o-mini`, choose a temperature, then activate the version.
4. Submit a realistic task from the interface. Show the output, token counts, and duration. If the provider omits token usage, the UI should show unknown rather than zero.
5. Open execution history for the agent, select the new row, and inspect its detail.
6. Run again with temporary `overrides`, for example `{"temperature":0.1}`. Show that the version's stored configuration is unchanged and the execution detail records both `overrides` and `appliedSettings`.
7. Demonstrate controlled errors in a non-production local environment: remove/replace the provider key for `PROVIDER_NOT_CONFIGURED` or `PROVIDER_AUTH_FAILED`, use a deliberately bounded slow provider call for `TIMEOUT`, and exercise an M2 tool returning 403 for a denied tool call. Do not expose keys or raw provider response bodies in screenshots/logs.
8. Show `/api/admin/outbox` as an admin. Once RabbitMQ/M7 is connected, demonstrate the `AGENT_EXECUTED` event and deduplication by `eventId`; while RabbitMQ is unavailable, show the event waiting in the outbox and later being retried.
