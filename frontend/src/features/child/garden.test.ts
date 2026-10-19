import { describe, expect, it } from "vitest";
import type { CurriculumProgress, SkillStanding } from "@/lib/api/types";
import { garden, grown } from "./garden";

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

  it("tells only what advanced in this activity: a new sprout, a bud, a bloom - never a step back", () => {
    const before = progress([
      { id: "a", standing: "NOT_STARTED", answers: 0 },
      { id: "b", standing: "TOO_EARLY_TO_TELL" },
      { id: "c", standing: "PROGRESSING" },
      { id: "d", standing: "SECURE" },
    ]);
    const after = progress([
      { id: "a", standing: "TOO_EARLY_TO_TELL", answers: 2 },
      { id: "b", standing: "PROGRESSING" },
      { id: "c", standing: "SECURE" },
      { id: "d", standing: "PROGRESSING" },
    ]);
    expect(grown(before, after).map((p) => [p.skill.skillId, p.stage])).toEqual([
      ["c", "bloom"],
      ["b", "bud"],
      ["a", "sprout"],
    ]);
    expect(grown(null, after)).toEqual([]);
  });
});
