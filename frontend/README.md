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
  components/ui/   Button, Pill, Card, SectionHeading, States — shared primitives
  features/marketing/   the public landing page (Nav + sections + Footer)
  features/auth/        Login / Register pages + shared AuthLayout, FormField
  features/parent/      family home, child overview (learning now + why,
                        skills & evidence, recent activity, insights)
  features/child/       the child space: journey home, play session,
                        question views, grown-up gate
  features/buddy/       the Learning Buddy character and feedback sounds
  features/learning/    explain.ts — words for server-recorded reasons
  features/teacher/     teacher classes, roster, student view
  features/app/          AppShell + placeholder shells for unbuilt consoles
```

## Design tokens

`tailwind.config.ts` colors/typography/shadows/radii started from the Figma
file (`mVXoqLOgRfnt8LjjYiezUh`). Two deliberate departures from the
brand-guideline doc are noted inline (Rubik instead of Nunito, and the exact
blue/green hex values). The `kid` palette (cream, mint, lavender, sky, peach,
sun, coral) is the child space's; each hue has a soft fill and a deep tone
checked for WCAG AA contrast on that fill. Filled controls with white text use
`brand.blueStrong` (#2563EB, 5.2:1) — `brand.blue` (#3B82F6) is only 3.7:1.

Motion keyframes live in the same file; every one communicates a state change
(arrival, success, "try again", the next step) and all motion is neutralised
under `prefers-reduced-motion` (`index.css`).

## Role model

Backend `PlatformRole` is `PARENT | TEACHER | PRINCIPAL | ADMIN` — there is
**no CHILD account type**. Children are represented via the family/child
model and are only ever reached through a parent's session. The child space
(`/learn/:childId`) is a mode inside the parent's session; leaving it for the
grown-up area goes through a press-and-hold gate (friction, not security — the
server authorizes every parent action).

## Current status

- Public marketing site: built.
- Auth: real login/register/logout and silent refresh (single-flight,
  cross-tab) wired to the backend.
- Parent: family home with each child's current path; child overview with
  what they're learning now and why the next step is next, skills with
  evidence strength (never "weak" for "not assessed"), recent activity with
  what the platform decided afterwards, current insights, rename.
- Child: journey home, activity intro, multiple choice / true-false /
  numeric / short answer, explanatory feedback (the correct answer after a
  miss), Learning Buddy reactions, sound (mutable), completion with the
  server's next step; resumes from server state after reload.
- Teacher: classes, roster, student view (early).
- Principal / admin consoles: placeholders ("Coming soon") — not built.
- No mock/fake data anywhere in a shipped flow.

## Not yet built

Principal and admin consoles, Content Studio, account settings (password
change/reset, verification), Khmer UI localization, subscription, and
automated frontend tests. See `BOOKEYOND-KID-ENGINEERING-PROGRESS.md` at the
repository root for the full, evidence-based status.
