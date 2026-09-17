# YulMe Frontend Delivery

## What is included

A complete first-pass product UI/UX and implementation for the user-facing YulMe learning platform.

### Routes

| Route | Purpose |
|---|---|
| `/` | Public landing / product story |
| `/login` | Parent authentication |
| `/register` | Family onboarding |
| `/app` | Child home / daily learning cockpit |
| `/app/learn` | Learning-path and subject browser |
| `/app/lesson/:id` | Lesson overview and activity sequence |
| `/app/activity/:id` | Focused activity / question player |
| `/app/results` | Celebration + next-step result screen |
| `/app/progress` | Child mastery and streak view |
| `/parent` | Parent dashboard |
| `/parent/child/:id` | Child learning profile / insights |
| `/parent/settings` | Parent account, privacy and consent controls |

## Design-first process

The page inventory, visual system, UX principles, responsive rules, accessibility requirements and backend integration boundary are documented in `UX-UI.md` before implementation.

## Backend relationship

The supplied Spring Boot project is preserved unchanged under `learning-platform-api/`. It currently contains domain services and persistence but no REST controllers. Therefore this frontend ships with **demo mode** enabled so every screen can be reviewed and clicked through now.

`src/lib/api.ts` is the transport boundary. When REST controllers are implemented, switch `VITE_DEMO_MODE=false` and map the real response DTOs there without rewriting the UI.

## Brand assets

The supplied YulMe brand/illustration references are included under `public/assets/` and are used in the public/auth experiences.

## Local development

```bash
cd yulme-frontend
npm install
npm run dev
```

## Production build

```bash
npm run build
```

This environment could not complete `npm install` because package registry access timed out, so dependency installation/build verification must be completed in a normal development environment.
