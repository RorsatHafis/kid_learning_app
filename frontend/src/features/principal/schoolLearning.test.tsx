/**
 * The principal's comparison of classes (X21): who answered, where they stand, what moved, and a silent class
 * flagged.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { SchoolLearningSection } from "./SchoolLearningSection";

const point = (over: Record<string, number>) => ({
  at: "2026-10-04T00:00:00Z",
  children: 0,
  notStarted: 0,
  tooEarlyToTell: 0,
  needsPractice: 0,
  progressing: 0,
  secure: 0,
  ...over,
});
const still = { becameSecure: 0, movedUp: 0, firstReading: 0, movedDown: 0 };

afterEach(() => vi.unstubAllGlobals());

describe("SchoolLearningSection (X21)", () => {
  it("compares classes: who answered, where they stand, what moved - and flags a silent class", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(
        async () =>
          new Response(
            JSON.stringify({
              schoolId: "s1",
              schoolName: "Sunrise",
              weeks: 4,
              classes: [
                {
                  classId: "c1",
                  className: "Grade 1A",
                  children: 8,
                  skills: 4,
                  answeringThisWeek: 6,
                  answersThisWeek: 70,
                  now: point({ children: 8, secure: 10, progressing: 12, needsPractice: 4, notStarted: 6 }),
                  movement: { becameSecure: 3, movedUp: 2, firstReading: 4, movedDown: 1 },
                },
                {
                  classId: "c2",
                  className: "Grade 2B",
                  children: 5,
                  skills: 4,
                  answeringThisWeek: 0,
                  answersThisWeek: 0,
                  now: point({ children: 5, notStarted: 20 }),
                  movement: still,
                },
                {
                  classId: "c3",
                  className: "Grade 3C",
                  children: 0,
                  skills: 0,
                  answeringThisWeek: 0,
                  answersThisWeek: 0,
                  now: point({}),
                  movement: still,
                },
              ],
            }),
            { status: 200, headers: { "Content-Type": "application/json" } },
          ),
      ),
    );
    render(
      <MemoryRouter>
        <SchoolLearningSection schoolId="s1" />
      </MemoryRouter>,
    );

    const first = await screen.findByRole("link", { name: /Grade 1A/ });
    expect(first).toHaveAttribute("href", "/principal/classes/c1");
    expect(within(first).getByText("6 of 8 children answered this week")).toBeInTheDocument();
    expect(within(first).getByText("Last 4 weeks: 3 became secure · 2 moved up · 1 slipped")).toBeInTheDocument();
    expect(within(first).getByText("Now: 10 secure, 12 progressing, 4 needs practice, 6 not started.")).toBeInTheDocument();
    const silent = screen.getByRole("link", { name: /Grade 2B/ });
    expect(within(silent).getByText("0 of 5 children answered this week")).toHaveClass("text-light-deep");
    expect(within(silent).getByText("No standing changed in 4 weeks")).toBeInTheDocument();
    expect(within(screen.getByRole("link", { name: /Grade 3C/ })).getByText("No children yet")).toBeInTheDocument();
  });
});
