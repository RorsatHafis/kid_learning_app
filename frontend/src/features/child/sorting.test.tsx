import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QuestionView } from "./QuestionView";
import type { ActivityItem } from "@/lib/api/types";

// What the server sends (X23): the groups, and the items mixed up - never which group an item belongs to.
const EVEN_ODD: ActivityItem = {
  activityItemId: "i1",
  sequenceOrder: 0,
  questionVersionId: "qv-sort",
  questionType: "SORTING",
  prompt: "Even or odd?",
  options: [
    { id: "even", label: "Even", displayOrder: 0, sortRole: "GROUP" },
    { id: "odd", label: "Odd", displayOrder: 1, sortRole: "GROUP" },
    { id: "s3", label: "3", displayOrder: 0, sortRole: "ITEM" },
    { id: "s4", label: "4", displayOrder: 1, sortRole: "ITEM" },
    { id: "s7", label: "7", displayOrder: 2, sortRole: "ITEM" },
    { id: "s2", label: "2", displayOrder: 3, sortRole: "ITEM" },
  ],
};

const groups = () => screen.getByRole("list", { name: "Groups" });
const tray = () => screen.getByRole("list", { name: "Things to sort" });

describe("SortingQuestion (X23)", () => {
  it("puts each item in the group the child chooses, lets one be taken back, and submits where every item went", async () => {
    const onAnswer = vi.fn();
    const user = userEvent.setup();
    render(<QuestionView item={EVEN_ODD} onAnswer={onAnswer} disabled={false} result={null} />);
    const check = screen.getByRole("button", { name: "Check my groups" });
    expect(check).toBeDisabled();

    // Pick up, then choose the group.
    await user.click(within(tray()).getByRole("button", { name: "3: pick it up" }));
    expect(within(tray()).getByRole("button", { name: "3, picked up" })).toHaveAttribute("aria-pressed", "true");
    await user.click(within(groups()).getByRole("button", { name: /^Odd: empty\. Put it here$/ }));
    expect(within(groups()).getByRole("button", { name: "3 in Odd. Tap to take it out" })).toBeInTheDocument();

    // A wrong placement taken back out and put right.
    await user.click(within(tray()).getByRole("button", { name: "4: pick it up" }));
    await user.click(within(groups()).getByRole("button", { name: /^Odd: 3\. Put it here$/ }));
    await user.click(within(groups()).getByRole("button", { name: "4 in Odd. Tap to take it out" }));
    await user.click(within(groups()).getByRole("button", { name: /^Even: empty\. Put it here$/ }));

    for (const [label, group] of [
      ["7", /^Odd/],
      ["2", /^Even/],
    ] as const) {
      await user.click(within(tray()).getByRole("button", { name: `${label}: pick it up` }));
      await user.click(within(groups()).getByRole("button", { name: group }));
    }
    expect(within(tray()).getByText("All sorted!")).toBeInTheDocument();
    await user.click(check);
    expect(JSON.parse(onAnswer.mock.calls[0][0])).toEqual({ s3: "odd", s4: "even", s7: "odd", s2: "even" });
  });

  it("does nothing when a group is tapped with nothing picked up", async () => {
    const user = userEvent.setup();
    render(<QuestionView item={EVEN_ODD} onAnswer={vi.fn()} disabled={false} result={null} />);
    await user.click(within(groups()).getByRole("button", { name: /^Even/ }));
    expect(within(groups()).getByRole("button", { name: "Even: empty" })).toHaveAttribute("aria-disabled", "true");
    expect(within(tray()).getAllByRole("button")).toHaveLength(4);
  });

  it("after answering, shows where each item went and where a misplaced one belongs", () => {
    render(
      <QuestionView
        item={EVEN_ODD}
        onAnswer={vi.fn()}
        disabled={false}
        result={{
          submitted: JSON.stringify({ s3: "odd", s4: "even", s7: "even", s2: "even" }),
          correct: false,
          correctAnswer: {
            optionIds: [],
            text: "3 → Odd, 4 → Even, 7 → Odd, 2 → Even",
            matches: [
              { leftOptionId: "s3", rightOptionId: "odd" },
              { leftOptionId: "s4", rightOptionId: "even" },
              { leftOptionId: "s7", rightOptionId: "odd" },
              { leftOptionId: "s2", rightOptionId: "even" },
            ],
          },
        }}
      />,
    );
    expect(screen.getByText("Here's where you put them.")).toBeInTheDocument();
    expect(within(groups()).getByRole("button", { name: "3 in Odd, right" })).toBeInTheDocument();
    expect(within(groups()).getByRole("button", { name: "7 in Even, belongs in Odd" })).toBeInTheDocument();
    expect(within(groups()).getByText("Goes in Odd")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Check my groups" })).not.toBeInTheDocument();
  });
});
