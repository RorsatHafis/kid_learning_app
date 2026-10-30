/**
 * Unsaved Studio work is not lost by following a link inside the app: the link asks first ("Stay" keeps the page
 * exactly as it is), and once the work is saved - or there is nothing unsaved - links simply go.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, MemoryRouter, Route, Routes } from "react-router-dom";
import { DetailsForm } from "./ActivityEditor";
import type { ActivityVersionDetail } from "@/lib/api/studio";

const version = {
  id: "v1",
  versionNumber: 1,
  status: "DRAFT",
  publishedAt: null,
  instructions: "Count the birds.",
  difficultyLevel: 1,
  estimatedDurationSeconds: null,
  items: [],
  skillIds: [],
  introduction: null,
  khmer: { title: null, instructions: null, introduction: null, introductionImageDescription: null, complete: false },
} as unknown as ActivityVersionDetail;

function renderEditor() {
  return render(
    <MemoryRouter initialEntries={["/editor"]}>
      <Routes>
        <Route
          path="/editor"
          element={
            <>
              <Link to="/elsewhere">All activities</Link>
              <DetailsForm title="Count the birds" version={version} busy={false} onSave={vi.fn()} />
            </>
          }
        />
        <Route path="/elsewhere" element={<p>the other page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => vi.restoreAllMocks());

describe("a link away from unsaved work", () => {
  it("asks first, and staying keeps the page and what was typed", async () => {
    const ask = vi.spyOn(window, "confirm").mockReturnValue(false);
    const user = userEvent.setup();
    renderEditor();
    const field = screen.getByLabelText("Instructions the child hears before starting");
    await user.type(field, " Then say the number.");

    await user.click(screen.getByRole("link", { name: "All activities" }));
    expect(ask).toHaveBeenCalledWith("Your changes aren't saved yet. Leave this page and lose them?");
    expect(screen.queryByText("the other page")).not.toBeInTheDocument();
    expect(field).toHaveValue("Count the birds. Then say the number.");

    ask.mockReturnValue(true);
    await user.click(screen.getByRole("link", { name: "All activities" }));
    expect(await screen.findByText("the other page")).toBeInTheDocument();
  });

  it("doesn't ask when nothing is unsaved", async () => {
    const ask = vi.spyOn(window, "confirm");
    const user = userEvent.setup();
    renderEditor();
    await user.click(screen.getByRole("link", { name: "All activities" }));
    expect(ask).not.toHaveBeenCalled();
    expect(await screen.findByText("the other page")).toBeInTheDocument();
  });
});
