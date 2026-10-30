# Localization — Khmer and English

Bookeyond Kid's own words (the interface, its errors and its emails) come in English and Khmer. What
educators author (curricula, questions, activities, skill and objective names, pictures and
recordings) is **content**. Content is never translated by the platform (PD-15).

> **Status of the Khmer:** the Khmer interface copy was drafted for the product. It was not written
> by a native-speaking editor. PD-14 says machine-produced Khmer for children is not acceptable,
> so **a native speaker must review it before it is offered to real families** (see
> [Native-speaker review](#native-speaker-review)). This status is recorded in the progress document.

## Which language is used

| Who | Where the choice lives | How it is chosen |
|---|---|---|
| A visitor (not signed in) | the browser (`localStorage` key `bk-locale`) | The first language the browser asks for that we support, otherwise English. The switcher on every public page and sign-in page changes it at once. |
| A parent, teacher, principal or administrator | the account (`accounts.preferred_language`) | Registration saves the language the visitor had chosen. **Account → Language** changes it; the change is saved before it is shown. Sign-in and refresh apply it. Signing out keeps it on that browser as the visitor's choice. |
| A new account someone else creates (an invited teacher, a provisioned member of staff) | the account | Starts in the inviter's language. Their invitation email is in that language. |
| A child | the child (`children.preferred_language`, V57) | Starts in the language the parent was using when the child was added. The child changes it in their own space, with no grown-up gate. It never changes the parent's language, and the parent's never changes theirs. |

The language is `en` or `km`, nothing else (database `CHECK` constraints; the server's
`AcceptHeaderLocaleResolver` is narrowed to those two, with English as the default).

## How it works

**Frontend (`frontend/src/i18n`)**

- `store.ts`: the current language, from three sources: a child scope, then the account, then the visitor. It sets `<html lang>`.
- `catalog.ts`: `defineMessages(name, en, km)`. A catalog is one feature's words under stable keys. An entry is text, or a function for text with values or plurals. The Khmer side must have the same keys, and functions must take the same values. A missing key is a **type error**, not a blank on screen.
- `format.ts`: dates, times and numbers in the current language. Khmer month and weekday names are built in, for browsers without Khmer date data.
- `LanguageSwitcher.tsx`: the switcher (two buttons) and the toggle (one button, used on phones and in the child space).
- `useLanguage.ts`: saves the choice. For an account or a child it saves to the server first; if that fails, the language stays as it was and the person is told.
- Every request sends `Accept-Language` with the current language, the token refresh included.
- Catalogs live next to the screens that use them: `messages.ts` or `*.messages.ts`.

**Backend**

- `common/web/ServerText.java`: error details and field messages in the request's language, translated once at the response boundary. Messages are thrown in English, so logs and audit records stay in one language. `ServerTextCoverageTests` fails when a thrown message has no entry. A message with no Khmer gets a plain Khmer sentence for its status, never English.
- Security filters (sign-in required, rejected token, rate limit) answer before Spring MVC, through `ServerText.forRequest`.
- Emails are written in the recipient's saved language. URLs, tokens and expiry periods are the same in both languages (`EmailLanguageIntegrationTests`).
- Parent insight sentences are translated by their shape (`parent/web/InsightText.java`). The family data export stays in English.
- **Studio publish checks and journey warnings** carry a stable `code` and the `values` their sentence names (a skill, a question's number, a count), next to the English `message`. The Studio words each one from its code (`features/admin/studio/messages.ts`, `checks` and `journeyIssues`). A code the client doesn't know yet, or a check missing a value its sentence needs, shows the server's English sentence rather than a gap. `issues.test.ts` reads the server's sources, so every check the server can give has wording in both languages.

## Adding or changing text

1. Put the words in the screen's catalog, in both languages. Never put a sentence in a component.
2. Use a function for anything with a number or a name in it: `(n: number) => …`. Use `plural(n, one, other)` for English. Khmer has no plural forms.
3. Format dates and numbers with `i18n/format.ts`, never with `toLocaleDateString()`.
4. On the server, throw the English message. Then add its Khmer to `ServerText`; the coverage test will tell you if you forget.
5. Run `npx vitest run src/i18n`. It checks:
   - `catalogs.test.ts`: every catalog has the same keys and kinds in both languages, no empty text, and Khmer written in Khmer.
   - `hardcoded.test.ts`: there is no interface text in the screens. Its `ALLOWED_TEXT` list is the only exception.

Khmer conventions used so far:

- A child is addressed as «ប្អូន».
- A plan is «កញ្ចប់», and a learning journey or path is «ដំណើររៀន» / «ផ្លូវរៀន».
- Content names inside a sentence are quoted «…» exactly as the educator wrote them.
- The ្ដ spelling is used throughout.

## Intentionally English, or not translated

- **Educator content** not yet in two languages (media descriptions, lesson titles): shown as written until its slice of "Content in two languages" (below) is built. Questions, activities, and subject, skill, chapter and curriculum names have English and Khmer since 2026-10-29.
- **Brand and product names:** Bookeyond Kid; browser names (`ALLOWED_TEXT`); currency codes (`USD`).
- **Audit log metadata** (keys and recorded values) and identifiers. Action and resource names *are* translated.
- **Text written by administrators:** messages to families. (Plan names and descriptions have English and Khmer since
  2026-10-30, V82 - see "Plans" below.)
- **The owner's legal documents** (terms, privacy notice). They are published documents, linked as they are. A Khmer version is a legal task (PD-18).
- **The family data export** (a portable record, kept in one language).
- **Browser-native messages**, such as built-in form-validation bubbles, which follow the browser's own language.
- **Developer-only pages** (`/__brand`, `/__play`).

## Content in two languages (owner requirement, 2026-10-29)

The owner asked for every piece of user-facing content that can be localized to have **two independently saved
inputs, English and Khmer**, with a content-language switch that is separate from the interface language. This
answers the first open question that used to be here (*a Khmer curriculum is a translation of the same content, not
separate content*). Slice 1 - **questions** - and slice 2 - **activities** - are built; the rest is listed below in order.

### The model

- A content version's own columns are its **English**. Its **Khmer** is kept in `content_localizations` (V12), keyed
  by the version, one row per field, locale `km`. For a question version: `prompt`, `hint`, `explanation`,
  `promptImageDescription`, and `option:<id>` for every choice, pair side, ordering item, sorting group and item.
  No new table and no migration: the table existed and was unused.
- English and Khmer are **saved together** in one request (`khmer` beside the English fields), so neither can
  overwrite the other. Answer rows are re-created on every save, so their Khmer labels travel with them in the same
  request (one label per row, in the rows' order) and are written against the new rows in the same transaction.
- A version's Khmer is **frozen with it** when it is published, like the rest of the version, and **carried to every
  copy**: a new draft of a published version, "Make a copy", and a colleague's copy of a shared lesson - each option's
  label mapped to the copy's option by its place, side and pair, role and group (`QuestionTranslations.copy`).
- Existing content has no Khmer and keeps working exactly as before (served in English, as written).

### What a child is given (rule, built - the owner may change it)

- A child whose language is Khmer (the request's language, which follows the child's own language, Y32) is given a
  question **in Khmer only when its Khmer is complete**: the prompt, and every field the English has (hint,
  explanation, picture description) and every option's label. Otherwise the **whole question in English**, as
  written - never half one language and half the other. The explanation after answering and the right answer shown
  after a wrong answer come in the same language as the question. Scoring never depends on the language (choices,
  pairs, items and groups are answered by id).
- The served question says its language (`language: "km"`), and the child's screen marks it `lang="km"`, so a screen
  reader reads Khmer content as Khmer.
- **The child's language comes first** (fixed 2026-10-30): content is served in the language of the request, and a
  child's space speaks the child's own language, not the parent's. `ChildLanguageGate` (the `/learn/:childId` route)
  learns the child's language before any page of the space asks the server for anything, and shows the page afresh
  when the child changes it (only possible outside an activity: the player has no language button). Before, the first
  page of a session was fetched in the parent's language, so an English-reading child of a Khmer-reading parent saw
  Khmer names and words under an English screen (and the reverse).
- **Short answers are not translated**: they are scored against the author's own words, so a Khmer prompt would mark
  a Khmer answer wrong. The Studio says so and keeps such a question in the language it is written in. Scoring
  per-language accepted answers is a later decision.
- Khmer is **optional**: an incomplete translation is a publish *warning* (`KHMER_INCOMPLETE`: children reading Khmer
  get the English), never an error.

**Decision recorded (L-1)**: the fallback above - a whole question in English when its Khmer is incomplete - was
chosen as the honest default (a child is never shown a mixed question, and nothing is invented). Alternatives: hide
untranslated questions from Khmer readers (would leave paths empty), or allow mixing per field. The owner may choose
differently.

### The Studio

- Above each question form, **"Writing in: English | ខ្មែរ"**, starting on English (the required language) whatever
  the interface language. Switching shows the other language's inputs and their saved values; nothing is reset,
  nothing navigates, and the language not shown is untouched.
- In Khmer, each field is labelled "... - in Khmer" and marked `lang="km"`; the English it translates is shown under
  the long fields and as the placeholder of each answer row, so both versions can be checked side by side.
- The switch says how much is written: "No Khmer yet", "Khmer: 3 of 5 written ...", "Khmer complete ...".
- The question bank says the same for every question in its list ("in Khmer too", "Khmer not finished", "English
  only"), and its search, like the lessons', finds content by its Khmer as well as its English.
- "Try it as a child" on an activity, like a question's preview, shows it "as a child reading: English | ខ្មែរ".
- Structured settings (the correct choice, difficulty, numbers, true/false, groups chosen, order, pictures and
  recordings) are shared: one control for both languages.
- The question's preview has **"As a child reading: English | ខ្មែរ"**, showing exactly what each child gets (and
  saying so when the Khmer isn't complete and a Khmer reader would get the English).
- There is **no automatic translation** anywhere: every Khmer word is written by an educator (PD-15). The interface's
  own language button changes only the interface.

### Catalog names (slice 3)

- A subject's, skill's, chapter's and curriculum's English stays on its own row; its Khmer `name` and `description`
  are rows of `content_localizations` keyed by that row (V81 adds the four content types to the CHECK constraint;
  nothing else changes).
- A name is **one self-contained label**, so it is given to a Khmer reader **per name, whenever its Khmer is
  written**, and in English otherwise - unlike a question, whose parts must all be in one language (L-1). A Khmer
  name never needs the description's Khmer to be shown.
- Where it is read: the family catalog (choosing a curriculum: curriculum and subject names), the child's path
  (curriculum, subject, chapter titles and descriptions, the skills each step practises), the skills page (chapter
  and skill names and descriptions), "what changed since your last visit", a skill explained (its activities and
  curricula too), and the skill or chapter named in a parent insight's sentence.
- A parent insight records its sentence in English with the name as it was then; on screen the sentence is put in the
  reader's language (`InsightText`) and the name it carries swapped for its Khmer (`InsightNames`). A name renamed
  since the sentence was recorded is left as recorded.
- The Studio: "Writing in: English | ខ្មែរ" in the Manage dialog of each of these and in the new-skill, new-chapter,
  new-subject and new-curriculum forms; the code stays one field (it is not a name). A save that never touched Khmer
  sends none and leaves it as it is; a Khmer field emptied on purpose removes that Khmer.
- Teachers' class views and the curricula they can assign name curricula and subjects the same way. Not in Khmer:
  the names of age groups (a short fixed list, not educator content).
- A Khmer reader's names are read together - per curriculum shown, not per name (`CatalogNames.prefetch`, kept for
  the rest of the request's transaction); an English reader costs no extra read.

### Plans (V82)

- A plan's English name and description stay in `name` / `description`; its Khmer in `name_km` / `description_km`
  (not `content_localizations`: a plan is commercial configuration, not learning content). NULL = not translated.
- Written, like the English, **only while the plan is a draft** (Admin -> Plans, "Writing in: English | ខ្មែរ" in the
  draft's form), and **carried to a duplicate** with the English; a save that never touched Khmer leaves it as it is.
- Read in the reader's language - per word, the English wherever no Khmer is written - by a family (its plan, what it
  may choose or ask for, the plan offered when a limit is reached, its open request and the emails about it) and by a
  principal (the school's plan and the plans it may pay for). An administrator always manages the English as English,
  with the Khmer beside it, whatever their interface language. A payment order keeps the English name it was made with.
- **Decision L-2 (owner, 2026-10-30: option b, built)**: an administrator adds or corrects *only* the Khmer of a plan
  at any status - offered, retired or draft - with "Edit the Khmer words" on the plan (`PUT /admin/plans/{id}/khmer`,
  `SubscriptionPlan#retranslate`). It is a translation, not a change to the plan: the English, price, currency,
  interval, limits, status, offer dates, every subscription and every payment order stay exactly as they were (nothing
  is activated, retired, renewed or moved). It needs a step-up like other changes customers of an offered plan see,
  and is audited (`PLAN_TRANSLATED`, with the Khmer before and after). Families and schools on the plan reading Khmer
  see the new words at once. That the Khmer says the same as the English is the administrator's responsibility -
  nothing checks meaning, and nothing is translated automatically. Editing an offered plan's *English* stays
  impossible (option c, not chosen): it is what subscribers agreed to.

### Screen-reader language of content shown as written (design, not built)

The owner's direction (2026-10-30): mark Khmer as `km` and English as `en` only from explicit metadata, never by
guessing, and leave content whose language is unknown unmarked.

- **Today**: a question served in Khmer says `language: "km"` and is marked `lang="km"` (also an activity's own
  words, `language` on the activity, since 2026-10-30). Content served *as written* says `null` and is not marked, so
  it inherits the page's language. That is correct for an English page; on a Khmer page an English fallback is read
  with Khmer pronunciation.
- **Why not mark it "en"**: the base columns are labelled English in the Studio only since the bilingual inputs
  (2026-10-29). Earlier versions may hold Khmer typed into those fields, and short labels can't be told apart
  reliably by script detection (numbers, names, mixed words).
- **Design**: a nullable `text_language` ("en") on `question_versions` and `activity_versions`, set when a version is
  created or saved through the bilingual Studio (whose base fields are English by label), copied to new drafts and
  copies; NULL for every existing version (unknown - stays unmarked). The served item's `language` then becomes
  "km" (Khmer given), "en" (as written, known English) or null (unknown), and the screens mark `lang` only when it is
  not null. Catalog names and plans need no column: their base fields have always been the English ones, but they
  were also editable before the bilingual inputs, so they get the same treatment only if the owner wants it.
- **Migration implications**: one migration adding the nullable column to two tables (no backfill: unknown stays
  unknown); a version published before stays NULL until its next draft is saved; an author can't mark old content
  "English" except by saving a new draft. Published versions are frozen, so nothing a child already has changes.
- **Status - questions built (V83, 2026-10-30)**: `question_versions.text_language` ('en' or NULL), set on every
  Studio create and save of a draft, carried to the next draft and to copies; served beside `language` as
  `writtenIn` ("en" or null - the existing `language` field keeps its meaning, so nothing reading it changes). The
  child's question is marked `lang` from `language ?? writtenIn`, and left unmarked when both are null. **Not yet**:
  an activity's own words (name, instructions, introduction) - the same column on `activity_versions` is next.

### Inventory: what still has one language (in order)

| # | Content | Where | Fields | Notes |
|---|---|---|---|---|
| 1 | **Questions** | Studio question editor, New question, the activity builder | prompt, hint, explanation, picture description, choices, pairs, items, groups, sorting items | **Built** (slice 1). Short answer excluded (scoring). |
| 2 | Activities | Activity editor, builder | title, instructions, "Teach it first" text and picture description | **Built** (slice 2): `ACTIVITY_VERSION` rows (`ActivityTranslations`); the name is kept per version and written under More settings; a child reading Khmer gets the activity's own words in Khmer when all are translated, and the same name on the path, home and shelf. |
| 3 | Curricula and chapters | Curriculum editor (Manage), New curriculum, Skills & objectives (new and Manage) | curriculum name; chapter (learning objective) title and description; skill name and description | **Built** (slice 3, V81 widens the content types to `SUBJECT`, `SKILL`, `LEARNING_OBJECTIVE`, `CURRICULUM`; `CatalogNames`). Catalog rows aren't versioned, so neither is their Khmer. A curriculum has no description to translate. |
| 4 | Subjects | Studio (new subject, Manage subject) | subject name and description | **Built** (slice 3). |
| 5 | Media | Media library | title, description | **Not a reader-facing text** (checked 2026-10-30): the library's title and description help authors find a file, and the description is offered as the starting text of a question's or introduction's picture description - which children's screen readers do read, and which is already written in both languages (slices 1-2). Low value; future. |
| 6 | Lessons | Studio | lesson title | Checked 2026-10-30: read only by teachers (their Studio, colleagues' shared lessons) - never by a child or a family; the activities inside are bilingual. Future (would need a `LESSON` content type). |
| 7 | Landing page / site copy | Admin → Landing page | every editable text | Already **bilingual** (F1a: each text has English and Khmer; statements require both). Checked 2026-10-30: the landing page's plans section is site copy (`features/marketing/trust`), not the plan records, so it is covered. |
| 8 | Plans | Admin → Plans | name, description | **Built** (V82, "Plans" above); offered plans' Khmer is decision L-2. |
| 9 | Messages to families | Plan request declines and other notices | free text | Written once to one family, in the administrator's language; translation optional. |
| 10 | School names, class names, academic years | Principal | names | Proper names: not translated. |
| 11 | Age groups | Seeded reference data (no form) | name ("Junior") | Checked 2026-10-30: families see it beside the age range, which *is* localized ("អាយុ 4-9"). No editing form exists; a Khmer name would come with the owner's final age groups (D-19). Future. |
| 12 | Assessments, challenges | Backend only | title, instructions, description | Checked 2026-10-30: no screen uses them; nothing to translate until they are part of the product. |

Not localized on purpose: numbers, dates, identifiers, codes, scores, difficulty, prices, durations.

## Native-speaker review

Before Khmer is offered to real families, a native-speaking editor reviews, in this order:

1. the child's space: `features/child/messages.ts` and `features/learning/messages.ts`;
2. what families read: `features/parent/*.messages.ts`, `features/marketing`, `features/auth`, `features/legal`, the emails (`AccountRecoveryService`, `SchoolStaffService`, `FamilyErasureService`, `PlanRequestService`, `AdminService`), `InsightText` and `ServerText`;
3. what schools read: `features/school`;
4. staff tools: `features/admin`, including the Studio.

Corrections go into the catalogs. The keys stay the same, so nothing else changes.

The text added or changed by the River Garden redesign (Y33: Learning Buddies, the activity's end, the parent's skill tally, the landing's children section) is listed with its context and intended meaning in KHMER-REVIEW-Y33.md, for a single review pass.
