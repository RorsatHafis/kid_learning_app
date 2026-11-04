/**
 * STREAK-1: what finishing an activity did to the streak comes from the server's streak before and after - lit,
 * grew, a milestone, or nothing (today already counted, or no reading: never guessed); the header shows the days.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import type { StreakSummary } from "@/lib/api/types";
import { streakGrowth } from "./streak";
import { StreakChip, StreakMoment } from "./StreakMoment";

const streak = (currentStreak: number, learnedToday: boolean, longestStreak = currentStreak): StreakSummary => ({
  childId: "c1",
  currentStreak,
  longestStreak,
  lastActivityDate: null,
  learnedToday,
});

function serverAnswers(body: StreakSummary) {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } })));
}

afterEach(() => vi.unstubAllGlobals());

describe("what finishing an activity did to the streak", () => {
  it("lit, grew or reached a milestone - only when this finish made today count", () => {
    expect(streakGrowth(streak(0, false), streak(1, true))).toEqual({ kind: "lit", days: 1, best: 1 });
    expect(streakGrowth(streak(1, false), streak(2, true))).toEqual({ kind: "grew", days: 2, best: 2 });
    expect(streakGrowth(streak(6, false, 9), streak(7, true, 9))).toEqual({ kind: "milestone", days: 7, best: 9 });
    expect(streakGrowth(streak(2, true), streak(2, true))).toBeNull();
    expect(streakGrowth(null, streak(1, true))).toBeNull();
    expect(streakGrowth(streak(0, false), null)).toBeNull();
  });

  it("says so after the activity, as the server counted it", async () => {
    serverAnswers(streak(4, true, 4));
    render(<StreakMoment childId="c1" before={streak(3, false)} />);
    expect(await screen.findByRole("status")).toHaveTextContent("Your flame grew - 4 days in a row!");
  });

  it("says nothing when today already counted", async () => {
    serverAnswers(streak(4, true));
    render(<StreakMoment childId="c1" before={streak(4, true)} />);
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});

describe("the streak in the child's header", () => {
  it("shows the days in a row", async () => {
    serverAnswers(streak(5, true));
    render(<StreakChip childId="c1" />);
    expect(await screen.findByRole("img", { name: "5-day learning streak" })).toHaveTextContent("5");
  });

  it("with no current streak: an unlit flame, no number, and how to light it", async () => {
    serverAnswers(streak(0, false, 6));
    render(<StreakChip childId="c1" />);
    const flame = await screen.findByRole("img", { name: /Finish an activity to light your flame/ });
    expect(flame).not.toHaveTextContent(/\d/);
  });
});
