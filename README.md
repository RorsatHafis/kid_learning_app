# YulMe

YulMe full-stack delivery containing the fixed backend foundation and the YulMe frontend.

## Packages

- `YulMe-Backend/` — Spring Boot 3 / Java 21 backend, PostgreSQL + Flyway.
- `YulMe-Frontend/` — React + TypeScript + Vite frontend with responsive YulMe UX/UI.

## MVP learning loop

The implemented P0 flow uses real backend state: authenticated family access,
child enrollment, published activity content, server-side answer scoring,
mastery evidence, learning-path completion, adaptive recommendation, smart review,
and parent insights. Learner-facing DTOs do not expose correct answers or option
correctness flags.

## Validation

The final hardening pass ran:

```bash
cd YulMe-Backend
./mvnw test
```

Result: 26 tests passed. The default test suite excludes the tagged Testcontainers
integration test; its execution needs a Docker-capable environment.

```bash
cd YulMe-Frontend
npm ci
npm run build
```

Result: production TypeScript/Vite build passed.
