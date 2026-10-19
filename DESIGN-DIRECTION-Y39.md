# Design direction and rationale, Y39 (2026-10-19)

The program record for this transformation is `REDESIGN-PROGRAM.md`. This file answers the
direction questions and keeps research findings apart from design choices.

## 1. Who each experience is for, and what they must get at once

| Audience | Understand immediately | Do next | Prominent | Secondary |
|---|---|---|---|---|
| **Child (4–9)** | "This is my garden; my buddy is here; this is where I am" | Press the one big button, or choose a subject | The world, the buddy, the next book, what has grown | Chapters ahead, the shelf, settings |
| **Parent** | What my child is learning, what the answers show (and how sure), whether anything needs me | Start a session, open a child, act on a flagged skill | Per-child signal: learning now, what the answers show, practice flags *only when real* | Evidence tables, history, insights, settings |
| **Teacher** | Which class needs attention, and on what | Open the class, follow up a skill, author content | Classes with children needing practice | Media, storage, schools |
| **Principal** | How the school is doing, and what is still to set up | Fix set-up gaps, look at a class | The glance with to-dos, learning across classes | Plan, teachers list |
| **Administrator** | What is waiting on them | Answer it | "Waiting on you" | Platform counts, content counts |

## 2. What makes it Bookeyond

- **Story:** a Cambodian village garden by day. The child's learning is literally a path of
  books, and what they come to understand **grows**.
- **Ethic:** evidence before claims. The interface never treats completion as mastery. It tells
  parents how sure the evidence is, and it never pressures anyone (no streaks, rankings,
  currencies, ads or nags).
- **Voice:** warm, plain and bilingual; Khmer is a first-class language.

## 3. Research findings (read 2026-10-19)

| Finding | Source | Strength |
|---|---|---|
| Children 3–12 differ sharply by age band. They read instructions, but their vocabulary is limited and their reasoning is still developing, so instructions must be short, specific and built on their mental models. | NN/G, *Designing for Children* (research with 3–12 year olds): https://www.nngroup.com/videos/designing-children/ ; age-band summaries: https://uxdesign.cc/children-first-802bd12fe28c | Practitioner research; age bands second-hand |
| Feedback that gives the **correct answer** helps more than right/wrong alone (g 0.32 vs 0.05). **Elaborated** feedback (an explanation) helps most (g 0.49). Evidence for young children is thinner. | Van der Kleij, Feskens & Eggen (2015), *Review of Educational Research* 85(4): https://acuresearchbank.acu.edu.au/item/86y84/effects-of-feedback-in-a-computer-based-learning-environment-on-students-learning-outcomes-a-meta-analysis | Meta-analysis |
| **Process** praise (what the child did) supports persistence after setbacks. **Person** praise ("you're smart") is linked to choosing easier tasks and helplessness after failure. | Mueller & Dweck (1998); Kamins & Dweck (1999); Corpus & Lepper (2007): https://www.reed.edu/psychology/motivation/assets/downloads/Corpus_Lepper_2007.pdf | Lab studies, replicated in direction |
| Gamification has a **small** positive effect (g ≈ 0.26). Its value comes through perceived competence, autonomy and relatedness, not through points as such. | Meta-analysis, *Educational Technology Research and Development* (2023/24): https://link.springer.com/10.1007/s11423-023-10337-7 | Meta-analysis |
| Learning dashboards mostly build **awareness** but give little **action**. Users want "how things should be", not just "what things are". | Teacher-facing dashboard review (CADA): https://www.ncbi.nlm.nih.gov/pmc/articles/PMC8982662/ | Systematic review |
| Khmer has no authoritative line-height or size standard. Base and subjoined consonants and vowel signs must not be split; and there are no spaces between words (zero-width spaces mark the breaks). | W3C *Khmer Layout Requirements* (draft): https://www.w3.org/TR/2024/DNOTE-khmr-lreq-20240705/ | Draft guidance |

Business and Cambodian context: PRODUCT-MODEL-BRIEF.md (unchanged).

## 4. Design principles (choices informed by the findings above)

1. **One question owns the screen.** Short instructions with a **pictogram** for each kind of
   interaction (order, match, sort, choose, count), so pre-readers know what to do.
2. **Show the right answer after a mistake** (already true), in kind words that point to
   practice. Explanations and hints need author-written content that the content model doesn't
   have; that is recorded as a gap, not faked.
3. **Praise the process, never the person.** "You kept going", "That's the order", never "clever".
4. **Collect what is real.** The one collectible is the child's **garden of skills**:
   - a **sprout** for each skill they have practised;
   - a **bud** when the evidence says it is getting stronger;
   - a **bloom** when the answers show it is secure.

   It grows only from the server's standings. No points, no currency, nothing to spend.
5. **The world reacts to learning.** When an activity changes what the answers show, the
   completion page says so ("A new bloom: Add within 10"), comparing the server's standings before
   and after. Lotuses already open as chapters finish.
6. **Where am I, what can I do, what's next.** The path is a trail of chapters with the current
   one open. A review waiting is named as a chance to make something stick. A finished path
   offers only what really exists: a review, replay from the shelf, or a truthful "all done".
7. **Adults get signals, not stats.** A parent sees a signal only when the data supports it
   (practice flags with enough answers, a review due, when the child last played). Empty
   reassurance boxes are not drawn.
8. **Bilingual by construction.** Labels wrap and never clip. Khmer keeps its taller leading. No
   meaning rests on an English-only abbreviation.
