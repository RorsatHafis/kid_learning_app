/**
 * A skill's page for a parent (X19): where it stands and why, honest when there are too few answers, and a skill
 * that isn't the family's explained.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { SkillPage } from "./SkillPage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const now = {
  skillId: "k1",
  name: "Add within 10",
  description: "Adds two numbers with a total up to 10.",
  evidenceLevel: "SUFFICIENT",
  standing: "PROGRESSING",
  scoredAnswers: 6,
  correctAnswers: 4,
  lastPracticedAt: "2026-09-28T10:00:00Z",
  inReview: false,
  reviewDueAt: null,
};

function story(over: Record<string, unknown>) {
  return {
    skillId: "k1",
    name: "Add within 10",
    description: "Adds two numbers with a total up to 10.",
    objectiveTitle: "Addition Within 10",
    curriculumName: "Maths - Junior",
    now,
    activities: [
      {
        attemptId: "a3",
        activityTitle: "Practice - Hard",
        at: "2026-09-28T10:00:00Z",
        answers: 2,
        correct: 1,
        standingAfter: "PROGRESSING",
        evidenceAfter: 6,
      },
      {
        attemptId: "a2",
        activityTitle: "Practice - Medium",
        at: "2026-09-27T10:00:00Z",
        answers: 2,
        correct: 2,
        standingAfter: "PROGRESSING",
        evidenceAfter: 4,
      },
      {
        attemptId: "a1",
        activityTitle: "Practice - Easy",
        at: "2026-09-26T10:00:00Z",
        answers: 2,
        correct: 1,
        standingAfter: "TOO_EARLY_TO_TELL",
        evidenceAfter: 2,
      },
    ],
    activitiesInAll: 3,
    waiting: [
      {
        learningPathItemId: "i9",
        activityTitle: "Fluency Challenge",
        status: "PENDING",
        source: "ADAPTIVE",
        addedAt: "2026-09-28T10:00:00Z",
        curriculumName: "Maths - Junior",
      },
    ],
    ...over,
  };
}

function serve(skill: Response) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      const path = String(url).replace(/^.*\/api\/v1/, "");
      if (path === "/children/c1") return respond(200, { id: "c1", displayName: "Sokha" });
      if (path === "/children/c1/skills/k1") return skill.clone();
      return respond(404, { status: 404 });
    }),
  );
}

function renderPage() {
  render(
    <MemoryRouter initialEntries={["/parent/children/c1/skills/k1"]}>
      <Routes>
        <Route path="/parent/children/:childId/skills/:skillId" element={<SkillPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("SkillPage (X19)", () => {
  it("says where the skill stands, on what, how it got there and what happens next", async () => {
    serve(respond(200, story({})));
    renderPage();

    expect(await screen.findByRole("heading", { level: 1, name: "Add within 10" })).toBeInTheDocument();
    const stands = screen.getByRole("region", { name: "Where it stands" });
    expect(within(stands).getByText(/4 of 6 answers right across 3 activities, last on/)).toBeInTheDocument();
    const history = screen.getByRole("region", { name: "How it got here" });
    expect(within(history).getAllByRole("listitem")).toHaveLength(3);
    expect(within(history).getByText("Practice - Easy")).toBeInTheDocument();
    expect(within(history).getAllByText(/1 of 2 answers right/)).toHaveLength(2);
    expect(within(history).getByText(/2 of 2 answers right/)).toBeInTheDocument();
    const next = screen.getByRole("complementary", { name: "What happens next" });
    expect(within(next).getByText("Fluency Challenge")).toBeInTheDocument();
    expect(within(next).getByText("Added by Bookeyond after an earlier activity")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Sokha" })).toHaveAttribute("href", "/parent/children/c1");
  });

  it("is honest when there are too few answers and nothing is waiting", async () => {
    serve(
      respond(
        200,
        story({
          now: { ...now, standing: "TOO_EARLY_TO_TELL", evidenceLevel: "PARTIAL", scoredAnswers: 2, correctAnswers: 1 },
          activities: [],
          activitiesInAll: 1,
          waiting: [],
        }),
      ),
    );
    renderPage();
    expect(
      await screen.findByText(/1 of 2 answers right across 1 activity — not enough yet to say how it's going\./),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Nothing on Sokha's path practises this skill right now\. A few more answers are needed/),
    ).toBeInTheDocument();
  });

  it("explains a skill that isn't the family's", async () => {
    serve(respond(404, { status: 404, detail: "Skill not found" }));
    renderPage();
    expect(await screen.findByText(/isn't part of your child's learning paths/)).toBeInTheDocument();
  });
});
