# Bookeyond Kid — business model and Cambodian context (decision brief, 2026-10-18)

What this covers: the business model as the code actually implements it, what credible comparable
products do, the Cambodian legal and child-wellbeing context, what was implemented as a result,
and the decisions that remain the owner's. Sources are listed at the end with the date each was
read (all on 2026-10-18).

## 1. What exists today (from the code, not from intentions)

| Customer | What they get now | Limits | Payment |
|---|---|---|---|
| Family (parent + children) | The child space (subjects for the child's age, the River Garden path, activities, review, Learning Buddy). The parent sees evidence-based progress, can stop or restart paths, export data and delete. | Default plan "Early access": **no limits**. Configurable levers are `CHILDREN_MAX` and `JOURNEYS_PER_CHILD_MAX`. | None. A family can **ask** for a self-service plan, and an administrator answers (Y11). |
| School (principal, teachers) | Classes with join codes and family-granted access. Teachers see class learning, assign curricula and author their own content and media. Principals get school oversight and trends. | Default "School early access": **no limit** on children, teachers or classes. **1024 MB of media per teacher** (seeded, a recommendation). | None. School plans are given by an administrator. |
| Platform | Admin operations: plans, grants, storage, content studio and review, audit. | n/a | n/a |

Assumptions still visible in the data (and now editable in Admin → Plans):
- Families and schools are unlimited by default.
- No price is set anywhere.
- Storage applies only to authors: teachers and principals.

## 2. What comparable products do (and what that suggests)

- **Free, donor-funded children's learning** sets the floor of what families compare against.
  Khan Academy Kids (ages 2–8) is "100% free — no ads, no subscriptions".
- **Family subscriptions sell content breadth, not caps.** ABCmouse sells one family membership
  with up to 3 child profiles: monthly $14.99 or yearly $45 on the web. IXL prices per subject
  bundle and adds about $4 a month for each additional child (secondary sources).
- **School products sell places, classes and administration, priced by enrolment and quoted.**
  Seesaw's free Starter (from 30 June 2026) allows 1 active class, up to 35 students and 1
  teacher per class. Multiple classes, co-teaching and administrator insights need a school
  subscription, "based on school size (student enrolment)", from US$2,500, and there is no
  individual-teacher upgrade. Limits apply to new work only; existing classes and student work are
  untouched. That is the same rule Bookeyond Kid enforces.
- **Storage is a cost safeguard, not a selling point.** Object storage costs about US$0.025 per
  GB-month in Singapore (secondary sources). With a backup copy, a teacher's 1 GB costs well under
  US$0.10 a month. Storage should protect disks from abuse and be visible to authors; it is not a
  reason to pay.

**Conclusions for Bookeyond Kid**
1. **Families**:
   - *Value*: what the child can learn (subjects and journeys) and how many children the family
     can include.
   - *Levers*: `CHILDREN_MAX` and `JOURNEYS_PER_CHILD_MAX`, already implemented.
   - *Storage*: never; families don't upload.
   - *Never gate*: a child's evidence, progress history, export or deletion (learning
     integrity, and the family's data is theirs).
2. **Schools**:
   - *Value*: places for children, teachers and classes, plus the principal's oversight.
   - *Levers*: the three place limits, implemented in Y35.
   - *Storage*: a per-teacher fair-use allowance with audited exceptions. No school pays for
     storage tiers.
   - *Pricing*: quoted by enrolment, given by an administrator. Not self-service (now enforced).
3. **Sponsored or institutional access** (an NGO, a ministry programme or a telco partner):
   - Use the school or family plans that already exist, given by an administrator for a period.
   - No third model, so partners can't distort the family and school experiences.
4. **Contradictions to prevent**:
   - a plan with another audience's limits;
   - a school plan offered to families;
   - a family-facing storage figure;
   - a retired plan given anew;
   - a plan change that deletes anything.
   All of these are now refused by the server.

## 3. Cambodian context

| Finding | Kind | What it means here |
|---|---|---|
| **Personal data protection law**: a first comprehensive draft was published by the Ministry of Post and Telecommunications on 23 July 2025. It is GDPR-like, with a 2-year transition (Art. 54 of the draft). Its enactment is **not confirmed** by any source found (latest: "not yet enacted" as of Sept 2025). | Draft law: **not binding** yet | Build to its likely shape now: minimal data, purpose, consent by the parent, export and deletion, security. These exist already (export, erasure with a ledger, audit). Re-check the status before launch (PD-31). |
| **Cambodian Child Online Protection Guidelines for the Digital Technology Industry**, MPTC, launched 6 July 2023 with UNICEF. | Official **guidelines**: not a statute | Expect child-rights impact assessment, safeguarding policy, prevention of exploitation, and a way to report harm. Bookeyond Kid has no child-to-child contact, no user-generated content shown to children, no ads and no chat, which removes the main risk surfaces. A child-safety contact and policy are an owner action (PD-32). |
| **MoEYS Instruction No. 50** (2023): phones and devices not used for learning are prohibited in classrooms during study hours, and parents are asked to guide children to use devices for study. | Ministerial instruction: **binding in public schools** | School use must be teacher-led learning, which is what the class features are. Nothing in the product should encourage phone use during lessons outside a teacher's activity. No change needed; worth stating to schools. |
| **Digital Education Strategy for Schools 2024–2030** (MoEYS). | Official strategy | Schools are expected to adopt digital learning. This supports the school offer; it doesn't oblige anything of a private provider. |
| **Consumer Protection Law and E-Commerce Law** (both 2 Nov 2019). | **Binding** | When payments arrive: clear prices, terms and cancellation. Never present a paid upgrade before prices and terms exist; that is already the rule (no checkout is faked). |
| **Screen time**: no Cambodian law sets a universal screen-time limit for children (none was found, and none is claimed here). WHO (2019): for ages 2–4, no more than 1 hour of sedentary screen time a day; none for under-2s. AAP: a family media plan; for ages 2–5 about 1 hour a day of high-quality content. | Public-health **guidance**: not law | Sessions should be short and end at natural points. Parents should set the rhythm, and the product must never push for more time. |
| Smartphone dependency among Cambodian children has been raised by UNICEF (reported 2021); there is no national framework on children's smartphone use (op-ed, futureforum.asia). | Advocacy and commentary | Parents need simple controls without surveillance. The product's role is good learning in short sessions, not maximum use. |

## 4. Implemented from this brief

- **Server rules against contradictory plans** (Y35 and this phase):
  - audience-bound keys and grants;
  - school plans can't be self-service;
  - nothing is removed by a limit or a plan change.
- **Break reminders, set by the parent** (Y36):
  - *Need*: parents want short sessions without nagging or locking a child out mid-question
    (WHO and AAP guidance above).
  - *Design*: off by default, so it is the parent's choice. When on, it fires only between
    activities. The buddy suggests a break, and the child can stop or do one more. There is no
    timer pressure, no countdown and no penalty.
  - *Trade-off*: a child can choose "one more"; the reminder is guidance, not a lock.
  - Hard daily limits would need device-level enforcement, which belongs to Bookeyond's
    parental-control product. That is recorded for integration, not duplicated here.
- **Honest plan explanation on the landing page**:
  - "Early access": everything included, nothing to pay, no card asked.
  - Families and schools explained separately.
  - No price is shown until the owner sets one.

## 5. Owner decisions (only genuine ones)

| # | Decision | Recommendation |
|---|---|---|
| PD-09 | Prices, currency (USD/KHR), payment provider (e.g. ABA PayWay/KHQR), trials, refunds | Families: one membership covering up to 3 children, monthly and yearly, priced against local purchasing power, with a free tier that keeps one subject. Schools: quoted by enrolment. Decide before any paid launch. |
| PD-31 | Re-check the Personal Data Protection Law's status and obligations before launch, with counsel | Keep the current minimal-data design. Map consent, retention and breach notice once the text is final. |
| PD-32 | Child-safety policy, contact for reports, and a child-rights impact assessment (MPTC 2023 guidelines) | Publish a short safeguarding page with a monitored contact. |
| PD-33 | Default for break reminders on new children (now off) | Consider suggesting 20 minutes in the add-a-child flow for children under 6. |
| PD-34 | Official Bookeyond logo files, and how "Kid" sits beside the parent brand | Supply the vector logo. The app's logo is one component, so swapping it is a one-file change. |
| PD-35 | Date of birth precision: the product needs only age (month and year would do) | Keep it until the PDP law is final, then consider month and year only. That would be a data migration. |
| (open) | The 1024 MB per-teacher allowance | Keep it as fair use, and revisit with real usage from Operations → Storage after a term. |

## Sources (read 2026-10-18)

- Seesaw, changes to free accounts: https://seesaw.com/pricing-packages/free-changes/
- Khan Academy Kids (official): https://learn.khanacademy.org/khan-academy-kids/ ; App Store: https://apps.apple.com/US/app/id1378467217
- ABCmouse pricing (official): https://www.abcmouse.com/learn/?p=58819 ; support: https://support.abcmouse.com/hc/en-us/articles/34385411866391
- IXL additional-child pricing (secondary): https://nibble-app.com/blog/ixl-cost ; https://time4learning.com/vs/ixl/cost.html
- S3 pricing (secondary): https://upstash.com/blog/aws-s3-pricing-explained-2026 (confirm at https://aws.amazon.com/s3/pricing/)
- Cambodia draft PDP law: https://ppc.land/cambodia-announces-comprehensive-data-protection-law/ ; https://data.opendevelopmentcambodia.net/laws_record/draft-law-on-personal-data-protection ; https://multilaw.com/Multilaw/Multilaw/Data_Protection_Laws_Guide/DataProtection_Guide_Cambodia.aspx
- MPTC Child Online Protection Guidelines (2023): https://www.unicef.org/cambodia/press-releases/cambodia-launches-child-online-protection-guidelines-digital-industry-ensure-safety ; PDF: https://www.unicef.org/cambodia/media/7751/file/Cambodian%20Child%20Online%20Protection%20Guidelines.pdf
- MoEYS Instruction No. 50: https://data.opendevelopmentcambodia.net/laws_record/instruction-no-50-on-strengthening-discipline-and-enhancing-the-effectiveness-of-using-electronic-d ; https://opendevelopmentcambodia.net/news/ministry-bans-use-of-mobile-phones-electronic-devices-in-classrooms/
- MoEYS Digital Education Strategy for Schools 2024–2030: https://aipalync.org/document/strategy-on-digital-education-strategy-for-schools-khmer
- E-Commerce and Consumer Protection Laws 2019: https://www.tilleke.com/insights/cambodia-enacts-new-e-commerce-law-and-consumer-protection-law/
- WHO 2019 guidelines for under-5s (summaries; primary: WHO, "Guidelines on physical activity, sedentary behaviour and sleep for children under 5 years of age"): https://www.technologynetworks.com/neuroscience/news/limit-screen-time-for-children-under-five-says-who-318671
- AAP media use, school-aged children: https://www.aafp.org/pubs/afp/issues/2017/0701/p56.html
- Smartphone use, Cambodian schools (commentary): https://www.futureforum.asia/post/op-ed-introducing-age-based-restrictions-on-unsupervised-smartphone-use-in-cambodian-schools
- Bookeyond (parent brand) on the App Store: https://apps.apple.com/app/id1492974619
