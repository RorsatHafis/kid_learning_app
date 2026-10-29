/**
 * A question draft with typed, unsaved changes says so (the editor won't copy from it then) and asks before the tab
 * is reloaded or closed; once the saved values match, it is clean again.
 */
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QuestionDraftForm } from "./QuestionDraftForm";

const saved = { prompt: "2 + 3 = ?", correctAnswer: "5", difficultyLevel: 1, hint: null, explanation: null };

function form(initial = saved, onDirtyChange = vi.fn()) {
  const view = render(
    <QuestionDraftForm questionType="NUMERIC" initial={initial} submitLabel="Save draft" busy={false} onSubmit={vi.fn()} onDirtyChange={onDirtyChange} />,
  );
  return { onDirtyChange, rerender: (next: typeof saved) =>
    view.rerender(<QuestionDraftForm questionType="NUMERIC" initial={next} submitLabel="Save draft" busy={false} onSubmit={vi.fn()} onDirtyChange={onDirtyChange} />) };
}

function unloadWarns() {
  const event = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(event);
  return event.defaultPrevented;
}

describe("unsaved question drafts", () => {
  it("is clean as loaded, dirty once something is typed, and asks before the page goes", async () => {
    const { onDirtyChange, rerender } = form();
    expect(onDirtyChange).toHaveBeenLastCalledWith(false);
    expect(unloadWarns()).toBe(false);

    await userEvent.type(screen.getByLabelText("Question the child sees"), " Count on.");
    expect(onDirtyChange).toHaveBeenLastCalledWith(true);
    expect(unloadWarns()).toBe(true);

    // Saved: the editor gives the form the saved values; nothing is unsaved any more.
    rerender({ ...saved, prompt: "2 + 3 = ? Count on." });
    expect(onDirtyChange).toHaveBeenLastCalledWith(false);
    expect(unloadWarns()).toBe(false);
  });

  it("doesn't count spaces around the words as a change", async () => {
    const { onDirtyChange } = form();
    await userEvent.type(screen.getByLabelText("Question the child sees"), "   ");
    expect(onDirtyChange).toHaveBeenLastCalledWith(false);
  });
});
