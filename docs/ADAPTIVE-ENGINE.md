# Adaptive engine - how a child's next step is decided (traced from code)

Part of the product review (`PRODUCT-JOURNEY-REVIEW.md`). **Verification level: INSPECTED** - read from
`MasteryEngine`, `AdaptiveEngine`, `PostCompletionService` and `application.yaml` on 2026-11-03. The worked examples
are computed from the code's formula with the configured defaults; they were **not** run against the server for this
document (the behaviours are covered by the Battle 2D/5A-5D integration tests, which this pass did not re-read).
Not traced yet: `SmartReviewService`, `CurriculumPathPlanner`, `EvidenceStandard`, `LearningDays` (next pass).

## 1. Inputs and who controls them

| Input | Where it comes from | Who controls it | Changes what |
|---|---|---|---|
| Each scored answer (right/wrong) | `AnswerRecord`, scored on the server | The child, by answering | The skill's mastery and evidence count |
| Skills a question/activity gives evidence for | Question/activity skill tags | The author (teacher/admin) | Which skills an answer moves; which activities are candidates |
| Skill prerequisites | `skill_prerequisites` | Admin (catalog) | Where reinforcement goes |
| Activity difficulty (1-10) | Activity version | The author | Easiest for reinforcement, next-harder for challenge |
| Path content scope | Family path vs school path (`PathContentScope`) | Who placed the curriculum | Which activities may be placed |
| `platform.mastery.learning-rate` | config, default **0.2** | Operator | How fast recent answers outweigh old ones |
| `confidence-saturation-count` | config, default **10** | Operator | Confidence = evidence / 10, capped at 1 |
| `objective-mastery-threshold` | config, default **0.8** | Operator | When an objective counts as mastered |
| LOW 0.4, HIGH 0.8, evidence gates 2 and 3 | **constants** in `AdaptiveEngine` | Code only | Reinforce / challenge / continue |

Parents and children do not set any of these; the parent sees the result ("why this next") grounded in evidence.

## 2. Mastery: one number with one meaning

Per (child, skill): `mastery = recency-weighted mean of the answers actually observed`, weights `0.8^age`.
Update for the n-th answer: `step = 0.2 / (1 - 0.8^n)`, `mastery += step * (outcome - mastery)` (outcome 1 or 0).
Step sizes: n=1 **1.0**, n=2 **0.556**, n=3 **0.410**, n=4 **0.339**, n=5 **0.298** -> 0.2. Every answer appends an
immutable `LearnerSkillHistory` row and re-rolls each objective the skill feeds: the mean of its skills **with
evidence** (an unpractised skill is unknown, not 0); MASTERED needs every mapped skill observed.

## 3. The decision after an activity is completed

`PostCompletionService` picks the **governing skill**: of the activity's skills that have evidence, the one with the
**lowest mastery** (ties by skill id). For it, `AdaptiveEngine.decide`:

| Condition (governing skill) | Direction | Target skill | Activity placed (published, in the path's scope, not finished on this path, not the one just done) | Recorded as |
|---|---|---|---|---|
| no evidence | CONTINUE | - | none | CONTINUE, "No evidence yet" |
| mastery < 0.4 **and** evidence >= 2 | REINFORCEMENT | first prerequisite, else the skill | the **easiest** candidate | REMEDIATE |
| mastery >= 0.8 **and** evidence >= 3 | CHALLENGE | the skill | the easiest candidate **harder than** the one just done; none if the one just done has no difficulty | INCREASE_DIFFICULTY |
| otherwise | CONTINUE | - | none (the curriculum path continues) | CONTINUE |

Placement is idempotent (the path is locked; an activity already waiting on the path is returned, not added again).
Every decision is stored (`AdaptationDecision`) with its reason, including "no published activity available".

## 4. Worked examples (rate 0.2; answers on one skill, oldest first)

| Child's answers | Mastery after each | Evidence | Decision | Why |
|---|---|---|---|---|
| W | 0 | 1 | CONTINUE | One wrong answer is weak evidence, not a weak child (gate 2) |
| W W | 0, 0 | 2 | REINFORCEMENT | Below 0.4 with 2 answers: easier practice, or the prerequisite skill |
| C W | 1, 0.444 | 2 | CONTINUE | Mixed evidence: keep to the path |
| C W W | 1, 0.444, 0.262 | 3 | REINFORCEMENT | The recent misses outweigh the first success |
| C C | 1, 1 | 2 | CONTINUE | Perfect but only 2 answers: challenge needs 3 |
| C C C | 1, 1, 1 | 3 | CHALLENGE | A harder activity on the skill, if one exists |
| W C C | 0, 0.556, 0.738 | 3 | CONTINUE | Recovering, not yet 0.8 |
| W C C C | ..., 0.827 | 4 | CHALLENGE | Recovered on recent evidence |

Same content, different evidence: the children in rows 2 and 6 did the same activity and get different next steps,
and the parent sees why.

## 5. Findings from this trace (to the roadmap)

| # | Finding | Effect | Severity | Proposed |
|---|---|---|---|---|
| AE-1 | "First prerequisite" comes from `SkillPrerequisiteRepository.findBySkillId` with **no ORDER BY** | With 2+ prerequisites, reinforcement's target can differ between runs - not deterministic as the class promises | Medium (learning integrity) | **Fixed 2026-11-03**: ordered by when recorded, then id (`SkillPrerequisiteRepository.findBySkillId`, `PrerequisiteOrderIntegrationTests`). Choosing the weakest prerequisite instead stays a product decision (AE-2) |
| AE-2 | Reinforcement targets the prerequisite without looking at the child's state on it | A child strong on the prerequisite is still sent there | Low-medium | Decision: skip prerequisites already mastered with evidence |
| AE-3 | Thresholds 0.4/0.8 and gates 2/3 are code constants | Operators can't tune per age band; documented in the class | Low | Promote to configuration only when a real need exists |
| AE-4 | Challenge needs the completed activity's difficulty | Activities without difficulty never lead to a challenge | Low | Already surfaced: the activity checks warn `NO_DIFFICULTY` ("challenge and reinforcement choices use it", `ContentValidator`). Nothing to build |
| AE-5 | One governing skill per completion (the weakest) | A multi-skill activity adapts on its weakest skill only; others continue via Smart Review | By design | Keep; state it in parent "why" (already grounded) |

Nothing here was changed in code: these are recorded for the owner's prioritisation (no architecture change during
review).
