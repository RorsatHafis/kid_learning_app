/**
 * A teacher's first steps, read from what is true now: the next step is the first not done, with where to do it; done
 * steps say so to screen readers; once every step is done the card goes away.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { GettingStarted } from "./GettingStarted";
import { schoolApi } from "@/lib/api/endpoints";
import { studioApi } from "@/lib/api/studio";
import type { ClassOverview } from "@/lib/api/schoolAccess";

const CLASS = {
  classId: "c1",
  className: "Grade 1A",
  schoolId: "s1",
  schoolName: "Lotus",
  children: 0,
  curricula: [],
  teachers: 1,
  joinCode: { open: true, expiresAt: null },
  attention: {},
  archived: false,
} as unknown as ClassOverview;

afterEach(() => vi.restoreAllMocks());

function renderSteps(classes: ClassOverview[], lessons: number) {
  vi.spyOn(schoolApi, "myMemberships").mockResolvedValue([{ id: "m1", schoolId: "s1", schoolName: "Lotus", joinedAt: "2026-11-01" }]);
  vi.spyOn(studioApi, "ownLessonsTotal").mockResolvedValue(lessons);
  return render(
    <MemoryRouter>
      <GettingStarted classes={classes} />
    </MemoryRouter>,
  );
}

describe("a teacher's first steps", () => {
  it("points at the next step with where to do it", async () => {
    renderSteps([CLASS], 0);
    const card = await screen.findByRole("region", { name: "Getting started" });
    expect(within(card).getByText("2 of 5 done. Each step shows how; this goes away once all are done.")).toBeInTheDocument();
    const steps = within(card).getAllByRole("listitem");
    expect(steps[0]).toHaveTextContent("Join your school - done");
    expect(steps[1]).toHaveTextContent("Get a class - done");
    expect(steps[2]).toHaveAttribute("aria-current", "step");
    expect(within(steps[2]).getByRole("link", { name: "Open the class" })).toHaveAttribute("href", "/teacher/classes/c1");
    // Only the next step links; the later ones say what they are.
    expect(within(steps[4]).queryByRole("link")).not.toBeInTheDocument();
    expect(steps[4]).toHaveTextContent("Write an activity of your own - to do");
  });

  it("goes away once every step is done", async () => {
    const { container } = renderSteps([{ ...CLASS, children: 3, curricula: [{ curriculumId: "k1" }] } as unknown as ClassOverview], 2);
    await new Promise((r) => setTimeout(r, 0));
    expect(screen.queryByRole("region", { name: "Getting started" })).not.toBeInTheDocument();
    expect(container).toBeEmptyDOMElement();
  });
});
