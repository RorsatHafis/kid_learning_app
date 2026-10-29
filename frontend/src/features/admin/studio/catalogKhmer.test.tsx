/**
 * LOCALIZATION.md "Content in two languages", slice 3: a skill's, chapter's, subject's or curriculum's name in English
 * and Khmer - each in its own field, saved together, neither overwriting the other; a save that never touched Khmer
 * sends none.
 */
import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ManageButton } from "./Lifecycle";
import { studioApi } from "@/lib/api/studio";

describe("a catalog name in two languages", () => {
  it("renames a skill in English and writes its Khmer apart, saving both", async () => {
    const update = vi.spyOn(studioApi, "updateDetails").mockResolvedValue(undefined);
    const user = userEvent.setup();
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
    await user.click(screen.getByRole("button", { name: /Counting/ }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText(/No Khmer yet/)).toBeInTheDocument();

    const name = within(dialog).getByLabelText("Name");
    await user.clear(name);
    await user.type(name, "Counting objects");
    await user.click(within(dialog).getByRole("button", { name: "ខ្មែរ" }));
    const nameKm = within(dialog).getByLabelText(/Name - in Khmer/);
    expect(nameKm).toHaveAttribute("lang", "km");
    await user.type(nameKm, "ការរាប់");
    expect(within(dialog).getByText(/1 of 2 written/)).toBeInTheDocument();
    expect(within(dialog).getByText("Counting objects")).toBeInTheDocument(); // the English it translates, beside it
    await user.type(within(dialog).getByLabelText(/Description - in Khmer/), "រាប់ដល់ដប់។");
    expect(within(dialog).getByText(/Khmer complete/)).toBeInTheDocument();

    // Back to English: what was written there is as it was.
    await user.click(within(dialog).getByRole("button", { name: "English" }));
    expect(within(dialog).getByLabelText("Name")).toHaveValue("Counting objects");
    expect(within(dialog).getByLabelText("Description")).toHaveValue("Counting to ten.");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    expect(update).toHaveBeenCalledWith("skills", "s1", {
      name: "Counting objects",
      description: "Counting to ten.",
      khmer: { name: "ការរាប់", description: "រាប់ដល់ដប់។" },
    });
  });

  it("sends no Khmer when none was there and none is written, and clears Khmer emptied on purpose", async () => {
    const update = vi.spyOn(studioApi, "updateDetails").mockResolvedValue(undefined);
    const user = userEvent.setup();
    const { unmount } = render(
      <ManageButton kind="curricula" id="c1" name="First numbers" khmer={{ name: null, description: null }} onChanged={vi.fn()} />,
    );
    await user.click(screen.getByRole("button", { name: /First numbers/ }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Save" }));
    expect(update).toHaveBeenLastCalledWith("curricula", "c1", { name: "First numbers", description: undefined, khmer: undefined });
    expect(JSON.parse(JSON.stringify(update.mock.lastCall?.[2]))).not.toHaveProperty("khmer");
    unmount();

    render(<ManageButton kind="curricula" id="c1" name="First numbers" khmer={{ name: "លេខដំបូង", description: null }} onChanged={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: /First numbers/ }));
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "ខ្មែរ" }));
    expect(within(dialog).getByLabelText(/in Khmer/)).toHaveValue("លេខដំបូង");
    expect(within(dialog).queryByLabelText(/Description/)).not.toBeInTheDocument(); // a curriculum has a name only
    await user.clear(within(dialog).getByLabelText(/in Khmer/));
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    expect(update).toHaveBeenLastCalledWith("curricula", "c1", { name: "First numbers", description: undefined, khmer: { name: null } });
  });
});
