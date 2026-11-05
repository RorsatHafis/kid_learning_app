/**
 * STREAK-1 on the home and shelf: the real current streak with the flame, said without loss or guilt, and never a
 * best-ever count.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { StreakCard } from "./StreakMoment";

function streak(current: number, learnedToday: boolean, longest = current) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () =>
      new Response(JSON.stringify({ childId: "c1", currentStreak: current, longestStreak: longest, lastActivityDate: null, learnedToday }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    ),
  );
  render(<StreakCard childId="c1" />);
}

afterEach(() => vi.unstubAllGlobals());

describe("the streak card", () => {
  it("invites lighting the flame when there is no streak", async () => {
    streak(0, false, 9);
    expect(await screen.findByText("Light your flame")).toBeInTheDocument();
    expect(screen.queryByText(/best/i)).not.toBeInTheDocument();
  });

  it("shows the days in a row, and invites today without loss words", async () => {
    streak(4, false, 12);
    expect(await screen.findByText("4 days in a row")).toBeInTheDocument();
    expect(screen.getByText("Learn today and your flame grows.")).toBeInTheDocument();
    expect(screen.queryByText(/best|lose|lost|miss/i)).not.toBeInTheDocument();
  });

  it("says the flame is glowing once today counts", async () => {
    streak(1, true);
    expect(await screen.findByText("1 day of learning")).toBeInTheDocument();
    expect(screen.getByText("You learned today - your flame is glowing.")).toBeInTheDocument();
  });
});
