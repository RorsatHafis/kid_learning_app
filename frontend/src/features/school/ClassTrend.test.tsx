/**
 * A class's trend over the weeks: what moved, with every week in a table, and an empty period said plainly.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { ClassTrendSection } from "./ClassTrend";

function respond(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}

const point = (at: string, over: Record<string, number>) => ({
  at,
  children: 2,
  notStarted: 0,
  tooEarlyToTell: 0,
  needsPractice: 0,
  progressing: 0,
  secure: 0,
  ...over,
});

function trend(weeks: number) {
  const points = Array.from({ length: weeks + 1 }, (_, i) =>
    point(new Date(Date.UTC(2026, 8, 1 + i * 7)).toISOString(), i < weeks ? { notStarted: 2 } : { secure: 1, needsPractice: 1 }),
  );
  return {
    weeks,
    skills: 1,
    points,
    answers: points
      .slice(1)
      .map((p, i) => ({ from: points[i].at, to: p.at, answers: i === weeks - 1 ? 9 : 0, childrenAnswering: 2 })),
    movement: { becameSecure: 1, movedUp: 0, firstReading: 1, movedDown: 0 },
    bySkill: [{ skillId: "sk1", name: "Counting to 20", becameSecure: 1, movedUp: 0, movedDown: 0 }],
  };
}

afterEach(() => vi.unstubAllGlobals());

describe("ClassTrendSection", () => {
  it("says what moved, links the skills, and carries every week in a table", async () => {
    const urls: string[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        urls.push(String(url));
        return respond(trend(Number(new URL(String(url), "http://x").searchParams.get("weeks"))));
      }),
    );
    render(
      <MemoryRouter>
        <ClassTrendSection classId="c1" skillHref={(id) => `/teacher/classes/c1/skills/${id}`} refreshKey={0} />
      </MemoryRouter>,
    );

    expect(
      await screen.findByText(/Over the last 8 weeks: 1 standing became secure, 1 got a first reading\./),
    ).toBeInTheDocument();
    expect(screen.getByText(/9 answers given in the class/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Counting to 20/ })).toHaveAttribute("href", "/teacher/classes/c1/skills/sk1");
    const table = screen.getByRole("table");
    expect(within(table).getAllByRole("row")).toHaveLength(10);
    expect(within(table).getByRole("rowheader", { name: "Now" })).toBeInTheDocument();
    // Nothing names a child.
    expect(screen.queryByText(/Dara/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "4 weeks" }));
    expect(await screen.findByText(/Over the last 4 weeks/)).toBeInTheDocument();
    expect(urls.at(-1)).toMatch(/\/schools\/classes\/c1\/trend\?weeks=4$/);
    expect(screen.getByRole("button", { name: "4 weeks" })).toHaveAttribute("aria-pressed", "true");
  });

  it("says so when no child was in the class during the period", async () => {
    const empty = trend(8);
    empty.points = empty.points.map((p) => ({ ...p, children: 0, notStarted: 0, secure: 0, needsPractice: 0 }));
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => respond(empty)),
    );
    render(
      <MemoryRouter>
        <ClassTrendSection classId="c1" skillHref={(id) => id} refreshKey={0} />
      </MemoryRouter>,
    );
    expect(await screen.findByText("No child was in the class during these weeks.")).toBeInTheDocument();
  });
});
