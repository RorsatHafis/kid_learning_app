import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ParentHome } from "./ParentHome";
import { lastPlayedText } from "./familyText";
import { ChooseCurriculumPanel } from "./ChooseCurriculumPanel";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const chapter = { position: 1, title: "Addition Within 10", completedSteps: 2, totalSteps: 6, state: "CURRENT" };

function enrollment(over: Record<string, unknown>) {
  return {
    enrollmentId: "e1",
    curriculum: { id: "cu1", name: "Maths - Junior", subjectCode: "MATHS", subjectName: "Maths", ageHubName: "Junior" },
    status: "ACTIVE",
    enrolledAt: "2026-09-01T00:00:00Z",
    completedSteps: 2,
    totalSteps: 6,
    nextStep: {
      learningPathItemId: "i3",
      activityVersionId: "a3",
      title: "Addition Within 10 Practice - Easy",
      explanation: { reason: "CURRICULUM", skill: null, targetSkill: null, afterActivityTitle: null, afterScore: null, decidedAt: null },
    },
    currentChapter: chapter,
    chapterCount: 2,
    chapters: [chapter],
    ...over,
  };
}

function family(routes: Record<string, unknown>) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      const path = String(url).replace(/^.*\/api\/v1/, "");
      const key = Object.keys(routes).find((k) => path.startsWith(k));
      return key ? respond(200, routes[key]) : respond(404, { status: 404, detail: path });
    }),
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("family home (X8)", () => {
  it("says when each child last played and where each path stands", async () => {
    family({
      "/children/c1/enrollments": [enrollment({})],
      "/children/c1/activity-history": [
        { attemptId: "t1", title: "Counting Practice", correctAnswers: 2, totalAnswers: 3, completedAt: new Date().toISOString() },
      ],
      "/children/c1/skills": [],
      "/children/c2/enrollments": [],
      "/children/c2/activity-history": [],
      "/children/c2/skills": [],
      "/children": [
        { id: "c1", displayName: "Sokha", dateOfBirth: "2019-03-14" },
        { id: "c2", displayName: "Dara", dateOfBirth: "2020-01-02" },
      ],
    });
    render(
      <MemoryRouter>
        <ParentHome />
      </MemoryRouter>,
    );

    // Y39: when the child last played, as the last activity's own answers - never as a skill judgement.
    expect(await screen.findByText("Counting Practice: 2 of 3 answers right, today")).toBeInTheDocument();
    expect(screen.getAllByText("One activity's answers - not a judgement of the skill.").length).toBeGreaterThan(0);
    expect(await screen.findByText("Hasn't played yet")).toBeInTheDocument();
    const sokha = screen.getByRole("list", { name: "Sokha's subjects" });
    expect(within(sokha).getByText(/2 of 6 steps/)).toBeInTheDocument();
    // A path still in progress is not "done", so no nudge to add a subject.
    expect(screen.queryByRole("link", { name: /Add a subject/ })).not.toBeInTheDocument();
  });

  it("calls a finished path finished, without claiming mastery, and offers the next subject", async () => {
    family({
      "/children/c1/enrollments": [enrollment({ completedSteps: 6, nextStep: null, currentChapter: null })],
      "/children/c1/activity-history": [{ attemptId: "t1", completedAt: "2026-09-28T09:00:00Z" }],
      "/children/c1/skills": [],
      "/children": [{ id: "c1", displayName: "Sokha", dateOfBirth: "2019-03-14" }],
    });
    render(
      <MemoryRouter>
        <ParentHome />
      </MemoryRouter>,
    );

    expect(await screen.findByText("every step done so far")).toBeInTheDocument();
    expect(screen.queryByText(/mastered|all caught up/i)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Add a subject for Sokha" })).toHaveAttribute(
      "href",
      "/parent/children/c1?add=subject",
    );
  });

  it("counts skills by the server's standing, once each, leaving out skills not started", async () => {
    const skill = (skillId: string, standing: string) => ({ skillId, name: skillId, standing });
    family({
      "/children/c1/enrollments": [enrollment({})],
      "/children/c1/activity-history": [{ attemptId: "t1", completedAt: new Date().toISOString() }],
      "/children/c1/skills": [
        {
          enrollmentId: "e1",
          objectives: [
            { objectiveId: "o1", skills: [skill("s1", "SECURE"), skill("s2", "TOO_EARLY_TO_TELL"), skill("s3", "NOT_STARTED")] },
            // The same skill under a second objective is still one skill.
            { objectiveId: "o2", skills: [skill("s1", "SECURE"), skill("s4", "TOO_EARLY_TO_TELL")] },
          ],
        },
      ],
      "/children": [{ id: "c1", displayName: "Sokha", dateOfBirth: "2019-03-14" }],
    });
    render(
      <MemoryRouter>
        <ParentHome />
      </MemoryRouter>,
    );

    expect(await screen.findByText("What the answers show so far")).toBeInTheDocument();
    expect(screen.getByText("1 Secure")).toBeInTheDocument();
    expect(screen.getByText("2 Too early to tell")).toBeInTheDocument();
    expect(screen.queryByText(/Not started/)).not.toBeInTheDocument();
  });

  it("says nothing is shown yet when no skill has answers", async () => {
    family({
      "/children/c1/enrollments": [enrollment({})],
      "/children/c1/activity-history": [],
      "/children/c1/skills": [{ enrollmentId: "e1", objectives: [{ objectiveId: "o1", skills: [{ skillId: "s1", standing: "NOT_STARTED" }] }] }],
      "/children": [{ id: "c1", displayName: "Sokha", dateOfBirth: "2019-03-14" }],
    });
    render(
      <MemoryRouter>
        <ParentHome />
      </MemoryRouter>,
    );

    expect(await screen.findByText("No answers yet - this fills in as they play.")).toBeInTheDocument();
  });
});

describe("ChooseCurriculumPanel (X8)", () => {
  const choice = (over: Record<string, unknown>) => ({
    id: "x",
    name: "Path",
    subjectCode: "MATHS",
    subjectName: "Maths",
    ageHubCode: "JUNIOR",
    ageHubName: "Junior",
    minAge: 4,
    maxAge: 9,
    forChildAge: true,
    ageOrder: 1,
    ...over,
  });

  it("puts the child's age first by subject, keeps other ages one step away, and hides what they already follow", async () => {
    family({
      "/children/c1/curricula": [
        choice({ id: "k1", name: "Khmer - Junior", subjectCode: "KHMER", subjectName: "Khmer" }),
        choice({ id: "m1", name: "Maths - Junior" }),
        choice({ id: "m3", name: "Maths - Junior stories" }),
        choice({ id: "taken", name: "Already followed" }),
        ...["Maths - Senior", "Maths - Senior stories"].map((name, i) =>
          choice({
            id: `s${i}`,
            name,
            ageHubCode: "SENIOR",
            ageHubName: "Senior",
            minAge: 10,
            maxAge: null,
            forChildAge: false,
            ageOrder: 2,
          }),
        ),
      ],
    });
    render(
      <ChooseCurriculumPanel childId="c1" childName="Sokha" excludeCurriculumIds={["taken"]} onEnrolled={() => undefined} />,
    );

    expect(await screen.findByText("For Sokha's age")).toBeInTheDocument();
    // A subject with several paths is headed; a subject with one names itself in its row.
    expect(screen.getByRole("region", { name: "Maths" })).toHaveTextContent("Maths - Junior stories");
    expect(screen.queryByRole("region", { name: "Khmer" })).not.toBeInTheDocument();
    expect(screen.getByText("Khmer · Junior · Ages 4–9")).toBeInTheDocument();
    expect(screen.queryByText("Already followed")).not.toBeInTheDocument();
    // Other ages are behind a disclosure, still choosable; the button names say which path.
    expect(screen.getByText("Paths for other ages (2)")).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Maths, other ages", hidden: true })).toHaveTextContent("Maths - Senior");
    expect(screen.getByRole("button", { name: /^Start this path\s*: Maths - Junior$/, hidden: true })).toBeInTheDocument();
    expect(screen.getAllByText(/Ages 10\+/)).toHaveLength(2);
  });

  it("says plainly when nothing is for the child's age yet", async () => {
    family({ "/children/c1/curricula": [choice({ forChildAge: false, name: "Maths - Senior" })] });
    render(<ChooseCurriculumPanel childId="c1" childName="Sokha" onEnrolled={() => undefined} />);
    expect(await screen.findByText(/No path is for Sokha's age yet/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Start this path/ })).toBeVisible();
  });
});

describe("lastPlayedText", () => {
  it("counts calendar days, then shows a date", () => {
    const now = new Date(2026, 8, 30, 8, 0);
    expect(lastPlayedText(new Date(2026, 8, 30, 7, 0).toISOString(), now)).toBe("today");
    expect(lastPlayedText(new Date(2026, 8, 29, 23, 0).toISOString(), now)).toBe("yesterday");
    expect(lastPlayedText(new Date(2026, 8, 26, 12, 0).toISOString(), now)).toBe("4 days ago");
    expect(lastPlayedText(new Date(2026, 8, 1, 12, 0).toISOString(), now)).not.toMatch(/ago|today|yesterday/);
  });
});
