/**
 * Ordering questions played by tapping and marked place by place, and questions with pictures and recordings read
 * aloud.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QuestionView } from "./QuestionView";
import { setMuted } from "@/features/buddy/sound";
import type { ActivityItem, MediaRef } from "@/lib/api/types";

const media = (id: string, type: "IMAGE" | "AUDIO"): MediaRef => ({
  id,
  type,
  mimeType: type === "IMAGE" ? "image/png" : "audio/mpeg",
  url: `/api/v1/media/${id}?e=1&s=sig`,
});

// What the server sends for "book": the items mixed up (never already in order), no answer.
const BOOK: ActivityItem = {
  activityItemId: "i1",
  sequenceOrder: 0,
  questionVersionId: "qv-book",
  questionType: "ORDERING",
  prompt: "Build the word",
  options: [
    { id: "o1", label: "o", displayOrder: 0 },
    { id: "k", label: "k", displayOrder: 1 },
    { id: "b", label: "b", displayOrder: 2 },
    { id: "o2", label: "o", displayOrder: 3 },
  ],
};

const yourOrder = () => screen.getByRole("list", { name: "Your order" });
const pool = () => screen.getByRole("list", { name: "Put these in order" });

class FakeAudio {
  static played: string[] = [];
  onended: (() => void) | null = null;
  onerror: (() => void) | null = null;
  constructor(public src: string) {}
  play() {
    FakeAudio.played.push(this.src);
    return Promise.resolve();
  }
  pause() {}
}

afterEach(() => {
  vi.unstubAllGlobals();
  FakeAudio.played = [];
  setMuted(false);
});

describe("OrderingQuestion", () => {
  it("places items by tapping them in order, takes one back on a second tap, and sends the child's order", async () => {
    const onAnswer = vi.fn();
    const user = userEvent.setup();
    render(<QuestionView item={BOOK} onAnswer={onAnswer} disabled={false} result={null} />);

    const check = screen.getByRole("button", { name: "Check my order" });
    expect(within(pool()).getAllByRole("button")).toHaveLength(4);
    expect(check).toBeDisabled();

    await user.click(within(pool()).getByRole("button", { name: "b: place it next" }));
    await user.click(within(pool()).getByRole("button", { name: "k: place it next" }));
    expect(screen.getByText("k is 2nd.")).toBeInTheDocument();
    // A mistake is undone by tapping it in the order.
    await user.click(within(yourOrder()).getByRole("button", { name: "2nd: k. Tap to take it back" }));
    expect(within(yourOrder()).getByRole("img", { name: "2nd: empty" })).toBeInTheDocument();

    const [firstO, secondO] = within(pool()).getAllByRole("button", { name: "o: place it next" });
    await user.click(secondO);
    await user.click(firstO);
    await user.click(within(pool()).getByRole("button", { name: "k: place it next" }));
    expect(screen.getByText("k is 4th. All placed: check your order.")).toBeInTheDocument();

    await user.click(check);
    expect(onAnswer).toHaveBeenCalledWith(JSON.stringify(["b", "o2", "o1", "k"]));
  });

  it("shows which places are right after answering, the two o's either way round", () => {
    render(
      <QuestionView
        item={BOOK}
        onAnswer={vi.fn()}
        disabled={false}
        result={{
          submitted: JSON.stringify(["b", "o2", "k", "o1"]),
          correct: false,
          correctAnswer: { optionIds: ["b", "o1", "o2", "k"], text: "b → o → o → k" },
        }}
      />,
    );
    const placed = within(yourOrder()).getAllByRole("button");
    expect(placed.map((b) => b.getAttribute("aria-label"))).toEqual([
      "1st: b, right",
      "2nd: o, right",
      "3rd: k, should be o",
      "4th: o, should be k",
    ]);
    // Y40: the right order is laid out as tiles too, for a child who can't read "should be".
    expect(screen.getByText("The right order").nextElementSibling?.textContent).toBe("book");
    expect(screen.queryByRole("list", { name: "Put these in order" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Check my order" })).not.toBeInTheDocument();
  });
});

describe("Questions with pictures and recordings", () => {
  const PICTURES: ActivityItem = {
    activityItemId: "i2",
    sequenceOrder: 0,
    questionVersionId: "qv-cat",
    questionType: "MULTIPLE_CHOICE",
    prompt: "Which one is the cat?",
    promptImage: media("garden", "IMAGE"),
    promptImageDescription: "A garden with three animals",
    promptAudio: media("reading", "AUDIO"),
    options: [
      { id: "cat", label: "A cat", displayOrder: 0, image: media("cat", "IMAGE"), audio: media("cat-word", "AUDIO") },
      { id: "dog", label: "A dog", displayOrder: 1, image: media("dog", "IMAGE") },
    ],
  };

  it("shows the picture with its description, reads the question aloud as it appears, and lets each word be heard", async () => {
    vi.stubGlobal("Audio", FakeAudio);
    const onAnswer = vi.fn();
    const user = userEvent.setup();
    render(<QuestionView item={PICTURES} onAnswer={onAnswer} disabled={false} result={null} />);

    expect(screen.getByRole("img", { name: "A garden with three animals" })).toHaveAttribute("src", PICTURES.promptImage!.url);
    expect(FakeAudio.played).toEqual([PICTURES.promptAudio!.url]);

    // A picture choice is named by its words, shows only its picture, and is answered by its id.
    const cat = screen.getByRole("button", { name: "A cat" });
    expect(within(cat).getByRole("presentation", { hidden: true })).toHaveAttribute("src", PICTURES.options[0].image!.url);
    await user.click(screen.getByRole("button", { name: "Hear A cat" }));
    expect(FakeAudio.played).toContain(PICTURES.options[0].audio!.url);
    expect(screen.queryByRole("button", { name: "Hear A dog" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Listen to the question" }));
    expect(FakeAudio.played.filter((url) => url === PICTURES.promptAudio!.url)).toHaveLength(2);

    await user.click(cat);
    expect(onAnswer).toHaveBeenCalledWith("cat");
  });

  it("doesn't read aloud by itself when sound is muted, or in the Studio's preview, but Listen still plays", async () => {
    vi.stubGlobal("Audio", FakeAudio);
    setMuted(true);
    const user = userEvent.setup();
    const { unmount } = render(<QuestionView item={PICTURES} onAnswer={vi.fn()} disabled={false} result={null} />);
    expect(FakeAudio.played).toEqual([]);
    await user.click(screen.getByRole("button", { name: "Listen to the question" }));
    expect(FakeAudio.played).toEqual([PICTURES.promptAudio!.url]);
    unmount();

    setMuted(false);
    FakeAudio.played = [];
    render(<QuestionView item={PICTURES} onAnswer={vi.fn()} disabled={false} result={null} headingLevel={3} autoFocus={false} />);
    expect(FakeAudio.played).toEqual([]);
  });
});
