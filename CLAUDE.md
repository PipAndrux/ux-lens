# UX Lens: notes for Claude Code

UX Lens analyzes UI screenshots against Nielsen heuristics and WCAG 2.2 using Claude (vision) plus RAG over the guidelines.

## Layout
- `frontend/`: React + TypeScript + Vite
- `backend/`: Spring Boot 4, Java 21, Gradle (package `dev.uxlens`)
- `knowledge/`: WCAG / Nielsen sources for RAG ingestion
- `eval/`: golden screenshots + expected issues
- `infra/`: docker-compose (Postgres + pgvector)
- `docs/adr/`: Architecture Decision Records (copy `0000-template.md`)

## Commands
- Backend tests: `cd backend && ./gradlew test` (needs Docker running: tests use Testcontainers)
- Frontend: `cd frontend && npm run lint && npm run typecheck && npm run build`
- Local stack: `docker compose -f infra/docker-compose.yml up --build`

## Conventions
- Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, `test:`); one small PR per feature, squash-merge.
- Claude calls go through the official Anthropic Java SDK (`com.anthropic:anthropic-java`), model `claude-opus-5-5`, structured output for issues; bounding boxes are normalized 0–1.
- Never commit secrets; `ANTHROPIC_API_KEY` comes from the environment.
- Any significant technical decision gets an ADR.
- The UI itself must meet WCAG 2.2 AA.
