import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ShelfPage } from "./ShelfPage";
import { milestones, nextLanterns, type Shelf } from "./shelf";

function respond(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}

const curriculum = { id: "cu1", name: "Maths - Junior", subjectCode: "MATH", subjectName: "Maths", ageHubName: "Ages 6-7" };

function step(id: string, status: "COMPLETED" | "PENDING", chapterId: string | null = "ch1", source = "CURRICULUM") {
  return {
    learningPathItemId: id,
    activityVersionId: `av-${id}`,
    title: `Book ${id}`,
    activityType: "PRACTICE",
    difficultyLevel: 1,
    estimatedDurationSeconds: 60,
    questionCount: 2,
    sequenceOrder: 1,
    status,
    source,
    skills: [],
    score: null,
    completedAt: status === "COMPLETED" ? "2026-09-28T00:00:00Z" : null,
    explanation: {
      reason: "CURRICULUM",
      skill: null,
      targetSkill: null,
      afterActivityTitle: null,
      afterScore: null,
      decidedAt: null,
    },
    chapterId,
  };
}

const chapter = (id: string, position: number, state: string, total: number, done: number) => ({
  id,
  position,
  title: `Chapter title ${position}`,
  description: null,
  totalSteps: total,
  completedSteps: done,
  state,
});

function shelf(steps: ReturnType<typeof step>[], chapters: ReturnType<typeof chapter>[], nextStepId: string | null): Shelf {
  const done = steps.filter((s) => s.status === "COMPLETED").length;
  return {
    overview: {
      enrollmentId: "e1",
      curriculum,
      status: "ACTIVE",
      enrolledAt: "2026-09-01T00:00:00Z",
      completedSteps: done,
      totalSteps: steps.length,
      nextStep: null,
      currentChapter: null,
      chapterCount: chapters.length,
      chapters,
    },
    journey: {
      enrollmentId: "e1",
      childId: "c1",
      curriculum,
      status: "ACTIVE",
      enrolledAt: "2026-09-01T00:00:00Z",
      completedSteps: done,
      totalSteps: steps.length,
      nextStepId,
      chapters,
      steps,
    },
  } as unknown as Shelf;
}

afterEach(() => vi.unstubAllGlobals());

describe("milestones (X18)", () => {
  it("hangs a lantern only for what happened", () => {
    expect(milestones([shelf([step("1", "PENDING")], [chapter("ch1", 1, "CURRENT", 1, 0)], "1")])).toEqual([]);

    const midway = milestones([
      shelf(
        [step("1", "COMPLETED"), step("2", "COMPLETED", "ch2"), step("3", "PENDING", "ch2")],
        [chapter("ch1", 1, "COMPLETE", 1, 1), chapter("ch2", 2, "CURRENT", 2, 1)],
        "3",
      ),
    ]);
    expect(midway.map((m) => m.title)).toEqual(["First book lit", "Chapter 1 all lit"]);

    const finished = milestones([shelf([step("1", "COMPLETED")], [chapter("ch1", 1, "COMPLETE", 1, 1)], null)]);
    expect(finished.map((m) => m.title)).toEqual(["First book lit", "Chapter 1 all lit", "Maths path all lit"]);
  });
});

describe("the next lantern (Y40)", () => {
  it("waits for the first book, then for the chapter the child is in, with the books still to go - and none on a finished path", () => {
    expect(nextLanterns([shelf([step("1", "PENDING")], [chapter("ch1", 1, "CURRENT", 1, 0)], "1")])).toEqual([
      { key: "e1:first", title: "First book lit", detail: "1 more book" },
    ]);
    const midway = nextLanterns([
      shelf(
        [step("1", "COMPLETED"), step("2", "COMPLETED", "ch2"), step("3", "PENDING", "ch2"), step("4", "PENDING", "ch2")],
        [chapter("ch1", 1, "COMPLETE", 1, 1), chapter("ch2", 2, "CURRENT", 3, 1)],
        "3",
      ),
    ]);
    expect(midway.map((l) => [l.title, l.detail])).toEqual([["Chapter 2 all lit", "2 more books"]]);
    expect(nextLanterns([shelf([step("1", "COMPLETED")], [chapter("ch1", 1, "COMPLETE", 1, 1)], null)])).toEqual([]);
  });
});

describe("ShelfPage (X18)", () => {
  it("shows lit and waiting books by chapter, and only skills the answers support", async () => {
    const s = shelf(
      [step("1", "COMPLETED"), step("2", "PENDING"), step("3", "PENDING", null, "REVIEW")],
      [chapter("ch1", 1, "CURRENT", 2, 1)],
      "2",
    );
    const skill = (id: string, name: string, standing: string) => ({
      skillId: id,
      name,
      description: null,
      evidenceLevel: "SUFFICIENT",
      standing,
      scoredAnswers: 4,
      correctAnswers: 3,
      lastPracticedAt: null,
      inReview: false,
      reviewDueAt: null,
    });
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (path === "/children/c1") return respond({ id: "c1", displayName: "Sokha" });
        if (path === "/children/c1/enrollments") return respond([{ ...s.overview, nextStep: s.journey.steps[1] }]);
        if (path === "/enrollments/e1/journey") return respond(s.journey);
        if (path === "/children/c1/skills")
          return respond([
            {
              enrollmentId: "e1",
              curriculum,
              objectives: [
                {
                  objectiveId: "o1",
                  title: "Numbers",
                  description: null,
                  standing: "IN_PROGRESS",
                  skillsMapped: 3,
                  skillsPractised: 3,
                  skillsWithSufficientEvidence: 3,
                  skills: [
                    skill("k1", "Count to 10", "SECURE"),
                    skill("k2", "Add within 5", "PROGRESSING"),
                    skill("k3", "Add within 10", "NEEDS_PRACTICE"),
                  ],
                },
              ],
            },
          ]);
        return new Response(null, { status: 404 });
      }),
    );
    render(
      <MemoryRouter initialEntries={["/learn/c1/shelf"]}>
        <Routes>
          <Route path="/learn/:childId/shelf" element={<ShelfPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText("1 book lit so far. Look how far you've come!")).toBeInTheDocument();
    expect(screen.getByRole("listitem", { name: "Book 1, lit" })).toBeInTheDocument();
    // X24: a lit book can be played again (free practice); a book not yet lit cannot.
    expect(screen.getByRole("link", { name: "Play Book 1 again" })).toHaveAttribute("href", "/learn/c1/practice/av-1");
    expect(screen.queryByRole("link", { name: "Play Book 2 again" })).not.toBeInTheDocument();
    expect(screen.getByRole("listitem", { name: "Book 2, up next" })).toBeInTheDocument();
    // Review placed outside every chapter has its own shelf.
    expect(screen.getByRole("heading", { name: "Extra practice" })).toBeInTheDocument();
    expect(screen.getByRole("listitem", { name: "Book 3, waiting" })).toBeInTheDocument();
    expect(within(screen.getByRole("region", { name: "My lanterns" })).getByText("First book lit")).toBeInTheDocument();

    // Y39: the garden grows only from the server's standings - secure blooms, progressing buds.
    const garden = within(screen.getByRole("region", { name: "My garden" }));
    expect(await garden.findByRole("listitem", { name: "Count to 10: I can do this" })).toBeInTheDocument();
    expect(garden.getByRole("listitem", { name: "Add within 5: Getting stronger" })).toBeInTheDocument();
    // A skill that needs practice is never labelled so in front of the child: it is a neutral sprout, still growing.
    expect(garden.getByRole("listitem", { name: "Add within 10: Growing" })).toBeInTheDocument();
    expect(screen.queryByText(/needs practice/i)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Play next/ })).toHaveAttribute("href", "/learn/c1/play/e1");
  });
});
