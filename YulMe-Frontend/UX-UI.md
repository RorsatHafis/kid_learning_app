# YulMe UX/UI Specification

## Product promise
**Understand Me. Help Me Grow.**

YulMe should feel safe enough for parents, playful enough for children, and calm enough to sustain focused learning.

## Brand system
- Primary blue: `#2563EB`
- Sky blue: `#38BDF8`
- Warm yellow: `#FBBF24`
- Growth green: `#22C55E`
- Soft background: `#EAF2FF`
- Deep ink: `#142B66`
- Font direction: Nunito Rounded / system rounded fallback

## UX principles
1. One clear action per screen for children.
2. Large touch targets (minimum ~48px).
3. Short copy, visual feedback, no intimidating error language.
4. Progress is celebratory, never punitive.
5. Parent surfaces are information-dense but calm and scannable.
6. Child data is visually separated from adult controls.
7. Every learning interaction should answer: What am I learning? What do I do? How did I do?

## Page inventory

### Public
- `/` Landing: value proposition, how it works, trust signals, CTA.
- `/login`: simple sign-in with demo role switch.
- `/register`: parent account creation with child-first onboarding explanation.

### Child
- `/app`: child home / learning cockpit.
- `/app/learn`: subject and learning-path browser.
- `/app/lesson/:id`: lesson overview with objective and activity sequence.
- `/app/activity/:id`: focused question/activity player.
- `/app/results`: session celebration and next recommendation.
- `/app/progress`: mastery map and streaks.

### Parent
- `/parent`: parent overview.
- `/parent/child/:id`: child detail and skill insights.
- `/parent/settings`: account, privacy, consent and preferences.

## Responsive behavior
Desktop uses a persistent sidebar for authenticated app views. Mobile collapses to a bottom navigation with a floating primary action. Learning player is intentionally full-width and distraction-light.

## Accessibility
Keyboard navigable controls, visible focus rings, semantic buttons/links, high contrast text, reduced-motion preference, and non-color-only progress indicators are included in the implementation.

## Backend integration boundary
The UI models the existing domain: account, family, child, curriculum, lessons, activities, attempts, mastery, assessments, recommendations. Because the supplied Spring Boot backend currently has no REST controllers, demo data is isolated in `src/data/mock.ts` and transport logic in `src/lib/api.ts`.
