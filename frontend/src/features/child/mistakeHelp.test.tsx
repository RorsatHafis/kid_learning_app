/**
 * After a mistake the child is given the author's help, not only the right answer: the explanation when the question
 * has one; otherwise the author's hint, as a way to think it through. A right answer adds no hint.
 */
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { AnswerVerdict } from "./AnswerVerdict";
import type { ActivityItem } from "@/lib/api/types";

const SUM: ActivityItem = {
  activityItemId: "i1",
  sequenceOrder: 0,
  questionVersionId: "q1",
  questionType: "NUMERIC",
  prompt: "5 + 4 = ?",
  options: [],
  hint: "Start at 5 and count on four more.",
};

function verdict(item: ActivityItem, correct: boolean, explanation: string | null) {
  render(
    <AnswerVerdict
      item={item}
      index={0}
      total={2}
      result={{ submitted: correct ? "9" : "10", correct, correctAnswer: { optionIds: [], text: "9" }, explanation }}
      onContinue={vi.fn()}
    />,
  );
}

describe("help after an answer", () => {
  it("shows the author's hint as a way to think about it after a wrong answer without an explanation", () => {
    verdict(SUM, false, null);
    expect(screen.getByText("The answer is 9. Let's keep going!")).toBeInTheDocument();
    expect(screen.getByText("A way to think about it")).toBeInTheDocument();
    expect(screen.getByText("Start at 5 and count on four more.")).toBeInTheDocument();
    // It is part of what is announced with the verdict.
    expect(screen.getByRole("status")).toHaveTextContent("Start at 5 and count on four more.");
  });

  it("shows only the explanation when the author wrote one", () => {
    verdict(SUM, false, "5 and 4 more make 9: 6, 7, 8, 9.");
    expect(screen.getByText("Why")).toBeInTheDocument();
    expect(screen.getByText("5 and 4 more make 9: 6, 7, 8, 9.")).toBeInTheDocument();
    expect(screen.queryByText("A way to think about it")).not.toBeInTheDocument();
    expect(screen.queryByText(SUM.hint!)).not.toBeInTheDocument();
  });

  it("adds no hint to a right answer, and nothing when the author wrote neither", () => {
    verdict(SUM, true, null);
    expect(screen.queryByText(SUM.hint!)).not.toBeInTheDocument();
    verdict({ ...SUM, questionVersionId: "q2", hint: "  " }, false, null);
    expect(screen.queryByText("A way to think about it")).not.toBeInTheDocument();
  });
});
