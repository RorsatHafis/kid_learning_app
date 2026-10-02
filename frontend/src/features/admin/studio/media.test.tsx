import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QuestionDraftForm } from "./QuestionDraftForm";
import { studioApi } from "@/lib/api/studio";
import { ApiError } from "@/lib/api/ApiError";
import type { MediaItem } from "@/lib/api/studio";

const picture: MediaItem = libraryItem({ id: "img-1", type: "IMAGE", mimeType: "image/png", url: "/api/v1/media/img-1?e=1&s=a" });
const reading: MediaItem = libraryItem({
  id: "aud-1",
  type: "AUDIO",
  mimeType: "audio/mpeg",
  url: "/api/v1/media/aud-1?e=1&s=b",
});
/** What the server returns for an upload: a library item (X13). */
function libraryItem(ref: Pick<MediaItem, "id" | "type" | "mimeType" | "url">): MediaItem {
  return {
    ...ref,
    sizeBytes: 1024,
    width: null,
    height: null,
    title: null,
    description: null,
    archived: false,
    createdAt: null,
    usedBy: 0,
    editable: true,
  };
}

const EMPTY = { prompt: "", correctAnswer: null, difficultyLevel: null };

const fileInputs = (container: HTMLElement) => [...container.querySelectorAll<HTMLInputElement>('input[type="file"]')];

describe("QuestionDraftForm (ORDERING)", () => {
  it("lists the items in their right order, which the author can rearrange, and saves them in that order", async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      <QuestionDraftForm questionType="ORDERING" initial={EMPTY} submitLabel="Create draft" busy={false} onSubmit={onSubmit} />,
    );

    await user.type(screen.getByLabelText("Question the child sees"), "Build the word");
    await user.type(screen.getByLabelText("Item 1"), "b");
    await user.type(screen.getByLabelText("Item 2"), "k");
    await user.type(screen.getByLabelText("Item 3"), "o");
    expect(screen.getByRole("button", { name: "Remove item 1" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Add an item" }));
    await user.type(screen.getByLabelText("Item 4"), "o");
    // "k" belongs last.
    await user.click(screen.getByRole("button", { name: "Move item 2 down" }));
    await user.click(screen.getByRole("button", { name: "Move item 3 down" }));
    expect(screen.getByRole("button", { name: "Move item 1 up" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Create draft" }));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        prompt: "Build the word",
        correctAnswer: null,
        items: [{ label: "b" }, { label: "o" }, { label: "o" }, { label: "k" }],
        options: undefined,
        pairs: undefined,
      }),
    );
  });
});

describe("MediaSlot in the question form", () => {
  it("uploads a picture and a reading for the question, asks for the picture's description, and saves their ids", async () => {
    const upload = vi.spyOn(studioApi, "uploadMedia").mockResolvedValueOnce(picture).mockResolvedValueOnce(reading);
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    const { container } = render(
      <QuestionDraftForm questionType="NUMERIC" initial={EMPTY} submitLabel="Create draft" busy={false} onSubmit={onSubmit} />,
    );
    await user.type(screen.getByLabelText("Question the child sees"), "How many apples?");
    await user.type(screen.getByLabelText("Correct answer (a number)"), "3");
    expect(screen.queryByLabelText(/Describe the picture/)).not.toBeInTheDocument();

    const [imageInput, audioInput] = fileInputs(container);
    const png = new File([new Uint8Array([137, 80, 78, 71])], "apples.png", { type: "image/png" });
    await user.upload(imageInput, png);
    expect(upload).toHaveBeenCalledWith(png);
    expect(await screen.findByRole("button", { name: "Replace the picture for the question" })).toBeInTheDocument();
    await user.type(screen.getByLabelText(/Describe the picture/), "Three red apples");
    await user.upload(audioInput, new File([new Uint8Array([73, 68, 51])], "q.mp3", { type: "audio/mpeg" }));
    expect(await screen.findByRole("button", { name: "Play the reading of the question" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Create draft" }));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        promptImageAssetId: "img-1",
        promptImageDescription: "Three red apples",
        promptAudioAssetId: "aud-1",
        correctAnswer: "3",
      }),
    );

    // Removed: nothing is sent for it, and its description goes with it.
    await user.click(screen.getByRole("button", { name: "Remove the picture for the question" }));
    await user.click(screen.getByRole("button", { name: "Create draft" }));
    expect(onSubmit).toHaveBeenLastCalledWith(
      expect.objectContaining({ promptImageAssetId: undefined, promptImageDescription: undefined, promptAudioAssetId: "aud-1" }),
    );
  });

  it("says why the server refused a file, and refuses a recording where a picture goes", async () => {
    vi.spyOn(studioApi, "uploadMedia")
      .mockRejectedValueOnce(
        new ApiError(422, {
          title: "Media Not Accepted",
          status: 422,
          detail: "A picture can be at most 4096 pixels on a side (this one is 5000 × 20).",
        }),
      )
      .mockResolvedValueOnce(reading);
    const user = userEvent.setup();
    const { container } = render(
      <QuestionDraftForm
        questionType="MULTIPLE_CHOICE"
        initial={EMPTY}
        submitLabel="Create draft"
        busy={false}
        onSubmit={vi.fn()}
      />,
    );
    const choicePicture = fileInputs(container)[2];
    await user.upload(choicePicture, new File([new Uint8Array([1])], "wide.png", { type: "image/png" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("at most 4096 pixels");
    await user.upload(choicePicture, new File([new Uint8Array([1])], "sound.png", { type: "image/png" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("That file is a recording, not a picture.");
    expect(screen.getByRole("button", { name: "Add a picture for choice 1" })).toBeInTheDocument();
  });
});
