/**
 * Break reminders: a sitting grows while the child learns and resets after time away, a break is suggested only
 * when a parent set one, and the parent's setting saves safely.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AWAY_MS, breakDue, sittingMinutes, touched } from "./breaks";
import { BreakReminderSection } from "@/features/parent/BreakReminderSection";

const MIN = 60_000;

describe("a sitting", () => {
  it("grows while the child keeps learning and starts again after ten minutes away", () => {
    const first = touched(null, 0);
    expect(first).toEqual({ start: 0, last: 0 });
    const later = touched(first, 8 * MIN);
    expect(later).toEqual({ start: 0, last: 8 * MIN });
    expect(touched(later, 8 * MIN + AWAY_MS + 1)).toEqual({ start: 8 * MIN + AWAY_MS + 1, last: 8 * MIN + AWAY_MS + 1 });
  });

  it("suggests a break only when the parent set one, and only once the sitting is that long", () => {
    const sitting = { start: 0, last: 19 * MIN };
    expect(breakDue(null, sitting, 60 * MIN)).toBe(false);
    expect(breakDue(20, sitting, 19 * MIN)).toBe(false);
    expect(breakDue(20, sitting, 20 * MIN)).toBe(true);
    expect(breakDue(20, null, 60 * MIN)).toBe(false);
    expect(sittingMinutes(sitting, 21 * MIN + 20_000)).toBe(21);
  });
});

function respond(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

afterEach(() => vi.unstubAllGlobals());

describe("BreakReminderSection", () => {
  it("shows the saved setting and saves a new one, keeping the old one when a save fails", async () => {
    const puts: unknown[] = [];
    let fail = false;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init?: RequestInit) => {
        if (init?.method === "PUT") {
          const body = JSON.parse(init.body as string);
          puts.push(body);
          return fail ? respond(500, { status: 500, detail: "x" }) : respond(200, body);
        }
        return respond(200, { minutes: null });
      }),
    );
    const user = userEvent.setup();
    render(<BreakReminderSection childId="c1" childName="Dara" />);

    const select = await screen.findByLabelText("Suggest a break after");
    await vi.waitFor(() => expect(select).toBeEnabled());
    expect(select).toHaveValue("");
    await user.selectOptions(select, "20");
    expect(puts).toEqual([{ minutes: 20 }]);
    expect(await screen.findByRole("status")).toHaveTextContent("Saved.");
    expect(select).toHaveValue("20");

    fail = true;
    await user.selectOptions(select, "45");
    expect(await screen.findByRole("alert")).toHaveTextContent("That didn't save");
    expect(select).toHaveValue("20");
  });
});
