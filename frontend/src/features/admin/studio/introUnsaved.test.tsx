/**
 * "Teach it first" saves on its own: typed but unsaved, it says so (and the builder won't publish without it).
 */
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntroductionEditor } from "./IntroductionEditor";
import type { ActivityVersionDetail } from "@/lib/api/studio";

const version = {
  id: "v1",
  versionNumber: 1,
  status: "DRAFT",
  publishedAt: null,
  instructions: null,
  difficultyLevel: null,
  estimatedDurationSeconds: null,
  items: [],
  skillIds: [],
  introduction: { text: "2 birds and 1 more make 3.", image: null, imageDescription: null, audio: null },
} as unknown as ActivityVersionDetail;

describe("an unsaved introduction", () => {
  it("is clean as saved, says 'Not saved yet' once changed, and clean again when changed back", async () => {
    const onDirtyChange = vi.fn();
    render(<IntroductionEditor version={version} onSaved={vi.fn()} onDirtyChange={onDirtyChange} />);
    expect(onDirtyChange).toHaveBeenLastCalledWith(false);
    expect(screen.queryByText("Not saved yet.")).not.toBeInTheDocument();

    const text = screen.getByRole("textbox", { name: /Explanation or example/ });
    await userEvent.type(text, " We write 2 + 1 = 3.");
    expect(onDirtyChange).toHaveBeenLastCalledWith(true);
    expect(screen.getByText("Not saved yet.")).toBeInTheDocument();
    const unload = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(unload);
    expect(unload.defaultPrevented).toBe(true);

    await userEvent.clear(text);
    await userEvent.type(text, "2 birds and 1 more make 3.  ");
    expect(onDirtyChange).toHaveBeenLastCalledWith(false);
    expect(screen.queryByText("Not saved yet.")).not.toBeInTheDocument();
  });
});
