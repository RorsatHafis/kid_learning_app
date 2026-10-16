# Khmer review sheet — River Garden redesign (Y33)

**Status: drafted by Claude, not reviewed by a native speaker.** PD-14 still applies: this Khmer
must not be offered to real families until a native-speaking editor has checked it. This sheet
lists only the text the redesign added or changed (the rest of the interface is covered by the
order in LOCALIZATION.md → "Native-speaker review"), so a reviewer can work through it in one pass.

How it was found: every catalog was loaded and compared with the commit before the redesign
(`6654fd0`); 33 entries were added or changed, plus one stale line fixed afterwards. All of them
pass the catalog parity test (same keys and values in both languages). The screens were checked in
Khmer in the browser at 1440, 390 and 320 px: no broken glyphs, no clipped or overlapping Khmer, no
sideways scrolling.

Corrections go into the catalog files named below. Keep the keys; change only the Khmer.

`{name}`, `{n}` and similar are filled in by the app. The child is addressed as ប្អូន, as everywhere
else in the child's space (Y32).

## 1. Learning Buddies — `frontend/src/features/buddy/buddy.messages.ts`

Seen by the child on "Choose your buddy" (`/learn/:childId/buddy`) and on the landing page.

### Names (proposals, PD-13 — not approved)

| Buddy | English | Khmer | Note for the reviewer |
|---|---|---|---|
| Otter | Momo | ម៉ូម៉ូ | A given name in both languages. |
| Elephant | Domrey | ដំរី | **In Khmer this is the species word, not a name.** "Domrey" reads as a name to an English reader, but a Khmer child sees "Elephant". Same for the three below. |
| Water buffalo | Krobey | ក្របី | Species word in Khmer. |
| Gibbon | Toch | ទោច | Species word in Khmer. Please check that ទោច is the word children know for a gibbon. |
| Frog | Kongkep | កង្កែប | Species word in Khmer. |

The question for the owner and reviewer: should each buddy have a personal name (like Momo), or is
"the elephant" etc. the intended way children name them? See the recommendation in REDESIGN-STATUS.md.

### Species words (`kinds`)

| English | Khmer | Note |
|---|---|---|
| otter | ភេ | |
| elephant | ដំរី | |
| water buffalo | ក្របី | |
| gibbon | ទោច | |
| frog | កង្កែប | |

### Character lines (one sentence under each buddy)

| English | Khmer |
|---|---|
| A curious otter who loves to swim and to ask why. | ភេដែលចង់ដឹងចង់ឃើញ ចូលចិត្តហែលទឹក និងសួរថា ហេតុអ្វី។ |
| A gentle elephant who never forgets a new word. | ដំរីស្លូតបូត ដែលមិនដែលភ្លេចពាក្យថ្មីទេ។ |
| A calm water buffalo: one step at a time. | ក្របីស្ងប់ស្ងាត់៖ មួយជំហានម្ដងៗ។ |
| A gibbon who swings from tree to tree, counting every branch. | ទោចដែលយោលពីដើមឈើមួយទៅដើមឈើមួយ ទាំងរាប់មែកឈើគ្រប់មែក។ |
| A frog who sings by the lotus pond every morning. | កង្កែបដែលច្រៀងក្បែរស្រះឈូករៀងរាល់ព្រឹក។ |

Uncertain: whether these sentences read naturally to a 4–9-year-old. The buffalo line copies the
English colon (៖); a full sentence may be more natural.

### Chooser

| Where | English | Khmer | Note |
|---|---|---|---|
| Page heading | Choose your buddy | ជ្រើសរើសមិត្តរបស់ប្អូន | |
| Under the heading | Your buddy walks your path with you. | មិត្តរបស់ប្អូនដើរតាមផ្លូវរៀនជាមួយប្អូន។ | |
| List name (screen reader) | Buddies | មិត្តរៀន | "Learning friend" — also used for the buddy on the parent page (section 3). Is this the right word for "buddy"? |
| Badge on the chosen card | My buddy | មិត្តរបស់ខ្ញុំ | |
| Button name (screen reader) | Choose {name} | ជ្រើស {name} | |
| Announced after saving | {name} is your buddy now! | ឥឡូវ {name} ជាមិត្តរបស់ប្អូនហើយ! | |
| While saving | Saving… | កំពុងរក្សាទុក… | |
| If saving fails | That didn't save. Try again. | មិនបានរក្សាទុកទេ។ សូមព្យាយាមម្ដងទៀត។ | |
| Back link | Back to my path | ត្រឡប់ទៅផ្លូវរៀនរបស់ខ្ញុំ | |
| Buddy button in the top bar (screen reader) | My buddy: {name} | មិត្តរបស់ខ្ញុំ៖ {name} | |

## 2. The child's activity — `frontend/src/features/child/messages.ts` (`play`)

| Where | English | Khmer | Note |
|---|---|---|---|
| Activity intro, under the buds | {n} questions | សំណួរ {n} | Was "… · light the lanterns"; the lanterns left the activity. |
| After the last answer | Your lotus is open. | ផ្កាឈូករបស់ប្អូនរីកហើយ។ | The pond's lotuses open as chapters finish. Is រីក right for a lotus opening? |
| Completion heading | You finished "{title}". | ប្អូនបានបញ្ចប់ «{title}»។ | `{title}` is the activity's own title, which can be English (content is not translated: PD-15). |

## 3. Parent pages — `frontend/src/features/parent/messages.ts` (`home`)

| Where | English | Khmer | Note |
|---|---|---|---|
| Family home, label over the skill tally | What the answers show so far | អ្វីដែលចម្លើយបង្ហាញរហូតមកដល់ពេលនេះ | |
| Family home, tally before any answers | No answers yet - this fills in as they play. | មិនទាន់មានចម្លើយនៅឡើយទេ - ផ្នែកនេះនឹងបំពេញ នៅពេលពួកគេលេង។ | Hyphen copied from English; Khmer punctuation may be better. |
| Family home, one tally item | {n} {standing} | {standing} {n} | Word order swapped for Khmer, e.g. «រឹងមាំ 2», «នៅដើមពេកដើម្បីដឹង 1». Please check the order reads naturally with each standing. |
| Family home with no children yet | Each child gets their own path, their own buddy, and a page here for you. | កូននីមួយៗមានផ្លូវរៀនផ្ទាល់ខ្លួន មិត្តរៀនផ្ទាល់ខ្លួន និងទំព័រមួយនៅទីនេះសម្រាប់អ្នក។ | Previously promised a firefly (អំពិលអំពែក), which no longer exists. |

## 4. Landing page — `frontend/src/features/marketing/messages.ts` (`children`)

| Where | English | Khmer |
|---|---|---|
| "For children" section, lead | A Cambodian village in the morning, a path of books that light up as they learn, and an animal friend they choose to walk it with. | ភូមិខ្មែរមួយនៅពេលព្រឹក ផ្លូវសៀវភៅដែលភ្លឺឡើងនៅពេលពួកគេរៀន និងមិត្តសត្វម្នាក់ដែលពួកគេជ្រើសរើស ដើម្បីដើរតាមផ្លូវនោះជាមួយគ្នា។ |
| Same section, second point | Their buddy is a character we wrote — not a chatbot. | មិត្តរបស់ពួកគេគឺជាតួអង្គដែលយើងបានសរសេរ — មិនមែនជាកម្មវិធីជជែកទេ។ |

Uncertain: whether ម្នាក់ (a classifier for people) suits an animal friend, or another classifier
reads better.

## Not Khmer issues, noted so they aren't mistaken for missing translations

- Activity, chapter and curriculum titles ("Addition Recognition Practice", "Maths - Junior") show
  in English on Khmer screens. That is educator content, which has no language yet (PD-15,
  LOCALIZATION.md → "Content translation: still to build").
- The landing hero deliberately shows the line in the other language above the heading (tagged
  with its `lang`).
- "Smart Review" stays in Latin script as a product name (Y32).
