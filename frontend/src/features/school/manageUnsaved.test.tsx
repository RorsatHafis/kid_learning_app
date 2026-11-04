/**
 * Renaming a class or a school in its Manage dialog keeps what was typed: closing with a name not saved yet asks
 * first - "Stay" keeps the dialog and the text - and once saved (before the page has reloaded) it closes without asking.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ManageClassButton, ManageSchoolButton } from "./ClassLifecycle";
import { schoolApi } from "@/lib/api/endpoints";
import type { SchoolClassSummary, SchoolSummary } from "@/lib/api/types";

afterEach(() => vi.restoreAllMocks());

describe("closing a class's Manage dialog with an unsaved name", () => {
  it("asks first, and Stay keeps the dialog and what was typed", async () => {
    const ask = vi.spyOn(window, "confirm").mockReturnValue(false);
    const user = userEvent.setup();
    render(<ManageClassButton classId="c1" name="Grade 1A" archived={false} onChanged={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: /Manage class/ }));
    const name = within(screen.getByRole("dialog")).getByLabelText(/Class name/);
    await user.clear(name);
    await user.type(name, "Grade 1B");

    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Close" }));
    expect(ask).toHaveBeenCalledWith("Your changes aren't saved yet. Close and lose them?");
    expect(within(screen.getByRole("dialog")).getByLabelText(/Class name/)).toHaveValue("Grade 1B");

    ask.mockReturnValue(true);
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("closes without asking when nothing changed, and after a save the page hasn't reloaded yet", async () => {
    const ask = vi.spyOn(window, "confirm");
    vi.spyOn(schoolApi, "renameClass").mockResolvedValue({ id: "c1", name: "Grade 1B" } as SchoolClassSummary);
    const user = userEvent.setup();
    render(<ManageClassButton classId="c1" name="Grade 1A" archived={false} onChanged={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: /Manage class/ }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Manage class/ }));
    const dialog = screen.getByRole("dialog");
    const name = within(dialog).getByLabelText(/Class name/);
    await user.clear(name);
    await user.type(name, "Grade 1B");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    await within(dialog).findByRole("status");
    await user.click(within(dialog).getByRole("button", { name: "Close" }));
    expect(ask).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});

describe("closing a school's Manage dialog with an unsaved name", () => {
  it("asks first; a save makes it close without asking", async () => {
    const ask = vi.spyOn(window, "confirm").mockReturnValue(false);
    vi.spyOn(schoolApi, "renameSchool").mockResolvedValue({ id: "s1", name: "Riverside School" } as SchoolSummary);
    const user = userEvent.setup();
    render(<ManageSchoolButton schoolId="s1" name="Riverside" archived={false} onChanged={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: /Manage school/ }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByLabelText(/School name/), " School");

    // A click beside the panel (on the dialog's backdrop). Escape is checked in a real browser: jsdom has no showModal.
    await user.click(dialog);
    expect(ask).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    await within(dialog).findByRole("status");
    await user.click(within(dialog).getByRole("button", { name: "Close" }));
    expect(ask).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
