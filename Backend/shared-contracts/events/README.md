# Agent service events

The publisher uses the durable topic exchange `agent.events` by default. Routing keys are configurable with `app.events.routing-keys.*` and default to `agent.created`, `agent.updated`, `agent.deleted`, `agent.executed`, and `agent.execution.failed`. Queue names are consumer-owned; the service's documented default logical queue name is `agent.events`, configurable as `app.events.queue-name`.

Outbox delivery is at-least-once. Negative confirms, mandatory returns, connection errors, and confirmation timeouts are retried with exponential backoff until `m1.outbox.max-attempts` (10 by default), after which the row is `FAILED`. Admins can explicitly retry a failed row. `eventId` remains stable for every retry; consumers must deduplicate by `eventId`.

The canonical envelope keys are `eventId`, `eventType`, `producer`, and `occurredAt`. Temporary aliases `event`, `source`, and `timestamp` are emitted only while `m1.events.legacy-aliases=true` (default); agree with M7 before disabling/removing them. Payloads must not include input, output, context, or secrets.

Schemas: `agent-created.schema.json`, `agent-updated.schema.json`, `agent-deleted.schema.json`, `agent-executed.schema.json`, and `agent-execution-failed.schema.json`.
