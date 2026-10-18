import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { MediaLibraryPage } from "./MediaLibrary";
import { QuestionDraftForm } from "./QuestionDraftForm";
import { studioApi, type MediaItem } from "@/lib/api/studio";
import type { Page } from "@/lib/api/types";
import { storageApi } from "@/lib/api/storage";
import { ApiError } from "@/lib/api/ApiError";

const item = (over: Partial<MediaItem>): MediaItem => ({
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
  usedBy: 2,
  editable: true,
  ...over,
});

const page = (items: MediaItem[]): Page<MediaItem> => ({ items, page: 0, size: 24, total: items.length });

function Where() {
  const location = useLocation();
  return <output aria-label="address">{location.search}</output>;
}

describe("MediaLibraryPage", () => {
  it("lists the library, keeps its filters in the address, and opens an item with where it is used", async () => {
    const library = vi.spyOn(studioApi, "mediaLibrary").mockResolvedValue(page([item({}), item({ id: "m2", type: "AUDIO", title: "Cat (word)", usedBy: 0 })]));
    vi.spyOn(studioApi, "mediaDetail").mockResolvedValue({
      item: item({}),
      uses: [
        {
          questionId: "q1",
          subjectId: "s1",
          versionId: "v1",
          versionNumber: 2,
          status: "PUBLISHED",
          prompt: "How many apples?",
          field: "PROMPT_IMAGE",
        },
      ],
    });
    const archive = vi.spyOn(studioApi, "archiveMedia").mockResolvedValue(item({ archived: true }));
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/admin/media"]}>
        <Routes>
          <Route
            path="/admin/media"
            element={
              <>
                <MediaLibraryPage />
                <Where />
              </>
            }
          />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByRole("button", { name: /Open: Three apples, picture, used in 2 questions/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Open: Cat \(word\), recording, not used yet/ })).toBeInTheDocument();

    await user.click(within(screen.getByRole("group", { name: "Kind" })).getByRole("button", { name: "Recordings" }));
    expect(screen.getByRole("status", { name: "address" })).toHaveTextContent("type=AUDIO");
    expect(library).toHaveBeenLastCalledWith(expect.objectContaining({ type: "AUDIO", page: 0 }));
    await user.selectOptions(screen.getByLabelText("Show"), "archived");
    expect(library).toHaveBeenLastCalledWith(expect.objectContaining({ archived: true }));

    await user.click(screen.getByRole("button", { name: /Open: Three apples/ }));
    const dialog = await screen.findByRole("dialog", { name: "Three apples" });
    expect(within(dialog).getByText("How many apples?").closest("a")).toHaveAttribute("href", "/admin/studio/s1/questions/q1");
    expect(within(dialog).getByText(/Question picture · version 2/)).toBeInTheDocument();
    // In use: it can be archived, never deleted.
    expect(within(dialog).queryByRole("button", { name: /Delete/ })).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Archive" }));
    expect(archive).toHaveBeenCalledWith("m1");
    expect(await within(dialog).findByRole("status")).toHaveTextContent("questions using it keep it");
  });
});

describe("Storage in the media library", () => {
  it("shows how full the author's storage is, and says why an upload that doesn't fit was refused", async () => {
    vi.spyOn(studioApi, "mediaLibrary").mockResolvedValue(page([item({})]));
    const own = vi.spyOn(storageApi, "own").mockResolvedValue({
      usedBytes: 900 * 1024 * 1024,
      allowance: { limitMb: 1024, limitBytes: 1024 * 1024 * 1024, source: "SCHOOL_PLAN" },
      remainingBytes: 124 * 1024 * 1024,
      state: "NEARLY_FULL",
      slices: [
        { type: "IMAGE", archived: false, unused: true, files: 4, bytes: 300 * 1024 * 1024 },
        { type: "AUDIO", archived: false, unused: false, files: 10, bytes: 600 * 1024 * 1024 },
      ],
      largest: [],
    });
    vi.spyOn(studioApi, "uploadMedia").mockRejectedValue(
      new ApiError(409, {
        type: "about:blank",
        title: "Storage Limit Reached",
        status: 409,
        detail: "Your storage is full: 900 MB of 1024 MB is used, and this file needs 4.5 MB. Remove media you no longer use, or ask for more space.",
        usedBytes: 900 * 1024 * 1024,
        limitBytes: 1024 * 1024 * 1024,
        neededBytes: 4.5 * 1024 * 1024,
      }),
    );
    const user = userEvent.setup();
    const { container } = render(
      <MemoryRouter initialEntries={["/admin/media"]}>
        <MediaLibraryPage />
      </MemoryRouter>,
    );

    const storage = await screen.findByRole("region", { name: "Storage" });
    expect(within(storage).getByText(/900 MB of 1 GB used/)).toBeInTheDocument();
    expect(within(storage).getByText(/124 MB left/)).toBeInTheDocument();
    expect(within(storage).getByRole("meter", { name: "Storage" })).toHaveAttribute("aria-valuenow", "88");
    expect(within(storage).getByRole("status")).toHaveTextContent("Nearly full.");
    expect(within(storage).getByText(/comes from your school's plan/)).toBeInTheDocument();
    expect(within(storage).getByRole("link", { name: "Show them" })).toHaveAttribute("href", "/admin/media?show=unused");

    const input = container.querySelector<HTMLInputElement>('input[type="file"]')!;
    await user.upload(input, new File(["x"], "big recording.wav", { type: "audio/wav" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "big recording.wav: Your storage is full: 900 MB of 1024 MB is used, and this file needs 4.5 MB.",
    );
    // A refused upload still refreshes what the meter shows.
    expect(own).toHaveBeenCalledTimes(2);
  });
});

describe("Choosing from the library in a question", () => {
  it("uses the chosen picture and offers its description to screen readers", async () => {
    vi.spyOn(studioApi, "mediaLibrary").mockResolvedValue(page([item({})]));
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <QuestionDraftForm
          questionType="MULTIPLE_CHOICE"
          initial={{ prompt: "How many apples?", correctAnswer: null, difficultyLevel: null }}
          submitLabel="Create draft"
          busy={false}
          onSubmit={onSubmit}
        />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole("button", { name: "Choose a picture for the question from the library" }));
    const picker = await screen.findByRole("dialog", { name: "Choose a picture" });
    await user.click(within(picker).getByRole("button", { name: /Use: Three apples/ }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Describe the picture/)).toHaveValue("Three red apples on a plate");

    // A picture choice takes the picture's words as its label when it has none.
    await user.click(screen.getByRole("button", { name: "Choose a picture for choice 1 from the library" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: /Use: Three apples/ }));
    expect(screen.getByLabelText("Choice 1")).toHaveValue("Three red apples on a plate");

    await user.type(screen.getByLabelText("Choice 2"), "Four");
    await user.click(screen.getByRole("button", { name: "Create draft" }));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        promptImageAssetId: "m1",
        promptImageDescription: "Three red apples on a plate",
        options: [
          { label: "Three red apples on a plate", correct: true, imageAssetId: "m1", audioAssetId: undefined },
          { label: "Four", correct: false, imageAssetId: undefined, audioAssetId: undefined },
        ],
      }),
    );
  });
});
