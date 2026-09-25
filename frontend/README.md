# Bookeyond Kid — Frontend

Vite + React 18 + TypeScript + Tailwind. Talks to the Spring Boot backend
(`/api/v1/...`) via a dev-time proxy — see `vite.config.ts`.

## Run it

```bash
npm install
cp .env.example .env   # optional — only matters if your backend isn't on :8080
npm run dev
```

Needs the Bookeyond Kid backend running locally (default expected at
`http://localhost:8080`) for anything past the public marketing page —
login, register, and every authenticated route make real API calls.

## Structure

```
src/
  lib/api/        fetch client, token storage, ApiError, typed DTOs (kept
                   in sync with backend/src/main/java/com/platform/**)
  lib/auth/        AuthContext — session state, login/register/logout,
                   boot-time silent refresh
  routes/          RequireAuth, RequireRole route guards
  components/ui/   Button, Pill, Card, SectionHeading — shared primitives
  features/marketing/   the public landing page (Nav + 17 sections + Footer)
  features/auth/        Login / Register pages + shared AuthLayout
  features/app/          AppShell + per-role placeholder shells
```

## Design tokens

`tailwind.config.ts` colors/typography/shadows/radii are read directly off
the Figma file (`mVXoqLOgRfnt8LjjYiezUh`) via `get_design_context`, not
guessed. Two deliberate departures from the brand-guideline doc are noted
inline in that file (Rubik instead of Nunito, and the exact blue/green hex
values) — the Figma wins on visual specifics per the project's own
source-of-truth rule.

## Role model

Backend `PlatformRole` is `PARENT | TEACHER | PRINCIPAL | ADMIN` — there is
**no CHILD account type**. Children are represented via the family/child
model and are only ever reached through a parent's session. The frontend
does not invent a child login; the child-facing experience (Stage 4) will
be a mode inside the parent's authenticated session, not a separate role.

## Current status

- Public marketing site: fully built (all sections from the Figma file)
- Auth: real login/register/logout wired to the backend, with the
  PRINCIPAL pending-approval branch handled
- Route guards: wired and role-aware
- Authenticated shells: placeholders only (Parent/Teacher/Principal/Admin)
  — they prove the auth → guard → shell chain works, they are **not** the
  real dashboards yet
- No mock/fake data anywhere in a shipped flow — placeholders say
  they're placeholders instead of fabricating numbers

## Not yet built

Parent dashboard, child experience, activity runner, teacher/principal/
admin consoles, and all of the real backend integration behind them
(enrollments, activity attempts, parent insights, etc.). See the API map
in `lib/api/endpoints.ts` for what's already wired vs. what a given screen
will still need.
