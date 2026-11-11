/**
 * D-20 (owner decision 2026-11-11): after a wrong answer the server may offer one more go. The answer stays hidden
 * meanwhile; a right second try is met as such; the child can instead ask to see the answer.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { PracticeSession } from "./PlaySession";
import { setMuted } from "@/features/buddy/sound";
import type { ActivityItem } from "@/lib/api/types";

const SUM: ActivityItem = {
  activityItemId: "i1",
  sequenceOrder: 0,
  questionVersionId: "q1",
  questionType: "NUMERIC",
  prompt: "2 + 2 = ?",
  options: [],
  hint: null,
};

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const OPEN = { id: "r1", questionVersionId: "q1", correct: false, correctAnswer: null, explanation: null, retryAvailable: true, retry: null };

function server(second: "right" | "reveal") {
  const calls: { path: string; body: unknown }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      const path = String(url).replace(/^.*\/api\/v1/, "");
      calls.push({ path, body: init?.body ? JSON.parse(init.body as string) : null });
      if (path === "/children/c1") return respond(200, { id: "c1", displayName: "Sokha" });
      if (method === "POST" && path === "/children/c1/activity-attempts") return respond(201, { id: "t1", status: "IN_PROGRESS" });
      if (path === "/activity-versions/av1?childId=c1") {
        return respond(200, { activityVersionId: "av1", activityId: "a1", title: "Adding", instructions: null, items: [SUM] });
      }
      if (method === "GET" && path === "/activity-attempts/t1/answers") return respond(200, []);
      if (method === "POST" && path === "/activity-attempts/t1/answers") return respond(201, OPEN);
      if (method === "POST" && path === "/activity-attempts/t1/answers/r1/retry" && second === "right") {
        return respond(200, { ...OPEN, retryAvailable: false, retry: { submittedAnswer: "4", correct: true, hintUsed: false }, correctAnswer: { optionIds: [], text: "4" } });
      }
      if (method === "POST" && path === "/activity-attempts/t1/answers/r1/reveal" && second === "reveal") {
        return respond(200, { ...OPEN, retryAvailable: false, revealed: true, correctAnswer: { optionIds: [], text: "4" } });
      }
      return respond(404, { status: 404 });
    }),
  );
  return calls;
}

async function firstTry() {
  setMuted(true);
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={["/learn/c1/practice/av1"]}>
      <Routes>
        <Route path="/learn/:childId/practice/:activityVersionId" element={<PracticeSession />} />
      </Routes>
    </MemoryRouter>,
  );
  await user.click(await screen.findByRole("button", { name: /Let's go/ }));
  await user.type(await screen.findByRole("textbox"), "5");
  await user.click(screen.getByRole("button", { name: "Check" }));
  return user;
}

afterEach(() => {
  vi.unstubAllGlobals();
  setMuted(false);
});

describe("one more go after a wrong answer (D-20)", () => {
  it("asks for another try without giving the answer away, then meets a right second try as such", async () => {
    const calls = server("right");
    const user = await firstTry();
    expect(await screen.findByText("Not quite — have another go!")).toBeInTheDocument();
    expect(screen.queryByText(/The answer is/)).not.toBeInTheDocument();
    // A clear question for the second try.
    const box = await screen.findByRole("textbox");
    expect(box).toHaveValue("");
    await user.type(box, "4");
    await user.click(screen.getByRole("button", { name: "Check" }));
    expect(await screen.findByText("You got it on your second try!")).toBeInTheDocument();
    expect(calls.find((c) => c.path.endsWith("/retry"))?.body).toMatchObject({ submittedAnswer: "4", hintUsed: false });
  });

  it("can show the answer instead", async () => {
    server("reveal");
    const user = await firstTry();
    await user.click(await screen.findByRole("button", { name: "Show me the answer" }));
    expect(await screen.findByText("Not quite — good try!")).toBeInTheDocument();
    expect(screen.getByText("The answer is 4. Let's keep going!")).toBeInTheDocument();
  });
});
