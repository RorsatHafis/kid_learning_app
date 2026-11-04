/**
 * A media item's dialog keeps a typed title or description: closing with it unsaved asks first - "Stay" keeps the
 * dialog and the text - and once saved it closes without asking.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { MediaLibraryPage } from "./MediaLibrary";
import { studioApi, type MediaItem } from "@/lib/api/studio";

afterEach(() => vi.restoreAllMocks());

const item = (over: Partial<MediaItem> = {}): MediaItem => ({
  id: "m1",
  type: "IMAGE",
  mimeType: "image/png",
  url: "/api/v1/media/m1?e=1&s=a",
  sizeBytes: 20480,
  width: 400,
  height: 300,
  title: "Three apples",
  description: "Three red apples on a plate",
  archived: false,
  createdAt: "2026-09-30T08:00:00Z",
  usedBy: 0,
  editable: true,
  ...over,
});

async function openItem() {
  vi.spyOn(studioApi, "mediaLibrary").mockResolvedValue({ items: [item()], page: 0, size: 24, total: 1 });
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={["/admin/media"]}>
      <MediaLibraryPage />
    </MemoryRouter>,
  );
  await user.click(await screen.findByRole("button", { name: /Open: Three apples/ }));
  const dialog = await screen.findByRole("dialog", { name: "Three apples" });
  return { user, dialog };
}

describe("closing a media item's dialog with an unsaved title", () => {
  it("asks first, and Stay keeps the dialog and what was typed", async () => {
    vi.spyOn(studioApi, "mediaDetail").mockResolvedValue({ item: item(), uses: [] });
    const ask = vi.spyOn(window, "confirm").mockReturnValue(false);
    const { user, dialog } = await openItem();
    await user.type(within(dialog).getByLabelText("Title"), " (red)");

    await user.click(within(dialog).getByRole("button", { name: "Close" }));
    expect(ask).toHaveBeenCalledWith("Your changes aren't saved yet. Leave this page and lose them?");
    expect(within(screen.getByRole("dialog")).getByLabelText("Title")).toHaveValue("Three apples (red)");

    ask.mockReturnValue(true);
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("closes without asking once the title is saved", async () => {
    const detail = vi.spyOn(studioApi, "mediaDetail").mockResolvedValue({ item: item(), uses: [] });
    vi.spyOn(studioApi, "describeMedia").mockImplementation(async () => {
      detail.mockResolvedValue({ item: item({ title: "Three apples (red)" }), uses: [] });
      return item({ title: "Three apples (red)" });
    });
    const ask = vi.spyOn(window, "confirm");
    const { user, dialog } = await openItem();
    await user.type(within(dialog).getByLabelText("Title"), " (red)");
    await user.click(within(dialog).getByRole("button", { name: "Save details" }));
    await screen.findByRole("dialog", { name: "Three apples (red)" });

    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Close" }));
    expect(ask).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
