import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import type { Catalog, CurriculumDetail, JourneyChapter, JourneyMap } from "@/lib/api/studio";
import { chapterChanges, durationText, issueAction } from "./journeyLogic";

const api = vi.hoisted(() => ({
  curriculum: vi.fn(),
  journey: vi.fn(),
  previewJourney: vi.fn(),
  validateCurriculumVersion: vi.fn(),
  replaceCurriculumObjectives: vi.fn(),
  publishCurriculumVersion: vi.fn(),
  openCurriculumDraft: vi.fn(),
}));
// Y14: review switched off (the default) - the editors publish directly.
const reviewApi = vi.hoisted(() => ({ state: vi.fn(async () => ({ required: false, review: null })) }));
vi.mock("@/lib/api/studio", () => ({ studioApi: api, reviewApi }));

const { CurriculumEditor } = await import("./CurriculumEditor");

describe("journey helpers", () => {
  it("lists chapters added, removed and moved since the published version", () => {
    expect(chapterChanges(["a", "b", "c"], ["b", "a", "d"])).toEqual({
      added: ["d"],
      removed: ["c"],
      moved: ["b", "a"],
      any: true,
    });
    expect(chapterChanges(["a", "b"], ["a", "b"]).any).toBe(false);
    // Removing a chapter doesn't make the ones after it "moved".
    expect(chapterChanges(["a", "b", "c"], ["a", "c"]).moved).toEqual([]);
  });

  it("describes time only when steps have durations", () => {
    expect(durationText(0, 0, 0)).toBeNull();
    expect(durationText(0, 2, 2)).toBeNull();
    expect(durationText(360, 0, 3)).toBe("about 6 min");
    expect(durationText(20, 1, 2)).toBe("under a minute (+1 untimed)");
  });

  it("points each gap at the place it's fixed", () => {
    expect(issueAction({ code: "SKILL_NO_EVIDENCE", severity: "WARNING", message: "", skillId: "s1" })?.to).toBe(
      "../../questions?skill=s1",
    );
    expect(issueAction({ code: "CHAPTER_NO_SKILLS", severity: "WARNING", message: "", skillId: null })?.to).toBe("../../catalog");
    expect(issueAction({ code: "PREREQUISITE_LATER", severity: "WARNING", message: "", skillId: "s1" })).toBeNull();
  });
});

const catalog: Catalog = {
  subject: { id: "subject", code: "MATHS", name: "Maths", description: null },
  skills: [],
  objectives: [
    { id: "o1", code: "O1", title: "Counting", description: null, skillIds: ["s1"] },
    { id: "o2", code: "O2", title: "Adding", description: null, skillIds: ["s2"] },
  ],
};

const DETAIL: CurriculumDetail = {
  id: "c1",
  name: "Maths - Junior",
  subjectId: "subject",
  ageHubId: "hub",
  versions: [{ id: "v1", versionNumber: 1, status: "DRAFT", publishedAt: null, objectiveIds: ["o1", "o2"] }],
};

function chapter(position: number, objectiveId: string, title: string, over: Partial<JourneyChapter> = {}): JourneyChapter {
  return {
    position,
    objectiveId,
    code: objectiveId.toUpperCase(),
    title,
    description: null,
    otherSubject: false,
    estimatedSeconds: 120,
    stepsWithoutDuration: 0,
    steps: [
      {
        activityId: `a-${objectiveId}`,
        activityVersionId: `av-${objectiveId}`,
        title: `${title} practice`,
        versionNumber: 1,
        difficulty: 1,
        estimatedSeconds: 120,
        questions: 3,
        placedBySkill: "skill",
      },
    ],
    skills: [],
    issues: [],
    ...over,
  };
}

function map(chapters: JourneyChapter[], preview = false): JourneyMap {
  return {
    curriculumId: "c1",
    curriculumVersionId: "v1",
    versionNumber: 1,
    status: "DRAFT",
    preview,
    totals: {
      chapters: chapters.length,
      steps: chapters.length,
      skills: 2,
      estimatedSeconds: 240,
      stepsWithoutDuration: 0,
      warnings: 1,
      tips: 0,
    },
    chapters,
  };
}

function renderBuilder() {
  render(
    <MemoryRouter initialEntries={["/studio/subject/curricula/c1"]}>
      <Routes>
        <Route element={<Outlet context={{ catalog, refreshCatalog: () => undefined }} />}>
          <Route path="/studio/:subjectId/curricula/:curriculumId" element={<CurriculumEditor />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("CurriculumEditor (journey builder)", () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset());
    api.curriculum.mockResolvedValue(DETAIL);
    api.validateCurriculumVersion.mockResolvedValue({ errors: [], warnings: [] });
    api.journey.mockResolvedValue(
      map([
        chapter(1, "o1", "Counting"),
        chapter(2, "o2", "Adding", {
          issues: [{ code: "SKILL_NO_EVIDENCE", severity: "WARNING", message: "No question records evidence.", skillId: "s2" }],
        }),
      ]),
    );
  });

  it("shows the journey, opens chapters that need attention, and filters to them", async () => {
    renderBuilder();
    const chapters = await screen.findByRole("list", { name: "Chapters in order" });
    expect(await within(chapters).findByRole("button", { name: /Chapter 1: Counting/ })).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    expect(within(chapters).getByRole("button", { name: /Chapter 2: Adding/ })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("link", { name: "Questions for this skill" })).toHaveAttribute(
      "href",
      "/studio/subject/questions?skill=s2",
    );
    expect(screen.getByRole("button", { name: "Chapter 2, Adding: 1 step, 1 warning" })).toBeInTheDocument();

    await userEvent.setup().click(screen.getByRole("button", { name: "Needs attention" }));
    expect(within(chapters).queryByRole("button", { name: /Chapter 1: Counting/ })).not.toBeInTheDocument();
  });

  it("previews a new order before it is saved, then saves it", async () => {
    api.previewJourney.mockResolvedValue(map([chapter(1, "o2", "Adding"), chapter(2, "o1", "Counting")], true));
    api.replaceCurriculumObjectives.mockResolvedValue({
      ...DETAIL,
      versions: [{ ...DETAIL.versions[0], objectiveIds: ["o2", "o1"] }],
    });
    renderBuilder();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Move chapter 2 up" }));
    expect(await screen.findByText(/Unsaved order: the journey below is a preview/)).toBeInTheDocument();
    await vi.waitFor(() => expect(api.previewJourney).toHaveBeenCalledWith("v1", ["o2", "o1"]));
    expect(api.replaceCurriculumObjectives).not.toHaveBeenCalled();
    // Publishing waits for the order to be saved.
    expect(screen.getByRole("button", { name: /Publish/ })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Save order" }));
    expect(api.replaceCurriculumObjectives).toHaveBeenCalledWith("v1", ["o2", "o1"]);
    expect(await screen.findByText("Chapters saved.")).toBeInTheDocument();
  });
});
