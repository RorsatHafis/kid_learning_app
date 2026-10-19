import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QuestionView } from "./QuestionView";
import type { ActivityItem } from "@/lib/api/types";

const SUM: ActivityItem = {
  activityItemId: "i1",
  sequenceOrder: 0,
  questionVersionId: "qv-sum",
  questionType: "NUMERIC",
  prompt: "1 + 2 = ?",
  options: [],
};

describe("feedback on a typed answer (Y40)", () => {
  it("stands the right answer beside a wrong one, where the child's answer is", () => {
    const { container } = render(
      <QuestionView item={SUM} onAnswer={vi.fn()} disabled={false} result={{ submitted: "7", correct: false, correctAnswer: { optionIds: [], text: "3" } }} />,
    );
    expect(screen.getByLabelText("Your answer")).toHaveValue("7");
    // Decorative: the feedback words say it to a screen reader.
    const shown = [...container.querySelectorAll("[aria-hidden] span")].map((s) => s.textContent);
    expect(shown).toContain("3");
  });

  it("shows nothing extra when the answer was right", () => {
    const { container } = render(
      <QuestionView item={SUM} onAnswer={vi.fn()} disabled={false} result={{ submitted: "3", correct: true, correctAnswer: { optionIds: [], text: "3" } }} />,
    );
    expect([...container.querySelectorAll("[aria-hidden] span")].map((s) => s.textContent)).not.toContain("3");
  });
});
