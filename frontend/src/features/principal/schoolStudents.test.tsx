/** M12: the principal's view of every student record - classes, a family connection, and putting a former student back. */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { SchoolStudents } from "./SchoolStudents";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const SOKHA = {
  id: "s1",
  name: "Sokha Chan",
  number: "S-001",
  classes: [{ id: "c1", name: "Grade 1", archived: false }],
  connected: true,
  lastClass: null,
  leftOn: null,
};
const DARA = {
  id: "s2",
  name: "Dara Kim",
  number: null,
  classes: [],
  connected: false,
  lastClass: { id: "c0", name: "Grade 1 (2025)", archived: true },
  leftOn: "2026-06-30",
};
const CLASSES = [
  { id: "c1", schoolId: "sch", name: "Grade 1", teachers: [], archived: false },
  { id: "c0", schoolId: "sch", name: "Grade 1 (2025)", teachers: [], archived: true },
];

afterEach(() => vi.unstubAllGlobals());

describe("the school's students (M12)", () => {
  it("shows where each student is, and puts a former student back in a class as the same record", async () => {
    const requests: string[] = [];
    const posted: unknown[] = [];
    let dara = DARA as typeof DARA | typeof SOKHA;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        requests.push(path);
        if (init?.method === "POST" && path === "/schools/classes/c1/students/return") {
          posted.push(JSON.parse(init.body as string));
          dara = { ...DARA, classes: [{ id: "c1", name: "Grade 1", archived: false }], lastClass: null, leftOn: null } as never;
          return respond(200, { added: [{ id: "s2", name: "Dara Kim", number: null, since: "2026-11-11" }], alreadyInClass: [], atSchool: [] });
        }
        if (path.startsWith("/schools/sch/students")) return respond(200, { items: [dara, SOKHA], page: 0, size: 50, total: 2 });
        if (path === "/schools/sch/classes") return respond(200, CLASSES);
        return respond(404);
      }),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <SchoolStudents schoolId="sch" />
      </MemoryRouter>,
    );
    const sokha = (await screen.findByText("Sokha Chan")).closest("li")!;
    expect(within(sokha).getByRole("link", { name: "Grade 1" })).toHaveAttribute("href", "/principal/classes/c1");
    expect(within(sokha).getByText("Connected with a family")).toBeInTheDocument();
    // In a class: nothing to put back.
    expect(within(sokha).queryByRole("button", { name: "Put in the class" })).not.toBeInTheDocument();

    const daraRow = screen.getByText("Dara Kim").closest("li")!;
    expect(within(daraRow).getByText(/^Left Grade 1 \(2025\) on/)).toBeInTheDocument();
    // Only classes in use are offered.
    const select = within(daraRow).getByLabelText("Class for Dara Kim");
    expect(within(select).getAllByRole("option").map((o) => o.textContent)).toEqual(["Choose a class", "Grade 1"]);
    expect(within(daraRow).getByRole("button", { name: "Put in the class" })).toBeDisabled();
    await user.selectOptions(select, "c1");
    await user.click(within(daraRow).getByRole("button", { name: "Put in the class" }));
    expect(await screen.findByText("Dara Kim is in Grade 1 again.")).toBeInTheDocument();
    expect(posted[0]).toEqual({ studentIds: ["s2"] });
    expect(requests.filter((r) => r.startsWith("/schools/sch/students")).length).toBeGreaterThanOrEqual(2);
  });

  it("asks the server for those who left, and says when nothing matches", async () => {
    const requests: string[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        requests.push(path);
        if (path.startsWith("/schools/sch/students")) return respond(200, { items: [], page: 0, size: 50, total: 0 });
        return respond(200, []);
      }),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <SchoolStudents schoolId="sch" />
      </MemoryRouter>,
    );
    expect(await screen.findByText("No students yet")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Left" }));
    expect(await screen.findByText("No student matches")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Left" })).toHaveAttribute("aria-pressed", "true");
    expect(requests.some((r) => r.includes("which=FORMER"))).toBe(true);
  });
});
