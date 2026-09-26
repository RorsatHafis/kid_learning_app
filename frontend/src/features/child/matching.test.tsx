import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QuestionView } from "./QuestionView";
import { QuestionDraftForm } from "@/features/admin/studio/QuestionDraftForm";
import type { ActivityItem } from "@/lib/api/types";

// What the server sends: two columns, no pairing, the right column in its own order.
const ITEM: ActivityItem = {
  activityItemId: "i1",
  sequenceOrder: 0,
  questionVersionId: "qv1",
  questionType: "MATCHING",
  prompt: "Match each number to its dots",
  options: [
    { id: "L1", label: "1", displayOrder: 0, matchSide: "LEFT" },
    { id: "L2", label: "2", displayOrder: 1, matchSide: "LEFT" },
    { id: "R2", label: "••", displayOrder: 0, matchSide: "RIGHT" },
    { id: "R1", label: "•", displayOrder: 1, matchSide: "RIGHT" },
  ],
};

const card = (name: RegExp) => screen.getByRole("button", { name });

describe("MatchingQuestion", () => {
  it("pairs by tapping one item then its match, and sends every pair in the order the child sees them", async () => {
    const onAnswer = vi.fn();
    const user = userEvent.setup();
    render(<QuestionView item={ITEM} onAnswer={onAnswer} disabled={false} result={null} />);

    const check = screen.getByRole("button", { name: "Check my matches" });
    expect(check).toBeDisabled();

    await user.click(card(/^2, not matched/));
    expect(card(/^2, not matched/)).toHaveAttribute("aria-pressed", "true");
    await user.click(card(/^••, not matched/));
    expect(card(/^2, matched with ••/)).toBeInTheDocument();
    expect(screen.getByText("Matched 2 with ••.")).toBeInTheDocument();
    expect(check).toBeDisabled();

    // Right first, then left, works too — and with the keyboard.
    card(/^•, not matched/).focus();
    await user.keyboard("{Enter}");
    card(/^1, not matched/).focus();
    await user.keyboard("{Enter}");

    expect(check).toBeEnabled();
    await user.click(check);
    expect(onAnswer).toHaveBeenCalledWith(JSON.stringify({ L1: "R1", L2: "R2" }));
  });

  it("moves a match that was already taken instead of pairing it twice", async () => {
    const user = userEvent.setup();
    render(<QuestionView item={ITEM} onAnswer={vi.fn()} disabled={false} result={null} />);

    await user.click(card(/^1, not matched/));
    await user.click(card(/^•, not matched/));
    await user.click(card(/^2, not matched/));
    await user.click(card(/^•, matched with 1/));

    expect(card(/^2, matched with •$/)).toBeInTheDocument();
    expect(card(/^1, not matched/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Check my matches" })).toBeDisabled();
  });

  it("after a wrong answer, shows which pairs were right and where the others go", () => {
    render(
      <QuestionView
        item={ITEM}
        onAnswer={vi.fn()}
        disabled={false}
        result={{
          submitted: JSON.stringify({ L1: "R2", L2: "R1" }),
          correct: false,
          correctAnswer: {
            optionIds: [],
            text: "1 → •, 2 → ••",
            matches: [
              { leftOptionId: "L1", rightOptionId: "R1" },
              { leftOptionId: "L2", rightOptionId: "R2" },
            ],
          },
        }}
      />,
    );

    expect(card(/^1, matched with ••, the match is •$/)).toBeInTheDocument();
    expect(screen.getByText("Goes with •")).toBeInTheDocument();
    expect(screen.getByText("Goes with ••")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Check my matches" })).not.toBeInTheDocument();
  });
});

describe("QuestionDraftForm (MATCHING)", () => {
  it("authors pairs, one item with its match per row", async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      <QuestionDraftForm
        questionType="MATCHING"
        initial={{ prompt: "", correctAnswer: null, difficultyLevel: null }}
        submitLabel="Save draft"
        busy={false}
        onSubmit={onSubmit}
      />,
    );

    await user.type(screen.getByLabelText("Question the child sees"), "Match each number to its dots");
    await user.type(screen.getByLabelText("Pair 1: item"), "1");
    await user.type(screen.getByLabelText("Pair 1: its match"), "•");
    expect(screen.getByRole("button", { name: "Save draft" })).toBeDisabled();
    await user.type(screen.getByLabelText("Pair 2: item"), "2");
    await user.type(screen.getByLabelText("Pair 2: its match"), "••");
    await user.click(screen.getByRole("button", { name: "Save draft" }));

    expect(onSubmit).toHaveBeenCalledWith({
      prompt: "Match each number to its dots",
      correctAnswer: null,
      difficultyLevel: null,
      options: undefined,
      pairs: [
        { left: "1", right: "•" },
        { left: "2", right: "••" },
      ],
    });
  });
});
