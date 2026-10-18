# Redesign plan, Y37 (2026-10-18)

The River Garden world (DESIGN.md) stays: it is distinctive, Cambodian, accessible, and it works in
both languages. This pass changes **structure and priority**, role by role, where the review of
every role's main screen (desktop, real data) showed a problem.

## What the review found

| Role | Strong | Problem |
|---|---|---|
| Landing | The hero, real product screens, the evidence explanation, the buddies, honest subjects | Nothing on safety or parental control, which is the first thing a Cambodian parent asks. No plans section, so "how much?" is unanswered. Two pastel audience cards read as generic SaaS. |
| Child | The home scene, one door per subject, the buddy on the path, the quiet activity field | Nothing structural. Breaks (Y36) are now in the flow. |
| Parent | Evidence first; standings; "what changed"; completion ≠ mastery | The family home repeats a long "Nothing flagged…" box on every child card. With several children, that is noise where the eye looks for action. |
| Teacher | Classes with who needs practice | The home is a bare list. Storage and authoring live elsewhere, with no sense of what is theirs. |
| Principal | The glance; learning across the school; the plan card (Y35) | One long page mixes oversight, class admin, staff admin and the plan. There is no progressive disclosure. |
| Admin | "Waiting on you" first; dense, calm | Storage pressure isn't surfaced where an operator looks first. |

## Decisions

1. **Brand**:
   - The logo stays one component (`components/brand/Logo.tsx`), so the official Bookeyond mark
     can replace the drafted one by swapping a single file (PD-34).
   - No new logo is invented.
2. **Bilingual constraints, applied everywhere**:
   - Labels may wrap and never truncate meaning.
   - Buttons grow in height rather than clipping, and touch targets are at least 44 px.
   - Kantumruy Pro for Khmer at its own line height (already in tokens).
   - No information carried by an English-only icon or abbreviation.
   - Curriculum language stays separate from interface language (LOCALIZATION.md).
3. **Landing** (Persuade):
   - Add **"Safe by design, and you decide"**. Only things the product really does:
     - no ads, no chat, no strangers, no rankings;
     - a grown-up gate;
     - break reminders the parent sets;
     - evidence the parent can read;
     - export and deletion.
   - Add **Plans**:
     - Families: early access, everything included and nothing charged. Prices come later, and
       nothing is charged without them.
     - Schools: arranged with the team.
     - No invented price.
   - Replace the two pastel audience cards with a quieter two-column "For families / For schools"
     band.
   - Add a Plans link to the navigation.
4. **Child**:
   - Keep the scene and the doors.
   - The break suggestion appears only at the activity-complete pause (Y36).
5. **Parent**:
   - The family home's child card shows the practice box only when something is flagged.
     Otherwise one quiet line: "Nothing flagged yet."
   - Deeper evidence stays one tap away.
6. **Teacher**:
   - The home gains a "Your teaching" summary under the classes: their content and their media
     storage (the meter's numbers, linking to the library).
7. **Principal**:
   - The workspace splits into **Overview**: the glance, learning across the school, and a
     one-line plan summary.
   - Then **Classes**, **Teachers** and **Plan**, each its own address in the rail.
   - Progressive disclosure, with nothing removed.
8. **Admin**:
   - "Waiting on you" lists authors who are nearly full or over their storage, linking to
     Storage.

## Verification (bounded)

- `tsc`, lint, the affected unit tests and the production build.
- One batch of captures: landing, child, parent, teacher, principal and admin, at desktop and
  phone, in English and Khmer for the landing and parent home.
- The E2E journeys the structure touches: school (principal navigation), accessibility, and the
  golden family journey.
