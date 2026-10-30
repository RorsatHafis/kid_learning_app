/**
 * An activity's "More settings" (instructions, difficulty, time, and their Khmer) keep an author's unsaved changes
 * from being lost: they say so beside the button and leaving the page asks first - like a question's form and the
 * introduction. Once saved, there is nothing left to warn about.
 */
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DetailsForm } from "./ActivityEditor";
import type { ActivityVersionDetail } from "@/lib/api/studio";

const version = {
  id: "v1",
  versionNumber: 1,
  status: "DRAFT",
  publishedAt: null,
  instructions: "Count the birds.",
  difficultyLevel: 1,
  estimatedDurationSeconds: null,
  items: [],
  skillIds: [],
  introduction: null,
  khmer: { title: null, instructions: null, introduction: null, introductionImageDescription: null, complete: false },
} as unknown as ActivityVersionDetail;

function leaving() {
  const event = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(event);
  return event.defaultPrevented;
}

describe("More settings with unsaved changes", () => {
  it("says so and asks before the page is left, until saved", async () => {
    const onSave = vi.fn();
    const user = userEvent.setup();
    const { rerender } = render(<DetailsForm title="Count the birds" version={version} busy={false} onSave={onSave} />);
    expect(screen.queryByText("Not saved yet.")).not.toBeInTheDocument();
    expect(leaving()).toBe(false);

    await user.type(screen.getByLabelText("Instructions the child hears before starting"), " Then say the number.");
    expect(screen.getByText("Not saved yet.")).toBeInTheDocument();
    expect(leaving()).toBe(true);

    await user.click(screen.getByRole("button", { name: "Save details" }));
    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ instructions: "Count the birds. Then say the number." }));
    // The saved version now holds what is on screen.
    rerender(
      <DetailsForm
        title="Count the birds"
        version={{ ...version, instructions: "Count the birds. Then say the number." }}
        busy={false}
        onSave={onSave}
      />,
    );
    expect(screen.queryByText("Not saved yet.")).not.toBeInTheDocument();
    expect(leaving()).toBe(false);
  });

  it("counts Khmer typed in its own field as unsaved too", async () => {
    const user = userEvent.setup();
    render(<DetailsForm title="Count the birds" version={version} busy={false} onSave={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: "ខ្មែរ" }));
    await user.type(screen.getByLabelText(/Instructions the child hears before starting - in Khmer/), "រាប់បក្សី។");
    expect(screen.getByText("Not saved yet.")).toBeInTheDocument();
    // Switching back shows the English untouched, and the change is still unsaved.
    await user.click(screen.getByRole("button", { name: "English" }));
    expect(screen.getByLabelText("Instructions the child hears before starting")).toHaveValue("Count the birds.");
    expect(screen.getByText("Not saved yet.")).toBeInTheDocument();
  });
});
