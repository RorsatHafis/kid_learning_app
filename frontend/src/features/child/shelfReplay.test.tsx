/**
 * X24: a lit book is played again in its activity's current published version - the server names it on the step
 * (`playAgainVersionId`), since the version the child played may have been revised and retired since.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ShelfPage } from "./ShelfPage";

afterEach(() => vi.unstubAllGlobals());

const curriculum = { id: "cu1", name: "Maths - Junior", subjectCode: "MATH", subjectName: "Maths", ageHubName: "Ages 6-7" };

function book(id: string, replay: string | null | undefined) {
  return {
    learningPathItemId: id,
    activityVersionId: `played-${id}`,
    title: `Book ${id}`,
    activityType: "PRACTICE",
    difficultyLevel: 1,
    estimatedDurationSeconds: 60,
    questionCount: 2,
    sequenceOrder: Number(id),
    status: "COMPLETED",
    source: "CURRICULUM",
    skills: [],
    score: null,
    completedAt: "2026-09-28T00:00:00Z",
    explanation: { reason: "CURRICULUM", skill: null, targetSkill: null, afterActivityTitle: null, afterScore: null, decidedAt: null },
    chapterId: null,
    ...(replay === undefined ? {} : { playAgainVersionId: replay }),
  };
}

function serve(steps: ReturnType<typeof book>[]) {
  const journey = {
    enrollmentId: "e1",
    childId: "c1",
    curriculum,
    status: "ACTIVE",
    enrolledAt: "2026-09-01T00:00:00Z",
    completedSteps: steps.length,
    totalSteps: steps.length,
    nextStepId: null,
    chapters: [],
    steps,
  };
  const respond = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      const path = String(url).replace(/^.*\/api\/v1/, "");
      if (path === "/children/c1") return respond({ id: "c1", displayName: "Sokha" });
      if (path === "/children/c1/enrollments")
        return respond([{ enrollmentId: "e1", curriculum, status: "ACTIVE", enrolledAt: journey.enrolledAt, completedSteps: steps.length,
          totalSteps: steps.length, nextStep: null, currentChapter: null, chapterCount: 0, chapters: [] }]);
      if (path === "/enrollments/e1/journey") return respond(journey);
      if (path === "/children/c1/skills") return respond([]);
      return new Response(null, { status: 404 });
    }),
  );
}

describe("playing a lit book again (X24)", () => {
  it("plays the version the server names: a revised activity's current one; nothing when it names none", async () => {
    serve([book("1", "current-1"), book("2", null), book("3", undefined)]);
    render(
      <MemoryRouter initialEntries={["/learn/c1/shelf"]}>
        <Routes>
          <Route path="/learn/:childId/shelf" element={<ShelfPage />} />
        </Routes>
      </MemoryRouter>,
    );

    // Revised since it was played: the current published version, not the retired one played.
    expect(await screen.findByRole("link", { name: "Play Book 1 again" })).toHaveAttribute("href", "/learn/c1/practice/current-1");
    // Archived or withdrawn: lit, but nothing to play again.
    expect(screen.getByRole("listitem", { name: "Book 2, lit" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Play Book 2 again" })).not.toBeInTheDocument();
    // A server that doesn't say (an older one): the version played, as before.
    expect(screen.getByRole("link", { name: "Play Book 3 again" })).toHaveAttribute("href", "/learn/c1/practice/played-3");
  });
});
