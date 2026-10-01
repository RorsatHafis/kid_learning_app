import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ChangesSection } from "./ChangesSection";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const change = (over: Record<string, unknown>) => ({ lastPracticedAt: null, ...over });

const FOUR_WEEKS = {
  since: "2026-09-03T00:00:00Z",
  days: 28,
  answersSince: 9,
  changes: [
    change({
      skillId: "s1",
      name: "Add within 10",
      kind: "MOVED_UP",
      then: "NEEDS_PRACTICE",
      now: "PROGRESSING",
      answersSince: 4,
    }),
    change({
      skillId: "s2",
      name: "Count to 20",
      kind: "STARTED",
      then: "NOT_STARTED",
      now: "TOO_EARLY_TO_TELL",
      answersSince: 1,
    }),
    change({ skillId: "s3", name: "Doubles", kind: "PRACTISED", then: "PROGRESSING", now: "PROGRESSING", answersSince: 2 }),
    change({ skillId: "s4", name: "Take away", kind: "MOVED_DOWN", then: "SECURE", now: "PROGRESSING", answersSince: 2 }),
  ],
};

afterEach(() => vi.unstubAllGlobals());

describe("ChangesSection", () => {
  it("groups each skill answered on by how it moved, with where it stood then and now", async () => {
    const calls: string[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        calls.push(String(url));
        return String(url).endsWith("days=7")
          ? respond(200, { since: "2026-09-24T00:00:00Z", days: 7, answersSince: 0, changes: [] })
          : respond(200, FOUR_WEEKS);
      }),
    );
    const user = userEvent.setup();
    render(<ChangesSection childId="c1" childName="Sokha" />);

    expect(await screen.findByText("9 answers")).toBeInTheDocument();
    const up = screen.getByRole("region", { name: "Moving up" });
    expect(within(up).getByText("Add within 10")).toBeInTheDocument();
    expect(within(up).getByText("Needs practice")).toBeInTheDocument();
    expect(within(up).getByText("Progressing")).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Just started" })).toHaveTextContent("Count to 20");
    // A skill that slipped is told plainly, and last.
    const regions = screen.getAllByRole("region").map((r) => r.getAttribute("aria-labelledby"));
    expect(regions[regions.length - 1]).toBe("change-MOVED_DOWN");
    expect(screen.getByRole("region", { name: "Needs more practice again" })).toHaveTextContent("Take away");
    expect(screen.queryByRole("region", { name: "Now secure" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "This week" }));
    expect(await screen.findByText("No answers this week")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "This week" })).toHaveAttribute("aria-pressed", "true");
    expect(calls).toEqual(["/api/v1/children/c1/changes?days=28", "/api/v1/children/c1/changes?days=7"]);
  });
});
