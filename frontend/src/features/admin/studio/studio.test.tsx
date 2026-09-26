import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { ApiError } from "@/lib/api/ApiError";
import type { Catalog, QuestionDetail } from "@/lib/api/studio";
import { codeFrom, currentStatus, reportFromError } from "./studioLogic";
import { ValidationPanel } from "./shared";
import { QuestionDraftForm } from "./QuestionDraftForm";

const api = vi.hoisted(() => ({
  question: vi.fn(),
  previewQuestionVersion: vi.fn(),
  validateQuestionVersion: vi.fn(),
  publishQuestionVersion: vi.fn(),
  reviseQuestionDraft: vi.fn(),
  openQuestionDraft: vi.fn(),
  tagQuestionSkill: vi.fn(),
  untagQuestionSkill: vi.fn(),
}));
vi.mock("@/lib/api/studio", () => ({ studioApi: api }));

// Imported after the mock so the editor talks to it.
const { QuestionEditor } = await import("./QuestionEditor");

describe("studio helpers", () => {
  it("suggests catalog codes from names", () => {
    expect(codeFrom("Add within 10")).toBe("ADD_WITHIN_10");
    expect(codeFrom("  Counting — to 5! ")).toBe("COUNTING_TO_5");
  });

  it("summarises the live version and an open draft together", () => {
    expect(
      currentStatus([
        { id: "1", versionNumber: 1, status: "RETIRED", publishedAt: null },
        { id: "2", versionNumber: 2, status: "PUBLISHED", publishedAt: null },
        { id: "3", versionNumber: 3, status: "DRAFT", publishedAt: null },
      ]),
    ).toEqual({ label: "v2 live · v3 draft", status: "DRAFT" });
    expect(currentStatus([]).status).toBeNull();
  });

  it("reads the validation report from a refused publish only", () => {
    const report = { errors: [{ code: "NO_QUESTIONS", message: "Add at least one question." }], warnings: [] };
    expect(reportFromError(new ApiError(409, { title: "", status: 409, detail: "", validation: report } as never))).toEqual(report);
    expect(reportFromError(new ApiError(409, { title: "", status: 409, detail: "busy" }))).toBeNull();
    expect(reportFromError(new ApiError(403, null))).toBeNull();
  });
});

describe("ValidationPanel", () => {
  it("lists blocking issues and warnings separately", () => {
    render(
      <ValidationPanel
        report={{
          errors: [{ code: "QUESTION_NO_SKILL", message: "Tag at least one skill." }],
          warnings: [{ code: "NO_DIFFICULTY", message: "No difficulty is set." }],
        }}
      />,
    );
    expect(screen.getByText(/1 issue to fix before publishing/)).toBeInTheDocument();
    expect(screen.getByText("Tag at least one skill.")).toBeInTheDocument();
    expect(within(screen.getByRole("list", { name: "Warnings" })).getByText("No difficulty is set.")).toBeInTheDocument();
  });

  it("says when a draft is ready", () => {
    render(<ValidationPanel report={{ errors: [], warnings: [] }} />);
    expect(screen.getByText("Ready to publish")).toBeInTheDocument();
  });
});

describe("QuestionDraftForm", () => {
  it("sends multiple-choice answers as choices with exactly the one marked correct", async () => {
    const onSubmit = vi.fn();
    render(
      <QuestionDraftForm
        questionType="MULTIPLE_CHOICE"
        initial={{ prompt: "", correctAnswer: null, difficultyLevel: null }}
        submitLabel="Create draft"
        busy={false}
        onSubmit={onSubmit}
      />,
    );
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Question the child sees"), "How many?");
    await user.type(screen.getByLabelText("Choice 1"), "2");
    // A blank choice blocks saving.
    expect(screen.getByRole("button", { name: "Create draft" })).toBeDisabled();
    await user.type(screen.getByLabelText("Choice 2"), "3");
    await user.click(screen.getByRole("button", { name: /Add a choice/ }));
    await user.type(screen.getByLabelText("Choice 3"), "4");
    await user.click(screen.getByLabelText("Choice 2 is the correct answer"));
    await user.selectOptions(screen.getByLabelText("Difficulty (1 easiest – 10 hardest)"), "3");
    await user.click(screen.getByRole("button", { name: "Create draft" }));

    expect(onSubmit).toHaveBeenCalledWith({
      prompt: "How many?",
      correctAnswer: null,
      difficultyLevel: 3,
      options: [
        { label: "2", correct: false },
        { label: "3", correct: true },
        { label: "4", correct: false },
      ],
    });
  });

  it("offers only true or false as the answer to a true/false question", async () => {
    const onSubmit = vi.fn();
    render(
      <QuestionDraftForm
        questionType="TRUE_FALSE"
        initial={{ prompt: "The sky is green", correctAnswer: null, difficultyLevel: null }}
        submitLabel="Save"
        busy={false}
        onSubmit={onSubmit}
      />,
    );
    const user = userEvent.setup();
    await user.selectOptions(screen.getByLabelText("Correct answer"), "false");
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(onSubmit).toHaveBeenCalledWith({ prompt: "The sky is green", correctAnswer: "false", difficultyLevel: null, options: undefined });
  });
});

const catalog: Catalog = {
  subject: { id: "subject", code: "MATHS", name: "Maths", description: null },
  skills: [{ id: "skill", code: "COUNT", name: "Count to 5", description: null, status: "ACTIVE", prerequisiteSkillIds: [] }],
  objectives: [],
};

function draftQuestion(skillIds: string[]): QuestionDetail {
  return {
    id: "q",
    subjectId: "subject",
    questionType: "NUMERIC",
    skillIds,
    versions: [
      { id: "qv1", versionNumber: 1, status: "DRAFT", publishedAt: null, prompt: "2 + 2 = ?", correctAnswer: "4", difficultyLevel: 1, options: [], pairs: [] },
    ],
  };
}

function renderEditor() {
  return render(
    <MemoryRouter initialEntries={["/questions/q"]}>
      <Routes>
        <Route element={<Outlet context={{ catalog, refreshCatalog: () => undefined }} />}>
          <Route path="/questions/:questionId" element={<QuestionEditor />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("QuestionEditor", () => {
  beforeEach(() => {
    api.previewQuestionVersion.mockResolvedValue({
      activityItemId: "qv1",
      sequenceOrder: 0,
      questionVersionId: "qv1",
      questionType: "NUMERIC",
      prompt: "2 + 2 = ?",
      options: [],
    });
  });

  it("keeps Publish unavailable while the server reports blocking issues", async () => {
    api.question.mockResolvedValue(draftQuestion([]));
    api.validateQuestionVersion.mockResolvedValue({
      errors: [{ code: "QUESTION_NO_SKILL", message: "Tag at least one skill." }],
      warnings: [],
    });
    renderEditor();

    expect(await screen.findByText("Tag at least one skill.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Publish" })).toBeDisabled();
    // The child's view renders from the learner-safe preview.
    expect(screen.getByText("Child's view")).toBeInTheDocument();
  });

  it("shows the server's report when publishing is refused, and the new state when it succeeds", async () => {
    api.question.mockResolvedValue(draftQuestion(["skill"]));
    api.validateQuestionVersion.mockResolvedValue({ errors: [], warnings: [] });
    api.publishQuestionVersion.mockRejectedValueOnce(
      new ApiError(409, {
        title: "Not Ready To Publish",
        status: 409,
        detail: "",
        validation: { errors: [{ code: "SKILL_ARCHIVED", message: "\"Count to 5\" is archived." }], warnings: [] },
      } as never),
    );
    renderEditor();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Publish" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Not published");
    expect(screen.getByText("\"Count to 5\" is archived.")).toBeInTheDocument();

    // The author fixes the cause elsewhere and checks again: Publish becomes available.
    api.validateQuestionVersion.mockResolvedValue({ errors: [], warnings: [] });
    expect(screen.getByRole("button", { name: "Publish" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Check again" }));
    expect(await screen.findByText("Ready to publish")).toBeInTheDocument();
    const published = draftQuestion(["skill"]);
    published.versions[0] = { ...published.versions[0], status: "PUBLISHED", publishedAt: "2026-09-26T00:00:00Z" };
    api.publishQuestionVersion.mockResolvedValueOnce(published);
    await user.click(await screen.findByRole("button", { name: "Publish" }));

    expect(await screen.findByRole("button", { name: /Start a new version/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Publish" })).not.toBeInTheDocument();
  });
});
