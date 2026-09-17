# YulMe Frontend

YulMe is a kid-first adaptive learning frontend designed around the supplied YulMe brand direction: playful, bright, trusted, and progress-oriented.

## Current state

- React + TypeScript + Vite
- Responsive kid experience and parent dashboard
- Route-ready authentication, learning, assessment, progress, and settings flows
- Demo/mock data so the UX can be reviewed before the backend REST layer exists
- API client boundary in `src/lib/api.ts` for future Spring Boot integration
- UX/UI design spec in `UX-UI.md`

## Run

```bash
npm install
npm run dev
```

## Build

```bash
npm run build
```

The current backend contains business services but does not yet expose REST controllers, so the frontend deliberately runs in demo mode by default. Replace the mock functions in `src/lib/api.ts` as backend endpoints become available.
