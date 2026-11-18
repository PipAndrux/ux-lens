# UX Lens

**Upload a screenshot, get a UX & accessibility audit.** UX Lens analyzes a web page, app screen or Figma frame against Nielsen's 10 usability heuristics and WCAG 2.2, marks every issue directly on the image, cites the exact rule, and suggests a fix.

> 🚧 Work in progress — see the [roadmap](#roadmap).

<!-- demo.gif goes here -->

**Live demo:** _coming soon_ · **Figma design:** _coming soon_

## Features
- [ ] Upload a screenshot and get a text report of the issues
- [ ] Issues highlighted with boxes on the image
- [ ] Scores per category: accessibility, visual hierarchy, consistency
- [ ] Compare two versions of the same screen (before / after)
- [ ] Export the report as PDF

## Tech stack
| Layer | Tech |
|---|---|
| Frontend | React, TypeScript, Vite |
| Backend | Spring Boot 4, Java 21 |
| AI | Claude (vision + structured output) via the Anthropic Java SDK, RAG over WCAG & Nielsen |
| Data | PostgreSQL + pgvector |
| Infra | Docker, GitHub Actions, Render/Railway, Neon, Vercel |

## Architecture
<!-- docs/architecture.svg goes here -->

## Technical decisions
Architecture Decision Records live in [`docs/adr/`](docs/adr/).

## Run locally
```bash
cp .env.example .env   # add your ANTHROPIC_API_KEY
docker compose -f infra/docker-compose.yml up --build
cd frontend && npm install && npm run dev
```

## Roadmap
- **v0.1** — upload + text report
- **v0.2** — RAG citations, boxes on image, scores
- **v1.0** — async queue, history, compare, PDF export, deploy

## License
MIT
