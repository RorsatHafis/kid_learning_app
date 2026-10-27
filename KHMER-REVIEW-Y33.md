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

---

# Iteration 2 additions (design iteration 2, 2026-10-16)

Also drafted by Claude, not reviewed by a native speaker (PD-14). Generated from the shipped catalogs, so
the Khmer below is exactly what the interface shows. `{name}`, `{n}` and similar are filled in by the app;
where an entry has a singular and a plural English form they are separated by " / ".

## 1. The child's home: choosing a subject - `frontend/src/features/child/entrance.messages.ts`

Seen by the child on /learn/:childId, the entrance with one door per subject. The child is addressed as ប្អូន.

| Key | English | Khmer |
|---|---|---|
| `ask` | What shall we learn today? | ថ្ងៃនេះ យើងរៀនអ្វី? |
| `askOne` | Your learning is ready. | ការរៀនរបស់ប្អូនរួចរាល់ហើយ។ |
| `subjects` | My subjects | មុខវិជ្ជារបស់ខ្ញុំ |
| `keepGoing` | Keep going: {title} | បន្តទៀត៖ {title} |
| `keepGoingShort` | Keep going | បន្តទៀត |
| `start` | Start here | ចាប់ផ្ដើមនៅទីនេះ |
| `allDone` | All done for now | រួចរាល់សម្រាប់ពេលនេះ |
| `booksLit` | {done} of {total} done | បានធ្វើ {done} ក្នុងចំណោម {total} |
| `open` | {subject}: {state} | {subject}៖ {state} |
| `allSubjects` | All my subjects | មុខវិជ្ជាទាំងអស់របស់ខ្ញុំ |

## 2. Landing page: audiences, product screens, schools - `frontend/src/features/marketing/messages.ts` (`landing`)

Seen by visitors on the landing page.

| Key | English | Khmer |
|---|---|---|
| `landing.forFamilies` | Start as a family | ចាប់ផ្ដើមជាគ្រួសារ |
| `landing.forSchools` | For schools | សម្រាប់សាលារៀន |
| `landing.sample` | Screens from the product, with sample data. | អេក្រង់ពីផលិតផល ជាមួយទិន្នន័យគំរូ។ |
| `landing.audiences` | Who it's for | សម្រាប់អ្នកណា |
| `landing.familiesTitle` | For families | សម្រាប់គ្រួសារ |
| `landing.familiesLead` | Set up each child in a minute, choose what they learn, and see what their answers really show. | រៀបចំកូននីមួយៗក្នុងមួយនាទី ជ្រើសរើសអ្វីដែលពួកគេរៀន ហើយមើលឃើញអ្វីដែលចម្លើយរបស់ពួកគេបង្ហាញពិតប្រាកដ។ |
| `landing.familiesPoints.a` | Choose the subjects for each child, and change them any time | ជ្រើសរើសមុខវិជ្ជាសម្រាប់កូននីមួយៗ ហើយផ្លាស់ប្ដូរបានគ្រប់ពេល |
| `landing.familiesPoints.b` | A world of their own, with a buddy they choose | ពិភពផ្ទាល់ខ្លួនរបស់ពួកគេ ជាមួយមិត្តរៀនដែលពួកគេជ្រើសរើស |
| `landing.familiesPoints.c` | Skills, the evidence behind them, and why each next step was chosen | ជំនាញ ភស្តុតាងពីក្រោយវា និងហេតុអ្វីជំហានបន្ទាប់នីមួយៗត្រូវបានជ្រើសរើស |
| `landing.familiesCta` | Create a family account | បង្កើតគណនីគ្រួសារ |
| `landing.schoolsTitle` | For schools | សម្រាប់សាលារៀន |
| `landing.schoolsLead` | Classes, teachers and families on one platform - with each class's learning drawn from the children's own answers. | ថ្នាក់រៀន គ្រូ និងគ្រួសារនៅលើវេទិកាតែមួយ - ជាមួយការរៀនរបស់ថ្នាក់នីមួយៗ ដែលយកចេញពីចម្លើយផ្ទាល់របស់កុមារ។ |
| `landing.schoolsPoints.a` | Families join a class with its code, and can end access at any time | គ្រួសារចូលរួមថ្នាក់ដោយប្រើលេខកូដរបស់វា ហើយអាចបញ្ចប់ការចូលប្រើបានគ្រប់ពេល |
| `landing.schoolsPoints.b` | Each class's skills, and how the class moved week by week | ជំនាញរបស់ថ្នាក់នីមួយៗ និងរបៀបដែលថ្នាក់បានផ្លាស់ប្ដូរពីមួយសប្ដាហ៍ទៅមួយសប្ដាហ៍ |
| `landing.schoolsPoints.c` | Practice assigned to the children who need it | លំហាត់ដែលកំណត់ឱ្យកុមារដែលត្រូវការវា |
| `landing.schoolsCta` | Register your school | ចុះឈ្មោះសាលារៀនរបស់អ្នក |
| `landing.shotChild` | A child's home: their subjects and the buddy they chose. | ទំព័រដើមរបស់កុមារ៖ មុខវិជ្ជារបស់ពួកគេ និងមិត្តរៀនដែលពួកគេបានជ្រើសរើស។ |
| `landing.shotQuestion` | Inside an activity: one question at a time. | នៅក្នុងសកម្មភាព៖ សំណួរមួយម្ដងៗ។ |
| `landing.shotParent` | A parent's home: what each child learns, and what the answers show. | ទំព័រដើមរបស់ឪពុកម្ដាយ៖ អ្វីដែលកូននីមួយៗរៀន និងអ្វីដែលចម្លើយបង្ហាញ។ |
| `landing.shotParentChild` | One child's page: the learning plan, why the next step is next, and what changed. | ទំព័ររបស់កូនម្នាក់៖ ផែនការរៀន ហេតុអ្វីជំហានបន្ទាប់ជាជំហានបន្ទាប់ និងអ្វីដែលបានផ្លាស់ប្ដូរ។ |
| `landing.shotTeacher` | A teacher's view of a class: where its skills stood at the end of each week. | ទិដ្ឋភាពរបស់គ្រូលើថ្នាក់មួយ៖ កន្លែងដែលជំនាញរបស់វាឈរនៅចុងសប្ដាហ៍នីមួយៗ។ |
| `landing.seeParent` | What a parent sees | អ្វីដែលឪពុកម្ដាយមើលឃើញ |
| `landing.schoolCapabilities.a` | A school overview for the principal: what is still to set up, and how each class is learning. | ទិដ្ឋភាពទូទៅនៃសាលារៀនសម្រាប់នាយកសាលា៖ អ្វីដែលនៅត្រូវរៀបចំ និងរបៀបដែលថ្នាក់នីមួយៗកំពុងរៀន។ |
| `landing.schoolCapabilities.b` | Class codes that families use - and that families can end at any time. | លេខកូដថ្នាក់ដែលគ្រួសារប្រើ - ហើយដែលគ្រួសារអាចបញ្ចប់បានគ្រប់ពេល។ |
| `landing.schoolCapabilities.c` | Each class's skills from its children's answers, with how much evidence stands behind them. | ជំនាញរបស់ថ្នាក់នីមួយៗពីចម្លើយរបស់កុមារ ជាមួយនឹងបរិមាណភស្តុតាងពីក្រោយវា។ |
| `landing.schoolCapabilities.d` | The class over four, eight or twelve weeks, worked out from the answers given by then. | ថ្នាក់ក្នុងរយៈពេលបួន ប្រាំបី ឬដប់ពីរសប្ដាហ៍ ដែលគណនាពីចម្លើយដែលបានផ្ដល់រហូតដល់ពេលនោះ។ |
| `landing.schoolCapabilities.e` | Practice assigned to the children who need it, from the skill's own page. | លំហាត់ដែលកំណត់ឱ្យកុមារដែលត្រូវការវា ពីទំព័ររបស់ជំនាញនោះផ្ទាល់។ |
| `landing.schoolCapabilities.f` | Teachers can author their own questions and activities for their classes. | គ្រូអាចនិពន្ធសំណួរ និងសកម្មភាពផ្ទាល់ខ្លួនសម្រាប់ថ្នាក់របស់ពួកគេ។ |

## 3. Parent pages: renamed and corrected lines - `frontend/src/features/parent/messages.ts`

Seen by parents on the child page and the family home.

| Key | English | Khmer |
|---|---|---|
| `home.noChildrenLead` | Each child gets their own path, their own buddy, and a page here for you. | កូននីមួយៗមានផ្លូវរៀនផ្ទាល់ខ្លួន មិត្តរៀនផ្ទាល់ខ្លួន និងទំព័រមួយនៅទីនេះសម្រាប់អ្នក។ |
| `learningNow.title` | Learning plan | ផែនការរៀន |
| `learningNow.subtitle` | The subjects {name} learns, what comes next, and why. | មុខវិជ្ជាដែល {name} រៀន អ្វីដែលនឹងមកបន្ទាប់ និងហេតុអ្វី។ |

## 4. Adding a child and the learning plan - `frontend/src/features/parent/setup.messages.ts`

Seen by parents: /parent/add-child (three steps), the family home's child summaries and actions, and the child page's sections.

| Key | English | Khmer |
|---|---|---|
| `title` | Add a child | បន្ថែមកូន |
| `steps.about` | About your child | អំពីកូនរបស់អ្នក |
| `steps.subjects` | Subjects | មុខវិជ្ជា |
| `steps.review` | Review | ពិនិត្យ |
| `stepOf` | Step {n} of {total} | ជំហាន {n} នៃ {total} |
| `about.heading` | Who is learning? | តើអ្នកណាកំពុងរៀន? |
| `about.lead` | Their age decides which paths suit them. Only you and your family can see this. | អាយុរបស់ពួកគេកំណត់ថាផ្លូវរៀនណាសមស្រប។ មានតែអ្នក និងគ្រួសាររបស់អ្នកប៉ុណ្ណោះដែលអាចមើលឃើញព័ត៌មាននេះ។ |
| `about.name` | Name | ឈ្មោះ |
| `about.nameHint` | The name your child knows - it's how their space greets them. | ឈ្មោះដែលកូនរបស់អ្នកស្គាល់ - គឺជារបៀបដែលកន្លែងរបស់ពួកគេស្វាគមន៍ពួកគេ។ |
| `about.dateOfBirth` | Date of birth | ថ្ងៃខែឆ្នាំកំណើត |
| `about.next` | Next: choose subjects | បន្ទាប់៖ ជ្រើសរើសមុខវិជ្ជា |
| `about.creating` | Adding… | កំពុងបន្ថែម… |
| `about.cancel` | Cancel | បោះបង់ |
| `about.failed` | Couldn't add your child. | មិនអាចបន្ថែមកូនរបស់អ្នកបានទេ។ |
| `about.somethingWrong` | Something went wrong. Please try again. | មានបញ្ហាអ្វីមួយបានកើតឡើង។ សូមព្យាយាមម្ដងទៀត។ |
| `subjects.heading` | What will {name} learn? | តើ {name} នឹងរៀនអ្វីខ្លះ? |
| `subjects.lead` | Choose the subjects to start with. You can add or stop a subject at any time from their learning plan. | ជ្រើសរើសមុខវិជ្ជាដើម្បីចាប់ផ្ដើម។ អ្នកអាចបន្ថែម ឬបញ្ឈប់មុខវិជ្ជាណាមួយបានគ្រប់ពេល ពីផែនការរៀនរបស់ពួកគេ។ |
| `subjects.loading` | Finding subjects… | កំពុងស្វែងរកមុខវិជ្ជា… |
| `subjects.forAge` | Made for {name}'s age | រៀបចំសម្រាប់អាយុរបស់ {name} |
| `subjects.otherAges` | {n} paths for other ages | ផ្លូវរៀន {n} សម្រាប់អាយុផ្សេងទៀត |
| `subjects.ages` | Ages {min}+ / Ages {min}-{max} | អាយុ {min}+ / អាយុ {min}-{max} |
| `subjects.chosen` | Chosen | បានជ្រើសរើស |
| `subjects.tapToChoose` | Tap to choose | ចុចដើម្បីជ្រើសរើស |
| `subjects.none` | Nothing is published for {name}'s age yet. When a subject is ready, you can add it from {name}'s learning plan. | មិនទាន់មានអ្វីបានផ្សាយសម្រាប់អាយុរបស់ {name} នៅឡើយទេ។ នៅពេលមុខវិជ្ជាណាមួយរួចរាល់ អ្នកអាចបន្ថែមវាពីផែនការរៀនរបស់ {name}។ |
| `subjects.needOne` | Choose a subject, or decide later. | ជ្រើសរើសមុខវិជ្ជាមួយ ឬសម្រេចចិត្តពេលក្រោយ។ |
| `subjects.next` | Next: review the plan | បន្ទាប់៖ ពិនិត្យផែនការ |
| `subjects.later` | Decide later | សម្រេចចិត្តពេលក្រោយ |
| `subjects.count` | {n} subjects chosen | បានជ្រើសរើសមុខវិជ្ជា {n} |
| `review.heading` | {name}'s learning plan | ផែនការរៀនរបស់ {name} |
| `review.lead` | This is what {name} will find in their own space. Each subject starts at its first chapter, and the platform adapts from {name}'s answers. | នេះជាអ្វីដែល {name} នឹងឃើញនៅក្នុងកន្លែងរបស់ពួកគេ។ មុខវិជ្ជានីមួយៗចាប់ផ្ដើមពីជំពូកទីមួយ ហើយប្រព័ន្ធសម្របតាមចម្លើយរបស់ {name}។ |
| `review.none` | No subjects yet. You can add them any time from {name}'s learning plan. | មិនទាន់មានមុខវិជ្ជានៅឡើយទេ។ អ្នកអាចបន្ថែមវាបានគ្រប់ពេល ពីផែនការរៀនរបស់ {name}។ |
| `review.start` | Start {name}'s learning | ចាប់ផ្ដើមការរៀនរបស់ {name} |
| `review.finish` | Finish | បញ្ចប់ |
| `review.starting` | Starting… | កំពុងចាប់ផ្ដើម… |
| `review.back` | Back to subjects | ត្រឡប់ទៅមុខវិជ្ជា |
| `review.failedSome` | These subjects couldn't be started: | មុខវិជ្ជាទាំងនេះមិនអាចចាប់ផ្ដើមបានទេ៖ |
| `review.retry` | Try again | ព្យាយាមម្ដងទៀត |
| `review.continue` | Continue without them | បន្តដោយគ្មានពួកវា |
| `home.learning` | Learning | ការរៀន |
| `home.practice` | May need more practice | ប្រហែលជាត្រូវការហាត់បន្ថែម |
| `home.practiceNone` | Nothing flagged. A skill is flagged only once there are enough answers to judge. | មិនទាន់មានអ្វីត្រូវកត់សម្គាល់ទេ។ ជំនាញមួយត្រូវបានកត់សម្គាល់ លុះត្រាតែមានចម្លើយគ្រប់គ្រាន់ដើម្បីវាយតម្លៃ។ |
| `home.manage` | Manage subjects | គ្រប់គ្រងមុខវិជ្ជា |
| `home.chooseSubjects` | Choose {name}'s subjects | ជ្រើសរើសមុខវិជ្ជារបស់ {name} |
| `home.standingsLabel` | {name}'s skills by standing | ជំនាញរបស់ {name} តាមកម្រិត |
| `home.skillsCount` | {n} skills | ជំនាញ {n} |
| `home.doTitle` | Things you can do | អ្វីដែលអ្នកអាចធ្វើបាន |
| `home.addChild` | Add a child | បន្ថែមកូន |
| `home.addChildLead` | Set up their space and choose what they learn. | រៀបចំកន្លែងរបស់ពួកគេ និងជ្រើសរើសអ្វីដែលពួកគេរៀន។ |
| `home.account` | Your plan and account | គម្រោង និងគណនីរបស់អ្នក |
| `home.accountLead` | Plan, sign-in, language and your family's data. | គម្រោង ការចូលគណនី ភាសា និងទិន្នន័យគ្រួសាររបស់អ្នក។ |
| `home.school` | Join a class with a code | ចូលរួមថ្នាក់ដោយប្រើលេខកូដ |
| `home.schoolLead` | Share {name}'s school-assigned learning with a teacher. | ចែករំលែកការរៀនដែលសាលាកំណត់របស់ {name} ជាមួយគ្រូ។ |
| `child.tabs` | Sections | ផ្នែក |
| `child.overview` | Overview | ទិដ្ឋភាពទូទៅ |
| `child.progress` | Skills & progress | ជំនាញ និងវឌ្ឍនភាព |
| `child.settings` | School & settings | សាលា និងការកំណត់ |
| `child.space` | Their own space | កន្លែងផ្ទាល់ខ្លួនរបស់ពួកគេ |
| `child.spaceLead` | {name} chooses their buddy and the language of their space themselves. | {name} ជ្រើសរើសមិត្តរៀន និងភាសានៃកន្លែងរបស់ពួកគេដោយខ្លួនឯង។ |
| `child.buddy` | Buddy: {buddy} | មិត្តរៀន៖ {buddy} |
| `child.language` | Language: {language} | ភាសា៖ {language} |
| `child.openSpace` | Open {name}'s space | បើកកន្លែងរបស់ {name} |
| `plan.title` | Learning plan | ផែនការរៀន |
| `plan.lead` | The subjects {name} learns. Adding one starts it at its first chapter; stopping one keeps everything {name} has done. | មុខវិជ្ជាដែល {name} រៀន។ ការបន្ថែមមួយចាប់ផ្ដើមវាពីជំពូកទីមួយ ការបញ្ឈប់មួយរក្សាទុកអ្វីៗទាំងអស់ដែល {name} បានធ្វើ។ |
| `plan.add` | Add a subject | បន្ថែមមុខវិជ្ជា |
| `plan.empty` | {name} has no subjects yet. | {name} មិនទាន់មានមុខវិជ្ជានៅឡើយទេ។ |
| `plan.emptyLead` | Choose what to start with - it appears in their space straight away. | ជ្រើសរើសអ្វីដែលត្រូវចាប់ផ្ដើម - វានឹងបង្ហាញនៅក្នុងកន្លែងរបស់ពួកគេភ្លាមៗ។ |

Uncertain throughout: whether the parent-facing lines read naturally (they were written to match the
English closely), and whether មិត្តរៀន is the right word for a Learning Buddy. The landing page's
school capabilities are product claims and must stay true to what the product does - a reviewer
should change wording, not meaning.

# Y35 additions (plans for schools and storage, 2026-10-16)

All Claude-drafted, none native-reviewed (PD-14). The English is the reference. Most of these are
for staff (principals, teachers, administrators). None of them is shown to a child.

- `frontend/src/features/school/plan.messages.ts`: the principal's "Your school's plan" card
  (where the plan comes from, usage "N of M", Full/Over/Nearly full, "nothing is removed").
- `frontend/src/features/admin/studio/storage.messages.ts`: the storage meter in the media
  library (allowance sources, full/over states, what takes the space).
- `frontend/src/features/admin/operations/storage.messages.ts`: the administrator's Storage page
  and allowance exceptions.
- `frontend/src/features/admin/operations/schoolPlan.messages.ts`: giving, moving and ending a
  school's plan.
- `frontend/src/features/admin/operations/plans.messages.ts`: the For families / For schools
  tabs, school plan wording.
- `frontend/src/lib/api/messages.ts`: names of the four school limits.
- `frontend/src/features/admin/operations/auditCodes.ts`: SCHOOL_SUBSCRIPTION_*,
  STORAGE_ALLOWANCE_*.
- `backend/src/main/java/com/platform/common/web/ServerText.java`: the school limit messages and
  "Your storage is full: …" (pattern rules keep the numbers).

Uncertain: whether កន្លែងផ្ទុក is the right word for storage in this sense, and whether "arranged with
the Bookeyond team" (បានរៀបចំជាមួយក្រុម Bookeyond) reads naturally.

## Added 2026-10-27 (F1c, F1d) - public pages

Drafted by Claude, not reviewed (PD-14). All are editable without code in Operations -> Landing page ("Words on the
public pages"), so a reviewer's corrections can also be entered there; the catalog file is the default.

- `frontend/src/features/marketing/parentDemo.messages.ts` - the whole page `/try/parent` ("What parents see"): a
  fictional sample child, **មាលា (Mealea)** - please check the name is natural and not a well-known person; the notes
  explain "secure needs another day" and "an answer after the hint is practice" - the two rules most likely to read
  awkwardly.
- `frontend/src/features/marketing/messages.ts`:
  - `how.*` new entries (`twoSoFar`, `secureNote`, `demoLabel`, `question`, `answerKept`, `play`, `pause`) and the
    changed `sampleStats` - the "How it works" demonstration.
  - `parents.lead`, `parents.strengths.*.text`, `parents.rule1When`...`rule4Then` (rewritten to match the adaptive
    engine), `parents.seeSample`.
  - `children.tryChoose` - introduces tapping a buddy. The buddy then answers with the child's own sentence
    (`buddy.chosen`, addressing the child as ប្អូន) although the reader of the landing page is usually a parent: please
    say whether that reads as intended ("this is what your child would hear") or needs a different line.
  - `promises.closingTitle`, `nav.tagline` - the same words as `common.tagline`, now editable for the public pages.
- `frontend/src/features/marketing/try.messages.ts`: `parentDemo` ("Parents: see what you'd see").
- `frontend/src/features/admin/operations/siteCopy.messages.ts`: `groups.parentDemo`.

Checked in Khmer in a browser: `/try/parent` and the "How it works", "For parents" and "A world of their own" sections
at 360 px (and 1280 px for the buddies) - no overflow, no clipped text, no axe violations.
