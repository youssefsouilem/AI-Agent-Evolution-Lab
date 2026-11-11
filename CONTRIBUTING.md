# Contributing guide

## Ownership
Each member owns one service folder (see CODEOWNERS). Do not modify
another member's service without telling them. Changes to shared
contracts need a PR reviewed by everyone concerned.

## Branching
- main: always runs, protected, no direct push
- develop: integration branch (optional)
- feature branches: feat/m3-mission-crud, fix/m1-timeout-handling,
  docs/api-contracts

## Workflow
1. git checkout develop && git pull
2. git checkout -b feat/m3-mission-crud
3. Commit small and often
4. git push -u origin feat/m3-mission-crud
5. Open a Pull Request, with at least 1 reviewer
6. Merge after approval; delete the branch

## Commit messages (Conventional Commits)
type(scope): short description
Types: feat, fix, docs, refactor, test, chore
Examples:
- feat(m3): add mission CRUD endpoints
- fix(m6): return 503 when model is not loaded
- docs(api): update M3 to M6 anomaly contract

## Code conventions
- Java: package com.ailab.<service>, controller/service/repository/dto/entity
- REST: /api/<resource>, correct HTTP codes (201, 204, 404, 503)
- Never commit secrets; use .env (see .env.example)
- Each service starts alone with its README instructions

## Changing an API or event
1. Edit Documentation/api-contracts.md or events-contract.md
2. Tag the affected members in the PR
3. Merge the contract change before the code that depends on it

## Definition of done for a PR
- [ ] Service builds and starts
- [ ] Endpoints tested (Postman or unit tests)
- [ ] README/contracts updated if needed
- [ ] No secrets, no target/ or node_modules/