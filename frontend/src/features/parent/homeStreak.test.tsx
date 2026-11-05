/**
 * STREAK-1 for parents: the family home says how many days in a row a child has learned, from two days up - activity,
 * worded as such - and nothing for a single day or when the streak can't be read.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ParentHome } from "./ParentHome";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function family(streak: number) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      const path = String(url).replace(/^.*\/api\/v1/, "");
      if (path === "/children") return respond(200, [{ id: "c1", displayName: "Sokha", dateOfBirth: "2019-03-14" }]);
      if (path === "/children/c1/streak") return respond(200, { childId: "c1", currentStreak: streak, longestStreak: streak, lastActivityDate: null, learnedToday: true });
      if (path.startsWith("/children/c1/changes")) return respond(200, { answersSince: 0, changes: [] });
      return respond(200, []);
    }),
  );
  render(
    <MemoryRouter>
      <ParentHome />
    </MemoryRouter>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("the learning streak on the family home", () => {
  it("says the days in a row from two days up", async () => {
    family(3);
    expect(await screen.findByText("Learned 3 days in a row")).toBeInTheDocument();
  });

  it("says nothing for a single day", async () => {
    family(1);
    await screen.findByText("Sokha");
    await new Promise((resolve) => setTimeout(resolve, 30));
    expect(screen.queryByText(/days in a row/)).not.toBeInTheDocument();
  });
});
