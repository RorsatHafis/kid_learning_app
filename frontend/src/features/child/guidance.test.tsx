import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QuestionView } from "./QuestionView";
import { PracticeSession } from "./PlaySession";
import { setMuted } from "@/features/buddy/sound";
import { QuestionDraftForm } from "@/features/admin/studio/QuestionDraftForm";
import type { ActivityItem } from "@/lib/api/types";

const SUM: ActivityItem = {
  activityItemId: "i1",
  sequenceOrder: 0,
  questionVersionId: "q1",
  questionType: "NUMERIC",
  prompt: "2 + 2 = ?",
  options: [],
  hint: "Hold up two fingers on each hand.",
};

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

afterEach(() => {
  vi.unstubAllGlobals();
  setMuted(false);
});

describe("an author's hint (Y41)", () => {
  it("waits closed until the child asks for it, and is gone once they have answered", async () => {
    const user = userEvent.setup();
    const { rerender } = render(<QuestionView item={SUM} onAnswer={vi.fn()} disabled={false} result={null} />);
    const ask = screen.getByRole("button", { name: "Need a hint?" });
    expect(ask).toHaveAttribute("aria-expanded", "false");
    expect(screen.getByText(SUM.hint!)).not.toBeVisible();

    await user.click(ask);
    expect(screen.getByText(SUM.hint!)).toBeVisible();
    expect(screen.getByRole("button", { name: "Hide the hint" })).toHaveAttribute("aria-expanded", "true");

    rerender(
      <QuestionView item={SUM} onAnswer={vi.fn()} disabled={false} result={{ submitted: "4", correct: true, correctAnswer: { optionIds: [], text: "4" } }} />,
    );
    expect(screen.queryByRole("button", { name: /hint/ })).not.toBeInTheDocument();
    expect(screen.queryByText(SUM.hint!)).not.toBeInTheDocument();
  });

  it("isn't offered when the question has none", () => {
    render(<QuestionView item={{ ...SUM, hint: null }} onAnswer={vi.fn()} disabled={false} result={null} />);
    expect(screen.queryByRole("button", { name: "Need a hint?" })).not.toBeInTheDocument();
  });
});

describe("an author's explanation (Y41)", () => {
  function server(explanation: string | null, correct: boolean) {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const method = init?.method ?? "GET";
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (path === "/children/c1") return respond(200, { id: "c1", displayName: "Sokha" });
        if (method === "POST" && path === "/children/c1/activity-attempts") return respond(201, { id: "t1", status: "IN_PROGRESS" });
        if (path === "/activity-versions/av1?childId=c1") {
          return respond(200, { activityVersionId: "av1", activityId: "a1", title: "Adding", instructions: null, items: [SUM] });
        }
        if (method === "GET" && path === "/activity-attempts/t1/answers") return respond(200, []);
        if (method === "POST" && path === "/activity-attempts/t1/answers") {
          return respond(201, { id: "r1", questionVersionId: "q1", correct, correctAnswer: { optionIds: [], text: "4" }, explanation });
        }
        return respond(404, { status: 404 });
      }),
    );
  }

  async function answer(value: string) {
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
    expect(screen.queryByText("Why")).not.toBeInTheDocument();
    await user.type(await screen.findByRole("textbox"), value);
    await user.click(screen.getByRole("button", { name: "Check" }));
  }

  it("comes with the verdict and the right answer, after the child answers - right or wrong", async () => {
    server("Two and two more make four.", false);
    await answer("5");
    expect(await screen.findByText("Not quite — good try!")).toBeInTheDocument();
    expect(screen.getByText("The answer is 4. Let's keep going!")).toBeInTheDocument();
    expect(screen.getByText("Why")).toBeInTheDocument();
    expect(screen.getByText("Two and two more make four.")).toBeInTheDocument();
  });

  it("leaves a question without one exactly as before", async () => {
    server(null, true);
    await answer("4");
    expect(await screen.findByRole("button", { name: /Finish/ })).toBeInTheDocument();
    expect(screen.queryByText("Why")).not.toBeInTheDocument();
  });
});

describe("authoring a hint and an explanation (Y41)", () => {
  it("says when the child sees each, and sends both - blank as none", async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      <QuestionDraftForm
        questionType="NUMERIC"
        initial={{ prompt: "2 + 2 = ?", correctAnswer: "4", difficultyLevel: 1, explanation: "old" }}
        submitLabel="Save draft"
        busy={false}
        onSubmit={onSubmit}
      />,
    );
    expect(screen.getByText(/Shown before the child answers, only if they tap/)).toBeInTheDocument();
    expect(screen.getByText(/Shown after the child answers, right or wrong/)).toBeInTheDocument();
    await user.type(screen.getByLabelText("Hint"), "  Count on from 2.  ");
    await user.clear(screen.getByLabelText("Explanation"));
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    expect(onSubmit.mock.calls[0][0]).toMatchObject({ hint: "Count on from 2.", explanation: null });
  });
});
