# ADR 0002: Cost-free by default (AnalysisProvider port + demo mode)

- **Status:** accepted
- **Date:** 2026-11-20

## Context
UX Lens needs a vision model to analyze screenshots. The Claude API is billed per token and separately from a Claude Pro subscription, which cannot be used to power a deployed application. The project has a hard budget of €0 beyond existing subscriptions, and a public demo with a real API key would also let anonymous visitors spend money on that key.

## Decision
1. **The AI is a port.** The domain depends only on an `AnalysisProvider` interface (screenshot in, `AnalysisResult` out). Two adapters implement it:
   - `MockAnalysisProvider` (the default): returns realistic fixture results. Tests, local development and the public demo use it.
   - `ClaudeAnalysisProvider`: the real integration via the official Anthropic Java SDK (`claude-opus-5-5`, vision + structured output). It only activates when `uxlens.ai.provider=claude` and `ANTHROPIC_API_KEY` are set.
2. **The public deployment runs in demo mode.** It serves a gallery of sample screenshots with pre-computed analyses stored as JSON in the same schema as `AnalysisResult`. Upload is disabled there, with a note that live analysis needs a self-hosted instance with an API key.
3. **The demo analyses are produced at development time** with Claude (through Claude Code, covered by the Pro subscription) in the exact output schema, and the README says so.
4. **Hosting uses free tiers:** Neon (Postgres + pgvector), Render or Railway (backend), Vercel (frontend).

## Alternatives considered
- **Pay for the API from day one:** about $0.08–0.10 per analysis with Opus 5.5 or under $0.01 with Haiku 5.5. Cheap, but it breaks the €0 budget and exposes the key to abuse on a public demo.
- **A free-tier model from another provider:** free quotas change often, and it would move the project away from Claude.
- **A local open-weights vision model (e.g. via Ollama):** free on a dev machine, but free hosting can't run it, and quality is lower.

## Consequences
- CI never makes real API calls; the Claude adapter is unit-tested against a mocked SDK client.
- The architecture shows ports and adapters in practice: switching to live analysis is a config change, not a code change.
- The demo is static, so it can't show the analysis of a visitor's own screenshot. Turning on live analysis later only needs a small API credit and a spend limit (see issue #3).
- The RAG pipeline (ADR to come) must also run with the mock provider, so retrieval and citation checks stay testable without the API.
