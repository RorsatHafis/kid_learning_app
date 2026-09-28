# Bookeyond Kid

A learning platform for children aged 4–9 and their families, with school workspaces and a
Content Studio for educators.

- **Run it on Windows and test it:** [OWNER-GUIDE.md](OWNER-GUIDE.md) — double-click
  `Start Bookeyond Kid.bat` (needs Docker Desktop running), then open http://localhost:5173.
- **Deploy it to a server:** [DEPLOYMENT.md](DEPLOYMENT.md).
- **What is built and how it was verified:** [BOOKEYOND-KID-ENGINEERING-PROGRESS.md](BOOKEYOND-KID-ENGINEERING-PROGRESS.md).
- **Engineering rules:** [CLAUDE.md](CLAUDE.md).

For developers: `backend/` (Spring Boot 4, Java 21, PostgreSQL 18 with Flyway) and `frontend/`
(React 18, Vite 7, Tailwind). Tests: `./mvnw -Pintegration test` in `backend/`;
`npm run lint && npm run typecheck && npm test` in `frontend/`; end-to-end journeys
`npm run test:e2e` against a running stack (see `frontend/playwright.config.ts`). CI runs all
of them (`.github/workflows/ci.yml`).
