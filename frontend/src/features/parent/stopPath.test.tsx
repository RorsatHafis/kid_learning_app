import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type { EnrollmentOverview } from "@/lib/api/types";
import { LearningNowSection } from "./LearningNowSection";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const ENROLLMENT = {
  enrollmentId: "e1",
  curriculum: { id: "c1", name: "Maths - Junior", subjectCode: "MATH", subjectName: "Maths", ageHubName: "Junior" },
  status: "ACTIVE",
  enrolledAt: "2026-10-01T00:00:00Z",
  completedSteps: 2,
  totalSteps: 10,
  nextStep: null,
  currentChapter: null,
  chapterCount: 0,
  chapters: [],
} as unknown as EnrollmentOverview;

function renderSection(refetch = vi.fn()) {
  render(
    <MemoryRouter>
      <LearningNowSection
        childId="child-1"
        childName="Dara"
        enrollments={{ data: [ENROLLMENT], error: null, isLoading: false, refetch }}
      />
    </MemoryRouter>,
  );
  return refetch;
}

afterEach(() => vi.unstubAllGlobals());

describe("Stopping a path (Y19)", () => {
  it("states what stopping means before it happens, then stops it", async () => {
    const fetch = vi.fn(async () => respond(200, { id: "e1", childId: "child-1", status: "WITHDRAWN" }));
    vi.stubGlobal("fetch", fetch);
    const refetch = renderSection();
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: /Stop this path…\s*: Maths - Junior/ }));
    expect(fetch).not.toHaveBeenCalled();
    const group = screen.getByRole("group", { name: "Stop Maths - Junior" });
    expect(group).toHaveTextContent(/Nothing is deleted: what Dara has learned stays/);
    await user.click(screen.getByRole("button", { name: "Stop the path" }));

    expect(fetch).toHaveBeenCalledTimes(1);
    expect(String((fetch.mock.calls[0] as unknown[])[0])).toMatch(/\/enrollments\/e1\/stop$/);
    expect(refetch).toHaveBeenCalled();
  });

  it("says why a path the class assigns can't be stopped here", async () => {
    const detail = "Grade 1 at Sunrise assigns this path, so it stays while the class does.";
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => respond(409, { status: 409, detail })),
    );
    const refetch = renderSection();
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: /Stop this path…/ }));
    await user.click(screen.getByRole("button", { name: "Stop the path" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(detail);
    expect(refetch).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Keep it" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
