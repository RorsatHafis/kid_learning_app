import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { ApiError } from "@/lib/api/ApiError";
import type { Catalog, ImportedQuestion, ValidationReport } from "@/lib/api/studio";

const api = vi.hoisted(() => ({
  importQuestions: vi.fn(),
  importIntoActivity: vi.fn(),
  publishQuestionVersion: vi.fn(),
  activity: vi.fn(),
}));
vi.mock("@/lib/api/studio", () => ({ studioApi: api }));

const { QuestionImport } = await import("./QuestionImport");

const catalog: Catalog = {
  subject: { id: "subject", code: "MATHS", name: "Maths", description: null },
  skills: [{ id: "skill", code: "COUNT", name: "Count to 5", description: null, status: "ACTIVE", prerequisiteSkillIds: [] }],
  objectives: [],
};

const ready: ValidationReport = { errors: [], warnings: [] };
function imported(id: string, prompt: string, validation: ValidationReport = ready): ImportedQuestion {
  return { questionId: id, versionId: `${id}-v1`, prompt, validation };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/questions/import"]}>
      <Routes>
        <Route element={<Outlet context={{ catalog, refreshCatalog: () => undefined }} />}>
          <Route path="/questions/import" element={<QuestionImport />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

const rows = ["type\tquestion\tanswer", "number\t1 + 1 = ?\t2", "number\t2 + 2 = ?\t4", "short answer\tSpell cat.\t"].join("\n");

describe("QuestionImport", () => {
  it("adds the pasted rows as drafts with their skills, then publishes the ready ones, each on its own", async () => {
    api.importQuestions.mockResolvedValue([
      imported("a", "1 + 1 = ?"),
      imported("b", "2 + 2 = ?"),
      imported("c", "Spell cat.", { errors: [{ code: "NO_ANSWER", message: "The question needs an answer.", values: {} }], warnings: [] }),
    ]);
    api.publishQuestionVersion.mockImplementation(async (versionId: string) => {
      if (versionId === "b-v1") throw new ApiError(409, { detail: "Platform content needs a second administrator's review before it is published: ask for a review" } as never);
      return {};
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("checkbox", { name: "Count to 5" }));
    fireEvent.change(screen.getByLabelText("Rows copied from your spreadsheet, the header row first"), { target: { value: rows } });
    await user.click(screen.getByRole("button", { name: "Add 3 questions as drafts" }));

    expect(await screen.findByText("3 questions were added as drafts.")).toBeInTheDocument();
    const sent = api.importQuestions.mock.calls[0][0];
    expect(sent).toHaveLength(3);
    expect(sent[0]).toMatchObject({ subjectId: "subject", questionType: "NUMERIC", prompt: "1 + 1 = ?", correctAnswer: "2", skillIds: ["skill"] });
    // Sent with the paste's key, so the same rows sent again can't be added twice.
    expect(api.importQuestions.mock.calls[0][1]).toMatch(/^[0-9a-f-]{36}$/);

    // Only the two whose checks pass are offered; the one to fix is left as a draft.
    await user.click(screen.getByRole("button", { name: "Publish the 2 that are ready" }));
    expect(api.publishQuestionVersion.mock.calls.map((c) => c[0])).toEqual(["a-v1", "b-v1"]);
    const list = screen.getByRole("list");
    expect(within(list).getByText("Published")).toBeInTheDocument();
    expect(within(list).getByRole("alert")).toHaveTextContent(/review/);
    // The refused one can be tried again; the published one isn't offered twice.
    expect(screen.getByRole("button", { name: "Publish the 1 that's ready" })).toBeInTheDocument();
  });

  it("names the sheet row when the server refuses the paste, and keeps nothing", async () => {
    api.importQuestions.mockRejectedValue(new ApiError(400, { detail: "Validation failed", errors: { "questions[1].prompt": "size must be between 0 and 2000" } } as never));
    const user = userEvent.setup();
    renderPage();
    fireEvent.change(screen.getByLabelText("Rows copied from your spreadsheet, the header row first"), { target: { value: rows } });
    await user.click(screen.getByRole("button", { name: "Add 3 questions as drafts" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Row 3: size must be between 0 and 2000");
    // Tried again unchanged: the same key (one paste); changed rows would be another.
    await user.click(screen.getByRole("button", { name: "Add 3 questions as drafts" }));
    expect(api.importQuestions.mock.calls.at(-1)![1]).toBe(api.importQuestions.mock.calls.at(-2)![1]);
    const more = [rows, ["number", "3 + 3 = ?", "6"].join("\t")].join("\n");
    fireEvent.change(screen.getByLabelText("Rows copied from your spreadsheet, the header row first"), { target: { value: more } });
    await user.click(screen.getByRole("button", { name: "Add 4 questions as drafts" }));
    expect(api.importQuestions.mock.calls.at(-1)![1]).not.toBe(api.importQuestions.mock.calls.at(-2)![1]);
    expect(screen.queryByText(/were added as drafts/)).not.toBeInTheDocument();
  });
});
