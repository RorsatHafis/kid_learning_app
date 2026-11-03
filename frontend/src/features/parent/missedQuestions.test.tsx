/**
 * A grown-up sees what their child missed in an activity: nothing is read until they open it, then each question
 * answered wrong - from the exact version the child played - with the answer and why; a failure says so and retries.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MissedQuestions } from "./MissedQuestions";
import { activityAttemptApi, activityContentApi } from "@/lib/api/endpoints";
import type { ActivityVersionDetail, AnswerRecord } from "@/lib/api/types";

const ACTIVITY = {
  activityVersionId: "av1",
  activityId: "a1",
  title: "Count",
  instructions: null,
  difficultyLevel: 1,
  estimatedDurationSeconds: null,
  introduction: null,
  items: [
    { activityItemId: "i1", sequenceOrder: 0, questionVersionId: "q1", questionType: "NUMERIC", prompt: "2 + 3 = ?", options: [], writtenIn: "en" },
    { activityItemId: "i2", sequenceOrder: 1, questionVersionId: "q2", questionType: "NUMERIC", prompt: "4 + 4 = ?", options: [], writtenIn: "en" },
  ],
} as unknown as ActivityVersionDetail;

const ANSWERS: AnswerRecord[] = [
  { id: "r1", questionVersionId: "q1", correct: true, timeSpentSeconds: 4, occurredAt: "2026-11-03T08:00:00Z", correctAnswer: null },
  { id: "r2", questionVersionId: "q2", correct: false, timeSpentSeconds: 6, occurredAt: "2026-11-03T08:01:00Z",
    correctAnswer: { optionIds: [], text: "8" }, explanation: "Four and four more make eight." },
];

afterEach(() => vi.restoreAllMocks());

describe("what a child missed, for their grown-up", () => {
  it("reads nothing until opened, then shows each missed question with the answer and why", async () => {
    const answers = vi.spyOn(activityAttemptApi, "listAnswers").mockResolvedValue(ANSWERS);
    const content = vi.spyOn(activityContentApi, "getVersion").mockResolvedValue(ACTIVITY);
    const user = userEvent.setup();
    render(<MissedQuestions attemptId="t1" activityVersionId="av1" childId="c1" missed={1} />);
    expect(answers).not.toHaveBeenCalled();

    await user.click(screen.getByText("See the question they missed"));
    expect(await screen.findByText("4 + 4 = ?")).toBeInTheDocument();
    expect(content).toHaveBeenCalledWith("av1", "c1");
    expect(screen.getByText("8")).toBeInTheDocument();
    expect(screen.getByText("Four and four more make eight.")).toBeInTheDocument();
    expect(screen.queryByText("2 + 3 = ?")).not.toBeInTheDocument();
  });

  it("says when the questions can't be loaded, and tries again when reopened", async () => {
    vi.spyOn(activityAttemptApi, "listAnswers").mockRejectedValueOnce(new Error("offline")).mockResolvedValue(ANSWERS);
    vi.spyOn(activityContentApi, "getVersion").mockResolvedValue(ACTIVITY);
    const user = userEvent.setup();
    render(<MissedQuestions attemptId="t1" activityVersionId="av1" childId="c1" missed={1} />);
    const summary = screen.getByText("See the question they missed");
    await user.click(summary);
    expect(await screen.findByRole("alert")).toHaveTextContent("couldn't be loaded");
    await user.click(summary);
    await user.click(summary);
    expect(await screen.findByText("4 + 4 = ?")).toBeInTheDocument();
  });
});
