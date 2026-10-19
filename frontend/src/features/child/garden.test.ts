import { describe, expect, it } from "vitest";
import type { CurriculumProgress, SkillStanding } from "@/lib/api/types";
import { afterActivity, garden } from "./garden";

function progress(skills: { id: string; standing: SkillStanding; answers?: number }[]): CurriculumProgress[] {
  return [
    {
      objectives: [
        {
          objectiveId: "o1",
          skills: skills.map((s) => ({
            skillId: s.id,
            name: s.id.toUpperCase(),
            description: null,
            evidenceLevel: "PARTIAL",
            standing: s.standing,
            scoredAnswers: s.answers ?? 3,
            correctAnswers: 1,
            lastPracticedAt: null,
            inReview: false,
            reviewDueAt: null,
          })),
        },
      ],
    } as unknown as CurriculumProgress,
  ];
}

describe("the garden of skills", () => {
  it("plants only practised skills, at the stage the server's standing supports", () => {
    const plants = garden(
      progress([
        { id: "a", standing: "NOT_STARTED", answers: 0 },
        { id: "b", standing: "NEEDS_PRACTICE" },
        { id: "c", standing: "SECURE" },
        { id: "d", standing: "PROGRESSING" },
        { id: "e", standing: "TOO_EARLY_TO_TELL" },
      ]),
    );
    expect(plants.map((p) => [p.skill.skillId, p.stage])).toEqual([
      ["c", "bloom"],
      ["d", "bud"],
      ["b", "sprout"],
      ["e", "sprout"],
    ]);
  });

  it("says a skill grew only when the server's standing advanced; first answers are planted; the rest is practice (Y41)", () => {
    const before = progress([
      { id: "a", standing: "NOT_STARTED", answers: 0 },
      { id: "b", standing: "TOO_EARLY_TO_TELL", answers: 2 },
      { id: "c", standing: "NEEDS_PRACTICE", answers: 6 },
      { id: "d", standing: "PROGRESSING", answers: 6 },
      { id: "e", standing: "PROGRESSING", answers: 6 },
      { id: "f", standing: "SECURE", answers: 9 },
      { id: "g", standing: "TOO_EARLY_TO_TELL", answers: 2 },
      { id: "h", standing: "PROGRESSING", answers: 4 },
    ]);
    const after = progress([
      { id: "a", standing: "TOO_EARLY_TO_TELL", answers: 1 }, // first answers: planted
      { id: "b", standing: "PROGRESSING", answers: 5 }, // first reading, getting stronger: budded (not "grew")
      { id: "c", standing: "PROGRESSING", answers: 8 }, // moved up: grew
      { id: "d", standing: "SECURE", answers: 9 }, // became secure: grew, in bloom
      { id: "e", standing: "PROGRESSING", answers: 8 }, // same standing: practice
      { id: "f", standing: "PROGRESSING", answers: 11 }, // a slip: practice, never told as a loss
      { id: "g", standing: "TOO_EARLY_TO_TELL", answers: 3 }, // still too early, not the first answers: practice
      { id: "h", standing: "PROGRESSING", answers: 4 }, // not answered in this activity: nothing
    ]);
    const news = afterActivity(before, after)!;
    expect(news.grew.map((p) => [p.skill.skillId, p.stage])).toEqual([
      ["d", "bloom"],
      ["c", "bud"],
    ]);
    expect(news.budded.map((p) => p.skill.skillId)).toEqual(["b"]);
    expect(news.planted.map((p) => p.skill.skillId)).toEqual(["a"]);
    expect(news.practised.map((s) => s.skillId)).toEqual(["e", "f", "g"]);
    // Without both readings nothing is claimed.
    expect(afterActivity(null, after)).toBeNull();
});
});
