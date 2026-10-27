# Research kept for the project

| File | What it is |
|---|---|
| `Bookeyond Kid  Evidence-Based Design Research Report.pdf` | The owner's evidence-based design report (41 pages, evidence current to 26 Oct 2026): learning science, child development and wellbeing, parent trust, curriculum authoring, Cambodian context. |

The PDF is the owner's file. It stays here as provided and is **not committed** (2.6 MB, not ours to publish);
this index is. To read it as text: `pip install --target <scratch>/pdflib pypdf`, then extract page by page.

## How it is used

It is evidence, not a specification. Each conclusion in it carries a label - **[Strong]**, **[Promising]**,
**[Design judgment]**, **[Validate]** - and its own caveats: most studies are from high-income English-speaking
samples, and its numbers (thresholds, intervals, session lengths) are starting defaults, not findings. A
recommendation is adopted, adapted, deferred or rejected against the code, the owner's decisions and Cambodian
families' reality; the decision and its reason go in `PRODUCT-IMPROVEMENT-ROADMAP.md`.

## What it has informed so far (details in the roadmap log)

| Finding (label) | What the product did |
|---|---|
| Brief explicit model before practice; worked examples ([Strong]) | L1 "Learn first": an activity can teach before it asks |
| Elaborated feedback beats right/wrong (Van der Kleij 2015, [Strong]) | L2: authors are told when a question has no explanation (only 4 of 524 had one) |
| Separate "worked through with help" from "showed on their own" ([Design judgment]) | L3 / owner decision D-16: answers given with the hint are practice, not mastery evidence |
| Pre-readers need every instruction spoken ([Promising]) | L4: educators can record a question or introduction in the Studio (0 of 13 real prompts had audio) |

## Considered and not adopted (yet)

- **The full evidence ladder** (Introduced -> Practising -> Shown -> Retained -> Secure, versioned rule sets, delayed and
  transfer checks): it would replace the current mastery engine. Worth a deliberate decision, not a side effect.
- **Device text-to-speech for prompts with no recording**: content carries no language tag (a voice would be
  guessed), Khmer voice quality is unproven ([Validate]), and some devices send the text to an online speech
  service. A product decision for the owner if wanted.
- **Age-band interaction profiles (3-5 / 6-8 / 9-12)**: only one age hub (Junior, 4-9) and one real curriculum exist;
  the bands are hypotheses to test once content for them exists.
