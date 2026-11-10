/**
 * V88 (owner 2026-11-10): on a plan with one subject learned at a time, the others wait. The card says what waiting
 * means and what a switch does before the family switches; the server makes the switch and its refusal is shown.
 */
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

function enrollment(id: string, name: string, status: "ACTIVE" | "PAUSED") {
  return {
    enrollmentId: id,
    curriculum: { id: `c-${id}`, name, subjectCode: "MATH", subjectName: "Maths", ageHubName: "Junior" },
    status,
    enrolledAt: "2026-10-01T00:00:00Z",
    completedSteps: 0,
    totalSteps: 4,
    nextStep: null,
    currentChapter: null,
    chapterCount: 0,
    chapters: [],
  } as unknown as EnrollmentOverview;
}

function renderSection(refetch = vi.fn()) {
  render(
    <MemoryRouter>
      <LearningNowSection
        childId="child-1"
        childName="Dara"
        enrollments={{
          data: [enrollment("e1", "Maths - Junior", "ACTIVE"), enrollment("e2", "Khmer - Junior", "PAUSED")],
          error: null,
          isLoading: false,
          refetch,
        }}
      />
    </MemoryRouter>,
  );
  return refetch;
}

afterEach(() => vi.unstubAllGlobals());

describe("Switching between subjects (V88)", () => {
  it("marks only the waiting subject, says what a switch does, and switches", async () => {
    const fetch = vi.fn(async () => respond(200, { id: "e2", childId: "child-1", status: "ACTIVE" }));
    vi.stubGlobal("fetch", fetch);
    const refetch = renderSection();
    const user = userEvent.setup();

    expect(screen.getAllByText("Waiting")).toHaveLength(1);
    expect(screen.getByText(/Dara learns one subject at a time on your plan\. Switch to Khmer - Junior/)).toBeInTheDocument();
    expect(screen.getByText(/nothing is lost on either/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Plans with more subjects at once" })).toHaveAttribute("href", "/parent/account#plan");
    expect(screen.getAllByRole("button", { name: /Learn this now/ })).toHaveLength(1);

    await user.click(screen.getByRole("button", { name: /^Learn this now.*Khmer - Junior$/ }));
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(String((fetch.mock.calls[0] as unknown[])[0])).toMatch(/\/enrollments\/e2\/switch$/);
    expect(refetch).toHaveBeenCalled();
  });

  it("shows the server's refusal and changes nothing", async () => {
    const detail = "Your family's plan includes one active journey per child at a time. Switch to another to learn it.";
    vi.stubGlobal("fetch", vi.fn(async () => respond(409, { status: 409, detail, entitlement: "ACTIVE_JOURNEYS_PER_CHILD_MAX", limit: 1 })));
    const refetch = renderSection();
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: /Learn this now/ }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(refetch).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: /Learn this now/ })).toBeEnabled();
  });
});
