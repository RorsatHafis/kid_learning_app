/**
 * V83 (LOCALIZATION.md, screen-reader language): a question is marked with its language only when that is known -
 * given in Khmer, or written in the Studio's English fields - and older content of unknown language is left unmarked,
 * never guessed.
 */
import { describe, expect, it, vi } from "vitest";
import { render } from "@testing-library/react";
import { QuestionView } from "./QuestionView";
import type { ActivityItem } from "@/lib/api/types";

const ITEM: ActivityItem = {
  activityItemId: "i1",
  sequenceOrder: 0,
  questionVersionId: "q1",
  questionType: "NUMERIC",
  prompt: "2 + 2 = ?",
  options: [],
};

function markOf(item: ActivityItem) {
  const { container, unmount } = render(<QuestionView item={item} onAnswer={vi.fn()} disabled={false} result={null} />);
  // The question's own mark, not the page's (<html lang> follows the interface language).
  const mark = container.firstElementChild?.getAttribute("lang") ?? null;
  unmount();
  return mark;
}

describe("the language a question is read in", () => {
  it("is Khmer when given in Khmer, English when written in English, and unmarked when unknown", () => {
    expect(markOf({ ...ITEM, prompt: "២ + ២ = ?", language: "km", writtenIn: null })).toBe("km");
    expect(markOf({ ...ITEM, language: null, writtenIn: "en" })).toBe("en");
    expect(markOf({ ...ITEM, language: null, writtenIn: null })).toBeNull();
  });
});
