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
Architecture Decision Records live in [`docs/adr/`](docs/adr/). Highlights:
- [ADR 0002: Cost-free by default](docs/adr/0002-cost-free-by-default.md): the AI sits behind a port with a mock and a Claude adapter, so the project runs at zero API cost unless a key is configured.

## Run locally
Requirements: Docker (with Compose) and Node 22. No API key needed: the app uses the free mock AI provider by default ([ADR 0002](docs/adr/0002-cost-free-by-default.md)).

```bash
cp .env.example .env
docker compose -f infra/docker-compose.yml up --build
```

The backend is up when http://localhost:8080/actuator/health returns `{"status":"UP"}`. For the frontend:

```bash
cd frontend && npm install && npm run dev
```

### Windows setup (WSL 2)
1. In PowerShell (admin): `wsl --install -d Ubuntu`, then restart and create your Linux user.
2. Install Docker **inside Ubuntu** (this avoids Docker Desktop DNS problems on some networks):
   ```bash
   sudo apt update && sudo apt install -y docker.io docker-compose-v2
   sudo usermod -aG docker $USER
   ```
3. In PowerShell: `wsl --shutdown`, reopen Ubuntu, check with `docker run hello-world`.
4. Clone the repo inside Linux (`~/code`, not `/mnt/c/...`, which is much slower) and run the commands above.

Troubleshooting:
- `WININET_E_NAME_NOT_RESOLVED` when installing Ubuntu: try `wsl --install -d Ubuntu --web-download`, turn off VPNs, or set your network DNS to `1.1.1.1` / `8.8.8.8`.
- `lookup auth.docker.io: no such host` with Docker Desktop: use Docker inside Ubuntu as in step 2.

## Roadmap
- **v0.1** — upload + text report
- **v0.2** — RAG citations, boxes on image, scores
- **v1.0** — async queue, history, compare, PDF export, deploy

## License
MIT
