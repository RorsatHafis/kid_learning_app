import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { SkillFollowUpView } from "./SkillFollowUp";
import { parentReason } from "@/features/learning/explain";
import type { JourneyStep } from "@/lib/api/types";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** A fetch double keyed by "METHOD /path"; every call is recorded. */
function server(routes: Record<string, (body: unknown) => Response>) {
  const calls: { method: string; path: string; body: unknown }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      const path = String(url).replace(/^.*\/api\/v1/, "");
      const body = init?.body ? JSON.parse(init.body as string) : undefined;
      calls.push({ method, path, body });
      const route = routes[`${method} ${path}`];
      return route ? route(body) : respond(404, { status: 404, detail: `no route ${method} ${path}` });
    }),
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

const child = (over: Record<string, unknown>) => ({
  standing: "NOT_STARTED",
  evidenceLevel: "NONE",
  scoredAnswers: 0,
  correctAnswers: 0,
  enrolled: true,
  latestPractice: null,
  ...over,
});

const VIEW = {
  skillId: "sk1",
  skillName: "Add within 10",
  objectiveTitle: "Adding small numbers",
  curriculumId: "cu1",
  curriculumName: "Maths - Junior",
  children: [
    child({
      childId: "a",
      displayName: "Arun",
      standing: "NEEDS_PRACTICE",
      evidenceLevel: "SUFFICIENT",
      scoredAnswers: 6,
      correctAnswers: 2,
    }),
    child({
      childId: "b",
      displayName: "Bopha",
      standing: "NEEDS_PRACTICE",
      evidenceLevel: "SUFFICIENT",
      scoredAnswers: 5,
      correctAnswers: 1,
      latestPractice: {
        activityId: "act1",
        activityTitle: "Add more",
        status: "PENDING",
        assignedAt: "2026-09-29T08:00:00Z",
        completedAt: null,
      },
    }),
    child({
      childId: "c",
      displayName: "Chenda",
      standing: "SECURE",
      evidenceLevel: "STRONG",
      scoredAnswers: 12,
      correctAnswers: 11,
      latestPractice: {
        activityId: "act1",
        activityTitle: "Add more",
        status: "COMPLETED",
        assignedAt: "2026-09-20T08:00:00Z",
        completedAt: "2026-09-21T08:00:00Z",
        standingWhenAssigned: "NEEDS_PRACTICE",
      },
    }),
    child({ childId: "d", displayName: "Dara", enrolled: false }),
  ],
  practice: [
    { activityId: "act1", title: "Add more", questionCount: 4, difficultyLevel: 1 },
    { activityId: "act2", title: "Adding with pictures", questionCount: 6, difficultyLevel: null },
  ],
};

function renderView() {
  return render(
    <MemoryRouter>
      <SkillFollowUpView classId="c1" skillId="sk1" memberHref={(id) => `/teacher/classes/c1/students/${id}`} />
    </MemoryRouter>,
  );
}

describe("SkillFollowUpView", () => {
  it("groups children by standing with their evidence, and suggests only those who need practice and have none waiting", async () => {
    server({ "GET /schools/classes/c1/skills/sk1": () => respond(200, VIEW) });
    renderView();

    expect(await screen.findByRole("heading", { level: 1, name: "Add within 10" })).toBeInTheDocument();
    const needs = screen.getByRole("region", { name: /Needs practice \(2\)/ });
    expect(within(needs).getByText("2 of 6 correct")).toBeInTheDocument();
    expect(within(needs).getByText(/Practice assigned .* · waiting/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Arun" })).toHaveAttribute("href", "/teacher/classes/c1/students/a");

    expect(screen.getByRole("checkbox", { name: "Choose Arun" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Choose Bopha" })).not.toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Choose Chenda" })).not.toBeChecked();
    // Not on the class's path: shown, but practice can't be placed for them.
    expect(screen.queryByRole("checkbox", { name: "Choose Dara" })).not.toBeInTheDocument();
    expect(screen.getByText("Not on this path")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Add more/ })).toBeChecked();
    // What happened after the class's practice: the standing then against now.
    expect(screen.getByText(/Practice done .* · needs practice then, secure now/)).toBeInTheDocument();
  });

  it("assigns the chosen activity to the chosen children and reports what the server did", async () => {
    let assigned = false;
    const calls = server({
      "GET /schools/classes/c1/skills/sk1": () => respond(200, VIEW),
      "POST /schools/classes/c1/skills/sk1/practice": () => {
        assigned = true;
        return respond(200, { assigned: ["a"], alreadyWaiting: ["b"], notOnThisPath: [] });
      },
    });
    const user = userEvent.setup();
    renderView();

    await user.click(await screen.findByRole("checkbox", { name: "Choose Bopha" }));
    await user.click(screen.getByRole("radio", { name: /Adding with pictures/ }));
    await user.click(screen.getByRole("button", { name: "Assign practice" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Assigned to Arun. Bopha already has it waiting.");
    expect(assigned).toBe(true);
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ activityId: "act2", childIds: ["a", "b"] });
    // Re-read after assigning, so "afterward" is the server's state, not a guess.
    expect(calls.filter((c) => c.method === "GET")).toHaveLength(2);
  });

  it("offers no assignment when nothing published practises the skill", async () => {
    server({
      "GET /schools/classes/c1/skills/sk1": () => respond(200, { ...VIEW, practice: [] }),
    });
    renderView();
    expect(await screen.findByText(/No published activity practises this skill/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Assign practice" })).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("says plainly when the page went stale, assigns nothing, and re-reads", async () => {
    const calls = server({
      "GET /schools/classes/c1/skills/sk1": () => respond(200, VIEW),
      "POST /schools/classes/c1/skills/sk1/practice": () => respond(404, { status: 404, detail: "Class member a not found" }),
    });
    const user = userEvent.setup();
    renderView();
    await user.click(await screen.findByRole("button", { name: "Assign practice" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Someone you chose is no longer in the class.");
    expect(screen.queryByText(/Class member a/)).not.toBeInTheDocument();
    expect(calls.filter((c) => c.method === "GET")).toHaveLength(2);
  });

  it("explains a not-a-member refusal instead of an empty page", async () => {
    server({
      "GET /schools/classes/c1/skills/sk1": () => respond(404, { status: 404, detail: "Skill in this class not found" }),
    });
    renderView();
    expect(await screen.findByText(/isn't part of what the class learns now/)).toBeInTheDocument();
  });
});

describe("parentReason for teacher-assigned practice", () => {
  const step = (explanation: Partial<JourneyStep["explanation"]>) =>
    ({
      explanation: {
        reason: "TEACHER_ASSIGNED",
        skill: null,
        targetSkill: null,
        afterActivityTitle: null,
        afterScore: null,
        decidedAt: null,
        assignedBy: null,
        ...explanation,
      },
    }) as JourneyStep;

  it("names the class and the skill when the server recorded them", () => {
    expect(
      parentReason(step({ skill: { id: "sk1", name: "Add within 10" }, assignedBy: "Grade 1 A · Wat Phnom Primary" }), "Maths"),
    ).toBe("Practice on Add within 10, assigned by a teacher of Grade 1 A · Wat Phnom Primary.");
  });

  it("reads as before for practice placed without them", () => {
    expect(parentReason(step({}), "Maths")).toBe("Assigned by a teacher.");
  });
});
