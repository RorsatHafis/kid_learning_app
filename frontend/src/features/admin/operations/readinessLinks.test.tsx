import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { LearningOpsPage } from "./LearningOpsPage";

afterEach(() => vi.unstubAllGlobals());

const readiness = {
  curriculumId: "c1",
  curriculum: "Early numbers",
  ageGroup: "Ages 5-6",
  questions: 9,
  explained: 3,
  hinted: 9,
  readAloud: 0,
  khmerComplete: 4,
  khmerTranslatable: 8,
  subjectId: "maths",
};

describe("content readiness", () => {
  it("leads each gap to the subject's bank showing what still needs it; what is complete is not a link", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(
          JSON.stringify({
            lastSevenDays: {
              attemptsStarted: 0,
              attemptsCompleted: 0,
              childrenLearning: 0,
              answers: 0,
              answersCorrect: 0,
              attemptsLeftOpen: 0,
              placementsBySource: { CURRICULUM: 0, ADAPTIVE: 0, REVIEW: 0, TEACHER_ASSIGNED: 0 },
              reviewItemsActive: 0,
              reviewDueNow: 0,
            },
            coverageGaps: [],
            coverageGapTotal: 0,
            ageGroupsWithNothingOffered: [],
            readiness: [readiness],
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );
    render(
      <MemoryRouter>
        <LearningOpsPage />
      </MemoryRouter>,
    );

    const row = (await screen.findByText("Early numbers")).closest("li")!;
    const links = within(row).getAllByRole("link");
    expect(links.map((a) => a.getAttribute("href"))).toEqual([
      "/admin/studio/maths/questions?needs=EXPLANATION",
      "/admin/studio/maths/questions?needs=READ_ALOUD",
      "/admin/studio/maths/questions?needs=KHMER",
    ]);
    // The name says where it goes and keeps the words on screen.
    expect(links[0]).toHaveAccessibleName(/3 of 9: find the questions that still need it in the subject's question bank/);
    expect(within(row).getByText("9 of 9").closest("a")).toBeNull();
  });
});
