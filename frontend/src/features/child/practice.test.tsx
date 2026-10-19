import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { PracticeSession } from "./PlaySession";
import { setMuted } from "@/features/buddy/sound";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const ACTIVITY = {
  activityVersionId: "av1",
  activityId: "a1",
  title: "Addition Within 5 Practice",
  instructions: null,
  difficultyLevel: 1,
  estimatedDurationSeconds: 60,
  items: [
    {
      activityItemId: "i1",
      sequenceOrder: 0,
      questionVersionId: "q1",
      questionType: "NUMERIC",
      prompt: "2 + 2 = ?",
      options: [],
    },
  ],
};

type Call = { method: string; path: string; body: unknown };

function server(start: () => Response) {
  const calls: Call[] = [];
  const recorded: unknown[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      const path = String(url).replace(/^.*\/api\/v1/, "");
      const body = init?.body ? JSON.parse(init.body as string) : undefined;
      calls.push({ method, path, body });
      if (path === "/children/c1") return respond(200, { id: "c1", displayName: "Sokha" });
      if (method === "POST" && path === "/children/c1/activity-attempts") return start();
      // The content is read for this child (TD-13).
      if (path === "/activity-versions/av1?childId=c1") return respond(200, ACTIVITY);
      if (method === "GET" && path === "/activity-attempts/t1/answers") return respond(200, recorded);
      if (method === "POST" && path === "/activity-attempts/t1/answers") {
        const record = {
          id: "r1",
          questionVersionId: "q1",
          submittedAnswer: "4",
          correct: true,
          correctAnswer: { optionIds: [], text: "4" },
        };
        recorded.push(record);
        return respond(201, record);
      }
      if (method === "POST" && path === "/activity-attempts/t1/complete") return respond(200, { id: "t1", status: "COMPLETED" });
      return respond(404, { status: 404 });
    }),
  );
  return calls;
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

describe("PracticeSession (X24)", () => {
  it("plays a lit book again as free practice - no path item - and ends back at the shelf, nothing promised on the path", async () => {
    setMuted(true);
    const attempt = { id: "t1", childId: "c1", activityVersionId: "av1", learningPathItemId: null, status: "IN_PROGRESS" };
    const calls = server(() => respond(201, attempt));
    const user = userEvent.setup();
    renderPractice();

    expect(await screen.findByText("Playing it again")).toBeInTheDocument();
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ activityVersionId: "av1" });
    // Y40: the intro shows what the book asks the child to do, from its own questions.
    expect(screen.getByRole("list", { name: "In this book" }).textContent).toBe("Numbers");
    await user.click(screen.getByRole("button", { name: /Let's go/ }));
    await user.type(await screen.findByRole("textbox"), "4");
    await user.click(screen.getByRole("button", { name: "Check" }));
    await user.click(await screen.findByRole("button", { name: /Finish/ }));

    expect(await screen.findByRole("heading", { name: "Perfect again!" })).toBeInTheDocument();
    expect(screen.getByText("You got 1 of 1 right")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Back to my shelf/ })).toHaveAttribute("href", "/learn/c1/shelf");
    expect(screen.queryByText(/Up next/)).not.toBeInTheDocument();
  });

  it("says so when the book is started on the path, or can't be played again", async () => {
    server(() => respond(409, { status: 409, detail: "An attempt is already in progress" }));
    renderPractice();
    expect(await screen.findByText("You've started this book on your path. Finish it there first!")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /My shelf/ })).toHaveAttribute("href", "/learn/c1/shelf");
    vi.unstubAllGlobals();
  });

  it("explains a book that is no longer playable", async () => {
    server(() => respond(404, { status: 404 }));
    renderPractice();
    expect(await screen.findByText("This book can't be played again just now.")).toBeInTheDocument();
  });
});
