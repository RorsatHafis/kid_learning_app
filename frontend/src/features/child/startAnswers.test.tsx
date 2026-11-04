/**
 * PERF-1: a start carries the answers already recorded on the attempt, so the player doesn't ask for them again and
 * resumes at the first unanswered question; a start without them (an older server) still works by asking.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { PracticeSession } from "./PlaySession";
import { setMuted } from "@/features/buddy/sound";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const item = (n: number) => ({
  activityItemId: `i${n}`,
  sequenceOrder: n,
  questionVersionId: `q${n}`,
  questionType: "NUMERIC",
  prompt: `${n} + 1 = ?`,
  options: [],
});
const ACTIVITY = { activityVersionId: "av1", activityId: "a1", title: "Adding", instructions: null, difficultyLevel: 1,
  estimatedDurationSeconds: 60, items: [item(0), item(1)] };
const FIRST_ANSWERED = { id: "r0", questionVersionId: "q0", submittedAnswer: "1", correct: true, correctAnswer: null };
const ATTEMPT = { id: "t1", childId: "c1", activityVersionId: "av1", learningPathItemId: null, status: "IN_PROGRESS",
  startedAt: "2026-11-04T08:00:00Z", completedAt: null, score: null };

function server(start: object) {
  const paths: string[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      const path = String(url).replace(/^.*\/api\/v1/, "");
      paths.push(`${method} ${path}`);
      if (path === "/children/c1") return respond(200, { id: "c1", displayName: "Sokha" });
      if (method === "POST" && path === "/children/c1/activity-attempts") return respond(201, start);
      if (path === "/activity-versions/av1?childId=c1") return respond(200, ACTIVITY);
      if (method === "GET" && path === "/activity-attempts/t1/answers") return respond(200, [FIRST_ANSWERED]);
      return respond(404, { status: 404 });
    }),
  );
  return paths;
}

function renderPractice() {
  render(
    <MemoryRouter initialEntries={["/learn/c1/practice/av1"]}>
      <Routes>
        <Route path="/learn/:childId/practice/:activityVersionId" element={<PracticeSession />} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  setMuted(false);
});

describe("resuming from the answers a start carries", () => {
  it("uses them: no separate request, and the second question is the one shown", async () => {
    const paths = server({ ...ATTEMPT, answers: [FIRST_ANSWERED] });
    renderPractice();
    await waitFor(() => expect(document.querySelector("#question-prompt")).toHaveTextContent("1 + 1 = ?"));
    expect(paths).not.toContain("GET /activity-attempts/t1/answers");
  });

  it("without them (an older server), asks for the answers and resumes the same way", async () => {
    const paths = server(ATTEMPT);
    renderPractice();
    await waitFor(() => expect(document.querySelector("#question-prompt")).toHaveTextContent("1 + 1 = ?"));
    expect(paths).toContain("GET /activity-attempts/t1/answers");
  });
});
