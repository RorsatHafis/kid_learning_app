import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import type { Catalog } from "@/lib/api/studio";

const api = vi.hoisted(() => ({ questions: vi.fn() }));
vi.mock("@/lib/api/studio", () => ({ studioApi: api }));

const { QuestionsPage } = await import("./QuestionsPage");

const catalog: Catalog = {
  subject: { id: "subject", code: "MATHS", name: "Maths", description: null },
  skills: [{ id: "skill", code: "COUNT", name: "Count to 5", description: null, status: "ACTIVE", prerequisiteSkillIds: [] }],
  objectives: [],
};

describe("question bank: still needs", () => {
  it("asks the server for the questions lacking what the author picks, and keeps it in the address", async () => {
    api.questions.mockResolvedValue({ items: [], page: 0, size: 25, total: 0 });
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/questions?needs=HINT"]}>
        <Routes>
          <Route element={<Outlet context={{ catalog, refreshCatalog: () => undefined }} />}>
            <Route path="/questions" element={<QuestionsPage />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    const select = await screen.findByLabelText("Still needs");
    expect(select).toHaveValue("HINT");
    expect(api.questions).toHaveBeenLastCalledWith("subject", expect.objectContaining({ needs: "HINT" }));

    await user.selectOptions(select, "Khmer");
    expect(api.questions).toHaveBeenLastCalledWith("subject", expect.objectContaining({ needs: "KHMER" }));
    // A filtered empty bank is a search with no match, not a first-run bank: no composer opens.
    expect(screen.queryByRole("textbox", { name: /question/i })).toBeNull();
  });

  it("says so when nothing still needs it - good news, not 'no match'", async () => {
    api.questions.mockResolvedValue({ items: [], page: 0, size: 25, total: 0 });
    render(
      <MemoryRouter initialEntries={["/questions?needs=KHMER"]}>
        <Routes>
          <Route element={<Outlet context={{ catalog, refreshCatalog: () => undefined }} />}>
            <Route path="/questions" element={<QuestionsPage />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText("Every question here is complete in Khmer.")).toBeInTheDocument();
    expect(screen.queryByText("No question matches these filters.")).toBeNull();
  });
});
