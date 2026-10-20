/**
 * The activity builder (Y42): questions listed with what is new, checks run together, everything published in one
 * confirmed step, new questions written in place, and publishing left to review where review is required.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import type { ActivityDetail, Catalog } from "@/lib/api/studio";

const api = vi.hoisted(() => ({
  activity: vi.fn(),
  previewActivityVersion: vi.fn(),
  validateActivityVersion: vi.fn(),
  validateQuestionVersion: vi.fn(),
  addNewQuestion: vi.fn(),
  publishActivityWithQuestions: vi.fn(),
  publishActivityVersion: vi.fn(),
  replaceActivityItems: vi.fn(),
  questions: vi.fn(),
  question: vi.fn(),
  untagActivitySkill: vi.fn(),
  tagActivitySkill: vi.fn(),
  reviseActivityDraft: vi.fn(),
}));
const reviewApi = vi.hoisted(() => ({
  settings: vi.fn(async () => ({ required: false })),
  state: vi.fn(async () => ({ required: false, review: null })),
}));
vi.mock("@/lib/api/studio", () => ({ studioApi: api, reviewApi }));
const { ActivityEditor } = await import("./ActivityEditor");

const catalog = {
  subject: { id: "subject", code: "MATHS", name: "Maths", description: null },
  skills: [{ id: "s1", code: "ADD", name: "Add within 10", description: null, status: "ACTIVE", prerequisiteSkillIds: [] }],
  objectives: [],
} as unknown as Catalog;

function activity(items: { id: string; prompt: string; status: "DRAFT" | "PUBLISHED" }[]): ActivityDetail {
  return {
    id: "a1",
    title: "Count the stars",
    activityType: "PRACTICE",
    lessonId: "l1",
    subjectId: "subject",
    skillIds: ["s1"],
    archived: false,
    versions: [
      {
        id: "av1",
        versionNumber: 1,
        status: "DRAFT",
        publishedAt: null,
        instructions: null,
        difficultyLevel: null,
        estimatedDurationSeconds: null,
        skillIds: ["s1"],
        items: items.map((i) => ({
          questionVersionId: i.id,
          questionId: `q-${i.id}`,
          questionType: "NUMERIC",
          prompt: i.prompt,
          questionVersionNumber: 1,
          questionVersionStatus: i.status,
          points: 1,
          questionSkillIds: ["s1"],
        })),
      },
    ],
  } as ActivityDetail;
}

const OK = { errors: [], warnings: [] };

function renderBuilder() {
  render(
    <MemoryRouter initialEntries={["/studio/subject/activities/a1"]}>
      <Routes>
        <Route element={<Outlet context={{ catalog, refreshCatalog: () => undefined }} />}>
          <Route path="/studio/:subjectId/activities/:activityId" element={<ActivityEditor />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  reviewApi.settings.mockResolvedValue({ required: false });
  api.previewActivityVersion.mockResolvedValue({ items: [] });
  api.questions.mockResolvedValue({ items: [], total: 0, page: 0, size: 20 });
});

describe("the activity builder (Y42)", () => {
  it("lists the questions with what is new, checks everything together, and publishes it all in one confirmed step", async () => {
    const draft = activity([
      { id: "qv1", prompt: "2 + 2 = ?", status: "DRAFT" },
      { id: "qv2", prompt: "1 + 1 = ?", status: "PUBLISHED" },
    ]);
    api.activity.mockResolvedValue(draft);
    // The activity's own "question 1 is still a draft" isn't listed: publishing here publishes it.
    api.validateActivityVersion.mockResolvedValue({
      errors: [{ code: "QUESTION_NOT_PUBLISHED", message: "", values: { question: 1, state: "DRAFT" } }],
      warnings: [],
    });
    api.validateQuestionVersion.mockResolvedValue(OK);
    api.publishActivityWithQuestions.mockResolvedValue({ ...draft, versions: [{ ...draft.versions[0], status: "PUBLISHED" }] });
    const user = userEvent.setup();
    renderBuilder();

    const list = await screen.findByRole("list", { name: "Write the questions" });
    expect(within(list).getByText("2 + 2 = ?")).toBeInTheDocument();
    expect(screen.getByText("New")).toBeInTheDocument();
    expect(screen.getByText("Published")).toBeInTheDocument();
    expect(await screen.findByText("Everything is ready.")).toBeInTheDocument();
    expect(api.validateQuestionVersion).toHaveBeenCalledWith("qv1");
    expect(api.validateQuestionVersion).not.toHaveBeenCalledWith("qv2");

    await user.click(screen.getByRole("button", { name: "Publish activity and 1 new question" }));
    expect(screen.getByText(/Published questions and activities don't change/)).toBeInTheDocument();
    expect(api.publishActivityWithQuestions).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Yes, publish" }));
    expect(api.publishActivityWithQuestions).toHaveBeenCalledWith("av1");
  });

  it("says what to fix, and where, before anything can be published", async () => {
    api.activity.mockResolvedValue(activity([{ id: "qv1", prompt: "5 + 5 = ?", status: "DRAFT" }]));
    api.validateActivityVersion.mockResolvedValue(OK);
    api.validateQuestionVersion.mockResolvedValue({
      errors: [{ code: "HINT_GIVES_ANSWER", message: "", values: { answer: "10" } }],
      warnings: [],
    });
    renderBuilder();
    expect(await screen.findByText("1 thing to fix first")).toBeInTheDocument();
    expect(screen.getByText("Question 1")).toBeInTheDocument();
    expect(screen.getByText(/The hint says the answer \("10"\)/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Publish activity/ })).toBeDisabled();
  });

  it("writes a new question in place: choose what children do, write it, add it", async () => {
    const empty = activity([]);
    api.activity.mockResolvedValue(empty);
    api.validateActivityVersion.mockResolvedValue({ errors: [{ code: "NO_QUESTIONS", message: "", values: {} }], warnings: [] });
    api.addNewQuestion.mockResolvedValue(activity([{ id: "qv9", prompt: "4 + 3 = ?", status: "DRAFT" }]));
    const user = userEvent.setup();
    renderBuilder();

    await user.click(await screen.findByRole("button", { name: "Add the first question" }));
    expect(screen.getByRole("button", { name: /Match pairs/ })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Type a number/ }));
    await user.type(screen.getByLabelText("Question the child sees"), "4 + 3 = ?");
    await user.type(screen.getByLabelText("Correct answer (a number)"), "7");
    await user.click(screen.getByRole("button", { name: "Add to the activity" }));
    expect(api.addNewQuestion).toHaveBeenCalledWith(
      "av1",
      expect.objectContaining({ subjectId: "subject", questionType: "NUMERIC", prompt: "4 + 3 = ?", correctAnswer: "7" }),
    );
    expect(await screen.findByText("Question added.")).toBeInTheDocument();
    expect(screen.getByText("4 + 3 = ?")).toBeInTheDocument();
  });

  it("leaves publishing to the review flow where review is required", async () => {
    reviewApi.settings.mockResolvedValue({ required: true });
    api.activity.mockResolvedValue(activity([{ id: "qv1", prompt: "2 + 2 = ?", status: "DRAFT" }]));
    api.validateActivityVersion.mockResolvedValue(OK);
    api.validateQuestionVersion.mockResolvedValue(OK);
    renderBuilder();
    expect(await screen.findByText(/Content needs a reviewer's approval here/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Publish activity and/ })).not.toBeInTheDocument();
  });
});
