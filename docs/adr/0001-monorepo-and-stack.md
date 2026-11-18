# ADR 0001: Monorepo with React frontend and Spring Boot backend

- **Status:** accepted
- **Date:** 2026-11-18

## Context
UX Lens is built by one developer as a portfolio project. Reviewers (recruiters, engineers) should find everything in one place.

## Decision
One repository with `frontend/` (React + TypeScript + Vite), `backend/` (Spring Boot 4, Java 21), `knowledge/`, `eval/`, `infra/` and `docs/`. CI runs per folder using path filters.

## Alternatives considered
- **Two repos:** cleaner separation, but splits stars, issues and the README; more overhead for a solo project.

## Consequences
A single README and project board. Path-filtered workflows keep CI fast. If the project grows into separately deployed teams, splitting later is straightforward.
