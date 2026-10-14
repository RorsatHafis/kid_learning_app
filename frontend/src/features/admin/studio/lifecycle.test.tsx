import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ArchivedSwitch, ManageButton } from "./Lifecycle";
import { studioApi } from "@/lib/api/studio";
import { ApiError } from "@/lib/api/ApiError";

describe("ManageButton", () => {
  it("renames, and archives only after saying what archiving does", async () => {
    const update = vi.spyOn(studioApi, "updateDetails").mockResolvedValue(undefined);
    const archive = vi.spyOn(studioApi, "archive").mockResolvedValue(undefined);
    const onChanged = vi.fn();
    const user = userEvent.setup();
    render(<ManageButton kind="activities" id="a1" name="Count the stars" onChanged={onChanged} />);

    await user.click(screen.getByRole("button", { name: "Manage activity: Count the stars" }));
    const dialog = screen.getByRole("dialog", { name: "Manage activity" });
    const title = within(dialog).getByLabelText("Title");
    await user.clear(title);
    await user.type(title, "Count the bright stars");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    expect(update).toHaveBeenCalledWith("activities", "a1", { name: "Count the bright stars", description: undefined });
    expect(onChanged).toHaveBeenLastCalledWith("updated");

    expect(within(dialog).getByText(/no longer placed on new paths/)).toBeInTheDocument();
    expect(within(dialog).getByText(/Nothing is deleted/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Archive…" }));
    expect(archive).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole("button", { name: "Archive the activity" }));
    expect(archive).toHaveBeenCalledWith("activities", "a1");
    expect(onChanged).toHaveBeenLastCalledWith("archived");
  });

  it("restores, and says why when the server refuses", async () => {
    vi.spyOn(studioApi, "restore").mockRejectedValueOnce(
      new ApiError(409, { title: "Conflict", status: 409, detail: "Its lesson is archived. Restore the lesson first." }),
    );
    const user = userEvent.setup();
    render(<ManageButton kind="activities" id="a1" name="Count the stars" archived wording="restore" onChanged={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: "Restore activity: Count the stars" }));
    await user.click(screen.getByRole("button", { name: "Restore" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Restore the lesson first");
  });

  it("does not offer renaming a question (its words live in its versions)", async () => {
    const user = userEvent.setup();
    render(<ManageButton kind="questions" id="q1" name="2 + 2 = ?" renamable={false} onChanged={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: "Manage question: 2 + 2 = ?" }));
    expect(screen.queryByLabelText("Name")).not.toBeInTheDocument();
    expect(screen.getByText(/leaves the question bank/)).toBeInTheDocument();
  });
});

describe("ArchivedSwitch", () => {
  it("switches between what is in use and what is archived", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<ArchivedSwitch archived={false} onChange={onChange} list="curricula" />);
    expect(screen.getByRole("button", { name: "In use" })).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: "Archived" }));
    expect(onChange).toHaveBeenCalledWith(true);
  });
});
