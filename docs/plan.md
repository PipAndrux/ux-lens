# UX Lens: step-by-step project plan

## Context
UX Lens is a portfolio project. The user uploads a screenshot (a web page, an app screen or a Figma frame). The AI checks it against Nielsen's 10 heuristics and WCAG 2.2, then returns a report. The report marks each issue with a box on the image, cites the exact rule, and suggests a fix. The project should show UX/UI, React, Spring Boot and software-architecture skills, and a recruiter should understand it in 10 seconds.

Decisions already made: **Claude** (official Anthropic Java SDK), a **monorepo**, and deploy on **Render/Railway (backend) + Neon (Postgres + pgvector) + Vercel (frontend)**.

**Budget: €0 beyond the Claude Pro subscription** (see ADR 0002). The AI sits behind an `AnalysisProvider` port: a mock adapter is the default, and the Claude adapter only activates when an API key is configured. The public deployment runs in demo mode with pre-computed sample analyses.

Target time: about 6 weeks part-time (roughly 10–12 hours a week).

---

## 1. Architecture (target)

```
React + TS (Vercel) ──REST/SSE──► Spring Boot API (Render/Railway, Docker)
                                    │  ├─ AnalysisController  (upload, status, report, compare, pdf)
                                    │  ├─ JobWorker  (Postgres-backed queue, SELECT … FOR UPDATE SKIP LOCKED)
                                    │  ├─ RagService (pgvector similarity search over guideline chunks)
                                    │  ├─ AnalysisProvider port → Mock (default) | Claude (Anthropic Java SDK)
                                    │  └─ ReportService / PdfService
                                    ▼
                         Postgres + pgvector (Neon)     Object storage for images (Postgres bytea → later S3/R2)
```

Key technical decisions. Each one gets its own ADR in `docs/adr/`:
1. **The queue lives in Postgres** (a jobs table plus `SKIP LOCKED`) instead of RabbitMQ or Kafka. There is no extra infrastructure or cost, and it is plenty at this scale. A port interface lets a real broker replace it later.
2. **RAG over guidelines**: WCAG 2.2 success criteria and Nielsen's heuristics (with explanations) are split into chunks, one chunk per criterion. A first cheap Claude pass describes the UI's elements; those descriptions are embedded, the top-k criteria are retrieved and injected into the analysis prompt, and the model must cite `criterion_id`. Then the backend checks each cited ID against the knowledge base. This gives fewer made-up rule numbers and more precise citations.
3. **Embeddings run locally** (an ONNX model such as all-MiniLM-L6-v2 via Spring AI `TransformersEmbeddingModel`, or LangChain4j in-process). Anthropic has no embeddings endpoint, and running the model locally costs nothing. Spring AI `PgVectorStore` stores the vectors.
4. **Structured output**: Claude returns JSON that must match a schema (`output_config.format`): issues[], with `bbox` as **normalized 0–1 coordinates**, `severity`, `criterion_id`, `category`, `suggestion`, and `scores` for each category. The backend resizes the image to a fixed maximum size before sending it, so the boxes line up reliably.
5. **API cost control**:
   - Results are cached by the image's SHA-256 hash.
   - Prompt caching on the system prompt and guideline chunks.
   - Effort is set per route.
   - Rate limiting per IP (Bucket4j).
   - A daily budget guard.
   - A "demo mode" with sample analyses already computed.
   - Every call logs `usage` (tokens and cost) to a table, which feeds the "Cost" section of the README.
6. **Models**: `claude-opus-5-5` for the analysis (adaptive thinking is always on; set `effort` explicitly, starting at `medium`), with the server-side refusal fallback turned on. A cheap "quick scan" tier using `claude-haiku-5-5` is optional and gets an ADR, measured with the eval set.

---

## 2. Repository and GitHub workflow

**Repo `ux-lens` (public)**
```
ux-lens/
├─ frontend/        React + TS + Vite
├─ backend/         Spring Boot 3 (Java 21, Gradle or Maven)
├─ knowledge/       WCAG/Nielsen source files + ingestion script
├─ eval/            golden screenshots + expected issues + eval runner
├─ infra/           docker-compose.yml, Dockerfiles, render.yaml
├─ docs/            architecture diagram, adr/, screenshots, demo.gif
├─ .github/         workflows/, ISSUE_TEMPLATE/, pull_request_template.md, dependabot.yml
├─ CLAUDE.md        project conventions for Claude Code
└─ README.md
```

**How to work on GitHub (even solo, it shows a professional workflow):**
- Protect `main`: changes go in only through a PR, and CI must pass.
- Branches: `feat/upload-endpoint`, `fix/bbox-scaling`, `docs/adr-queue`.
- **Conventional Commits** (`feat:`, `fix:`, `docs:`, `chore:`, `test:`).
- One PR per feature, small, filled in from the PR template (what, why, screenshot). Squash-merge.
- **GitHub Projects** board (Backlog → In progress → Done), with **Issues** for every task and **Milestones** = the weeks below. Labels: `frontend`, `backend`, `ai`, `infra`, `docs`, `good-first-issue`.
- **Actions CI**:
  - `backend.yml`: test, then build the jar, then build the Docker image.
  - `frontend.yml`: lint, type-check, test, build.
  - `codeql.yml`.
  - Dependabot.
- **Secrets**: `ANTHROPIC_API_KEY` goes only in GitHub/Render secrets and in a `.env` that `.gitignore` excludes. Commit only a `.env.example`.
- **Releases**: tag `v0.1` (MVP), `v0.2` (boxes and scores), `v1.0` (compare and PDF), each with release notes.
- Pin the repo on the GitHub profile and add topics (`ux`, `accessibility`, `wcag`, `spring-boot`, `react`, `rag`, `claude`).

---

## 3. Tools, extensions and skills

**Claude Code (in this environment)**
- `/init`: create `CLAUDE.md` (stack, commands, conventions).
- `claude-api` skill: correct Anthropic Java SDK usage (models, structured output, caching, refusal fallback).
- `/code-review` and `/security-review` before every PR merge (file upload is an attack surface).
- `/simplify` after a feature is finished.
- `run` skill: start the app and take screenshots.
- `artifact-diagramming`: the architecture diagram for the README.
- `dataviz`: the score charts in the UI and the PDF.
- `pdf` skill: help with the PDF export.
- Playwright (already installed here): end-to-end tests, plus README screenshots and the demo GIF.
- GitHub through the `gh` CLI: issues, PRs, releases.

**Figma**
- Design the system first: tokens (color, type, spacing), the components (UploadDropzone, IssueCard, BBoxOverlay, ScoreGauge, CompareSlider), and 4 screens (Home/Upload, Analyzing, Report, Compare).
- Plugins:
  - Stark or Contrast (checks contrast in your own UI, which UX Lens must itself pass).
  - The A11y Annotation Kit.
  - Figma Dev Mode MCP server (lets Claude Code read the frames).
- Link the Figma file in the README.

**IDE**
- IntelliJ IDEA (Community is fine) for the backend, or VS Code with the Extension Pack for Java and the Spring Boot Extension Pack.
- VS Code for the frontend: ESLint, Prettier, Error Lens, axe Accessibility Linter, Docker, GitHub Actions, GitLens, and REST Client (or Bruno or Postman) for the API.

**Main libraries**
- Frontend:
  - Vite, React 18, TypeScript.
  - TanStack Query, React Router, Zod.
  - Tailwind CSS (or CSS modules with the Figma tokens).
  - Konva or plain SVG for the overlay.
  - Vitest + Testing Library, Playwright.
- Backend:
  - Spring Web, Validation, Data JPA, Flyway, Actuator.
  - `com.anthropic:anthropic-java`.
  - Spring AI (pgvector store + ONNX embeddings).
  - Bucket4j, OpenPDF (or Thymeleaf + Flying Saucer) for PDF.
  - Testcontainers (Postgres + pgvector), JUnit 5, springdoc-openapi (Swagger UI).

---

## 4. Step-by-step roadmap

### Week 0: Setup and design (3–4 days)
1. Create the GitHub repo, the folder layout, `.gitignore`, `.env.example`, a short README skeleton, license (MIT), the PR and issue templates, and the Project board with milestones.
2. `infra/docker-compose.yml`: `pgvector/pgvector:pg16` plus the backend.
3. Skeletons: Spring Boot (`/actuator/health`) and Vite React. Make both CI workflows pass.
4. Figma: tokens, components and the 4 screens. Link it in the README.
5. Optional, only for live analyses: an Anthropic API key with a small credit and an equal spend limit (issue #3).

### Week 1: Feature 1, upload and text report (MVP core)
1. `POST /api/analyses` (multipart): check type (png, jpg, webp) and size (≤ 5 MB) and verify the magic bytes. Resize to a maximum of 1568 px on the longest side. Save the image and its SHA-256 hash.
2. `AnalysisProvider` port with `MockAnalysisProvider` (default) and `ClaudeAnalysisProvider`: one vision call with a system prompt (role: senior UX auditor, Nielsen + WCAG) and a JSON schema for structured output. Handle `stop_reason` (refusal, max_tokens).
3. Synchronous at first. Save the analysis and its issues (Flyway migration: `analysis`, `issue`, `api_usage`).
4. Frontend: Upload page (dropzone) → Report page (issues grouped by severity).
5. **Milestone v0.1**: upload a screenshot, get a list of issues.

### Week 2: RAG over the guidelines
1. `knowledge/`: get WCAG 2.2 (W3C publishes it as JSON or HTML under the W3C license) and write the 10 Nielsen heuristics with examples. One chunk per criterion, with metadata (`id`, `level` A/AA/AAA, `category`).
2. Ingestion job (`CommandLineRunner` or `/admin/ingest`): embed the chunks and store them in pgvector.
3. Two-step pipeline: a first call describes the UI's elements, then retrieval finds the top-k criteria, then the analysis call runs with the criteria in the prompt (cached block). Validate that each `criterion_id` is in the knowledge base; drop it or flag it otherwise.
4. Frontend: every issue shows a "WCAG 1.4.3 Contrast (Minimum) – AA" chip with a link to the W3C page.
5. ADRs 0001 (RAG) and 0002 (local embeddings).

### Week 3: Features 2 and 3, boxes on the image and scores
1. The schema already has `bbox` (x, y, w, h in 0–1). Frontend: an SVG overlay scaled to the displayed image, numbered markers, and hover/click linking each box to its card in the list.
2. Scores from 0 to 100 for accessibility, visual hierarchy and consistency. Compute them in the backend from the severity of the issues (a deterministic formula, documented) blended with the model's rating. That makes them explainable.
3. A ScoreGauge or radar chart in the UI (use the `dataviz` skill).
4. Make the UI itself accessible: keyboard navigation of the issues, focus states, aria-labels, and contrast that passes AA.
5. **Milestone v0.2**.

### Week 4: Asynchronous work, history, cost control and the first deploy
1. Postgres job queue: `POST` returns `202` with a `jobId`. `@Scheduled` workers claim jobs with `SKIP LOCKED`, with retries and backoff. The frontend gets updates through SSE (or polling with TanStack Query).
2. History page: the list of past analyses (anonymous session id in a cookie; no auth for now; ADR on that choice).
3. Cost control:
   - Cache results by hash.
   - Prompt caching.
   - Rate limiting with Bucket4j.
   - A daily budget guard.
   - Log usage to `api_usage`.
   - An admin `/api/stats/costs` endpoint.
4. Demo mode (issue #17): a gallery of sample screenshots with pre-computed analyses; upload disabled on the public deployment.
5. Deploy: Neon (enable the `vector` extension), the backend Docker image on Render or Railway, the frontend on Vercel, CORS, and environment variables.
6. Add the live link to the README.

### Week 5: Features 4 and 5, compare and PDF
1. Compare: `POST /api/comparisons` with two analyses (before and after). Issue diff: resolved, new and persistent, matched by `criterion_id` and bbox overlap (IoU), plus the score deltas. UI: images side by side or a slider, with the diff list.
2. PDF export: `GET /api/analyses/{id}/pdf`, a server-side template that includes the image with its boxes drawn on (Java2D), the scores, the issues and the citations.
3. **Milestone v1.0**.

### Week 6: Quality, evaluation and the showcase
1. **Eval set** in `eval/`: 10–15 screenshots with issues annotated by hand (for example a contrast problem placed on purpose). A script measures recall and precision of the issues and checks the citations. Run it when the prompt or the model changes. A strong signal for the architect profile.
2. Tests: unit tests (score formula, diff/IoU, schema parsing), integration tests with Testcontainers (with the Claude client mocked), and Playwright end-to-end tests.
3. README:
   - A one-line hook and a demo GIF at the top.
   - The live link and the Figma link.
   - Screenshots and the architecture diagram.
   - Features, Tech stack, and run locally (`docker compose up`).
   - **"Technical decisions"**: links to the ADRs, the cost per analysis (measured), and the eval results.
   - Roadmap.
4. Record the GIF with Playwright or a screen recorder. Publish the `v1.0` release. Write a LinkedIn post.

---

## 5. Verification (per milestone)
- `docker compose up`: the full stack runs locally. Upload a known screenshot (with a contrast problem placed on purpose) and check that the report cites WCAG 1.4.3 and that its box sits over the right element.
- CI is green on every PR. `/code-review` and `/security-review` find nothing blocking.
- Eval script: recall/precision does not drop between versions.
- Deployed: the live URL works from a phone, and `/actuator/health` is UP.
- Costs: the `api_usage` table shows the average cost per analysis, which goes in the README.

## 6. First concrete actions after approval
1. Create the GitHub repo `ux-lens` and the monorepo skeleton (this session can scaffold it and push it).
2. Run `/init` to create `CLAUDE.md`.
3. Set up the CI workflows and the Project board with milestones and the Week 0–1 issues.
