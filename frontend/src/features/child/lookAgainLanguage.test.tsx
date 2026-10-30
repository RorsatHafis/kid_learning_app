/**
 * "Show me again" during the questions (LOCALIZATION.md, V84): the introduction is marked with its language when it
 * is known - Khmer, or English written in the Studio - and left unmarked when unknown, never guessed. The dialog's own
 * words stay in the interface's language.
 */
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LookAgain } from "./Learn";

const INTRO = { text: "Ducks swim in a row.", image: null, imageDescription: null, audio: null };

async function markOf(lang?: string) {
  const user = userEvent.setup();
  const { unmount } = render(<LookAgain introduction={INTRO} lang={lang} />);
  await user.click(screen.getByRole("button", { name: "Show me again" }));
  const dialog = screen.getByRole("dialog");
  const mark = screen.getByText(INTRO.text).closest("[lang]");
  const result = {
    // The nearest mark inside the dialog (the page's own <html lang> is outside it).
    content: mark && dialog.contains(mark) ? mark.getAttribute("lang") : null,
    dialogMarked: dialog.hasAttribute("lang"),
  };
  unmount();
  return result;
}

describe("the introduction shown again", () => {
  it("is marked Khmer or English when known, and unmarked when unknown", async () => {
    expect(await markOf("km")).toEqual({ content: "km", dialogMarked: false });
    expect(await markOf("en")).toEqual({ content: "en", dialogMarked: false });
    expect(await markOf(undefined)).toEqual({ content: null, dialogMarked: false });
  });
});
