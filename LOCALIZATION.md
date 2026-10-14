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

- **Educator content.** Curricula, questions, activities, skill and objective titles, media titles and descriptions: what the author wrote is shown as written. The Studio has no language field for content yet (see below).
- **Brand and product names:** Bookeyond Kid; browser names (`ALLOWED_TEXT`); currency codes (`USD`).
- **Audit log metadata** (keys and recorded values) and identifiers. Action and resource names *are* translated.
- **Text written by administrators:** plan names and descriptions, messages to families.
- **The owner's legal documents** (terms, privacy notice). They are published documents, linked as they are. A Khmer version is a legal task (PD-18).
- **The family data export** (a portable record, kept in one language).
- **Browser-native messages**, such as built-in form-validation bubbles, which follow the browser's own language.
- **Developer-only pages** (`/__brand`, `/__play`).

## Content translation: still to build

The interface is bilingual; **content is not yet**. The base content tables have no language column. A
`content_localizations` table (V12) and a `ContentLocalizationService` exist, but no endpoint,
Studio screen or learner read path uses them. So:

- the Studio can't record which language a question or activity is written in, or hold a second language for it;
- a child sees content in the language its author wrote it in, whatever the interface language;
- the content's own `lang` attribute can't be set, so screen readers read Khmer content with the page's language.

Building this needs product decisions first:

- Is a Khmer curriculum separate content, or a translation of an English one?
- Does a version pin its language?
- What happens to a child whose interface language has no content?

The content itself must come from educators (PD-15). Nothing here may translate it automatically.

## Native-speaker review

Before Khmer is offered to real families, a native-speaking editor reviews, in this order:

1. the child's space: `features/child/messages.ts` and `features/learning/messages.ts`;
2. what families read: `features/parent/*.messages.ts`, `features/marketing`, `features/auth`, `features/legal`, the emails (`AccountRecoveryService`, `SchoolStaffService`, `FamilyErasureService`, `PlanRequestService`, `AdminService`), `InsightText` and `ServerText`;
3. what schools read: `features/school`;
4. staff tools: `features/admin`, including the Studio.

Corrections go into the catalogs. The keys stay the same, so nothing else changes.
