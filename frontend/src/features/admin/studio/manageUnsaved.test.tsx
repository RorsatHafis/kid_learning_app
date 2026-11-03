/**
 * A Manage dialog (a skill's, chapter's, curriculum's or lesson's names) keeps what was typed: closing it with unsaved
 * names in either language asks first - "Stay" keeps the dialog and the text - and once saved it closes without asking.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ManageButton } from "./Lifecycle";
import { studioApi } from "@/lib/api/studio";

afterEach(() => vi.restoreAllMocks());

function renderSkill() {
  render(
    <ManageButton
      kind="skills"
      id="s1"
      name="Counting"
      description="Counting to ten."
      describable
      khmer={{ name: null, description: null }}
      onChanged={vi.fn()}
    />,
  );
}

describe("closing a Manage dialog with unsaved names", () => {
  it("asks first, and Stay keeps the dialog and what was typed", async () => {
    const ask = vi.spyOn(window, "confirm").mockReturnValue(false);
    const user = userEvent.setup();
    renderSkill();
    await user.click(screen.getByRole("button", { name: /Counting/ }));
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "ខ្មែរ" }));
    await user.type(within(dialog).getByLabelText(/Name - in Khmer/), "ការរាប់");

    await user.click(within(dialog).getByRole("button", { name: "Close" }));
    expect(ask).toHaveBeenCalledWith("Your changes aren't saved yet. Leave this page and lose them?");
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(within(screen.getByRole("dialog")).getByLabelText(/Name - in Khmer/)).toHaveValue("ការរាប់");

    ask.mockReturnValue(true);
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("closes without asking when nothing is unsaved, and after a save", async () => {
    const ask = vi.spyOn(window, "confirm");
    vi.spyOn(studioApi, "updateDetails").mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderSkill();
    await user.click(screen.getByRole("button", { name: /Counting/ }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Close" }));
    expect(ask).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Counting/ }));
    const dialog = screen.getByRole("dialog");
    const name = within(dialog).getByLabelText("Name");
    await user.clear(name);
    await user.type(name, "Counting objects");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    await screen.findByText(/saved/i);
    await user.click(within(dialog).getByRole("button", { name: "Close" }));
    expect(ask).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
