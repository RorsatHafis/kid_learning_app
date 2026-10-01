import { describe, expect, it } from "vitest";
import type { Chapter, Journey, JourneyStep } from "@/lib/api/types";
import { chapterOf, chapterRuns, focusChapter, journeyLight } from "./chapters";

const chapter = (id: string, position: number, state: Chapter["state"], total = 2, done = 0): Chapter => ({
  id,
  position,
  title: `Chapter ${id}`,
  description: null,
  totalSteps: total,
  completedSteps: done,
  state,
});

const step = (id: string, chapterId: string | null, status: JourneyStep["status"] = "PENDING"): JourneyStep => ({
  learningPathItemId: id,
  activityVersionId: `v-${id}`,
  title: id,
  activityType: "PRACTICE",
  difficultyLevel: null,
  estimatedDurationSeconds: null,
  questionCount: 2,
  sequenceOrder: 0,
  status,
  source: chapterId ? "CURRICULUM" : "ADAPTIVE",
  skills: [],
  score: null,
  completedAt: null,
  explanation: { reason: "CURRICULUM", skill: null, targetSkill: null, afterActivityTitle: null, afterScore: null, decidedAt: null, assignedBy: null },
  chapterId,
});

const journey = (steps: JourneyStep[], chapters: Chapter[], nextStepId: string | null): Journey => ({
  enrollmentId: "e",
  childId: "c",
  curriculum: { id: "k", name: "Numbers", subjectCode: "N", subjectName: "Numbers", ageHubName: "Junior" },
  status: "ACTIVE",
  enrolledAt: "2026-09-01T00:00:00Z",
  completedSteps: steps.filter((s) => s.status === "COMPLETED").length,
  totalSteps: steps.length,
  nextStepId,
  chapters,
  steps,
});

describe("chapters", () => {
  const chapters = [chapter("a", 1, "COMPLETE", 2, 2), chapter("b", 2, "CURRENT"), chapter("c", 3, "UPCOMING")];

  it("groups steps into runs in play order, keeping practice from outside every chapter where the child meets it", () => {
    const j = journey(
      [step("a1", "a", "COMPLETED"), step("a2", "a", "COMPLETED"), step("extra", null), step("b1", "b"), step("b2", "b"), step("c1", "c")],
      chapters,
      "extra",
    );
    const runs = chapterRuns(j);
    expect(runs.map((r) => [r.chapter?.id ?? null, r.steps.map((s) => s.step.learningPathItemId)])).toEqual([
      ["a", ["a1", "a2", "extra"]],
      ["b", ["b1", "b2"]],
      ["c", ["c1"]],
    ]);
  });

  it("focuses the chapter holding the next step", () => {
    const j = journey([step("a1", "a", "COMPLETED"), step("b1", "b"), step("b2", "b"), step("c1", "c")], chapters, "b1");
    const focus = focusChapter(j);
    expect(focus.chapter?.id).toBe("b");
    expect(focus.steps.map((s) => s.step.learningPathItemId)).toEqual(["b1", "b2"]);
  });

  it("with nothing waiting, focuses the last chapter; without chapters, the whole journey", () => {
    const done = journey([step("a1", "a", "COMPLETED"), step("c1", "c", "COMPLETED")], chapters, null);
    expect(focusChapter(done).chapter?.id).toBe("c");

    const flat = journey([step("x", null), step("y", null)], [], "x");
    expect(focusChapter(flat).chapter).toBeNull();
    expect(focusChapter(flat).steps).toHaveLength(2);
  });

  it("lights the world by finished chapters, or by steps when a path has none", () => {
    expect(journeyLight({ chapters, completedSteps: 2, totalSteps: 6 })).toBeCloseTo(1 / 3);
    // A chapter half done doesn't move the sky: only finished chapters do.
    expect(journeyLight({ chapters: [chapter("a", 1, "CURRENT", 4, 3)], completedSteps: 3, totalSteps: 4 })).toBe(0);
    expect(journeyLight({ chapters: [chapter("a", 1, "COMPLETE", 2, 2)], completedSteps: 2, totalSteps: 2 })).toBe(1);
    expect(journeyLight({ chapters: [], completedSteps: 3, totalSteps: 4 })).toBe(0.75);
    expect(journeyLight({ chapters: [], completedSteps: 0, totalSteps: 0 })).toBe(0);
  });

  it("names a position only when there is more than one chapter", () => {
    expect(chapterOf(chapters[1], 3)).toBe("Chapter 2 of 3");
    expect(chapterOf(chapters[0], 1)).toBeNull();
    expect(chapterOf(null, 3)).toBeNull();
  });
});
