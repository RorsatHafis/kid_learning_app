/**
 * M12 attendance (owner decisions 2026-11-11): the day's register - mark the rest present, save what changed, and a
 * saved mark changes only with a reason the teacher gives.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ClassAttendancePanel, ClassStudentsPanel, parseStudentLines } from "./ClassAttendance";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const SOKHA = { id: "s1", name: "Sokha Chan", number: "S-001", since: "2026-11-01" };
const DARA = { id: "s2", name: "Dara Kim", number: null, since: "2026-11-01" };
const register = (marks: Record<string, string | null>) => ({
  classId: "c1",
  day: "2026-11-11",
  today: true,
  editable: true,
  rows: [SOKHA, DARA].map((student) => ({ student, status: marks[student.id] ?? null, note: null, recordedAt: null, corrected: false })),
  counts: { PRESENT: Object.values(marks).filter((s) => s === "PRESENT").length, ABSENT: 0, LATE: 0, EXCUSED: 0 },
  unmarked: [SOKHA, DARA].filter((s) => !marks[s.id]).length,
});

afterEach(() => vi.unstubAllGlobals());

describe("a class's register (M12)", () => {
  it("marks the rest present, saves what changed, and asks why before a saved mark changes", async () => {
    const puts: { marks: { studentId: string; status: string }[]; reason?: string }[] = [];
    let current = register({});
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (init?.method === "PUT" && path.startsWith("/schools/classes/c1/attendance")) {
          const body = JSON.parse(init.body as string);
          puts.push(body);
          const marks: Record<string, string> = {};
          current.rows.forEach((r) => r.status && (marks[r.student.id] = r.status));
          body.marks.forEach((m: { studentId: string; status: string }) => (marks[m.studentId] = m.status));
          current = register(marks);
          return respond(200, current);
        }
        if (path.startsWith("/schools/classes/c1/attendance/month")) return respond(200, { classId: "c1", month: "2026-11", daysWithMarks: 0, rows: [] });
        return respond(200, current);
      }),
    );
    const user = userEvent.setup();
    render(<ClassAttendancePanel classId="c1" />);

    expect(await screen.findByText(/2 not marked yet/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Mark the rest present" }));
    await user.click(within(screen.getByRole("group", { name: "Dara Kim" })).getByText("Absent"));
    await user.click(screen.getByRole("button", { name: "Save attendance" }));
    expect(await screen.findByText("Attendance saved.")).toBeInTheDocument();
    expect(puts[0].marks).toEqual([
      { studentId: "s1", status: "PRESENT", note: null },
      { studentId: "s2", status: "ABSENT", note: null },
    ]);
    expect(puts[0].reason).toBeUndefined();

    // Changing a saved mark: the reason is asked for, and sent.
    await user.click(within(screen.getByRole("group", { name: "Dara Kim" })).getByText("Late"));
    await user.click(screen.getByRole("button", { name: "Save attendance" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Say why the saved marks are changing");
    await user.type(screen.getByLabelText(/Why is a saved mark being changed/), "Arrived after the register");
    await user.click(screen.getByRole("button", { name: "Save attendance" }));
    expect(await screen.findByText("Attendance saved.")).toBeInTheDocument();
    expect(puts[1]).toMatchObject({ marks: [{ studentId: "s2", status: "LATE" }], reason: "Arrived after the register" });
  });

  it("adds students from one name per line, with the school's number after a comma", async () => {
    const posted: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init?: RequestInit) => {
        if (init?.method === "POST") {
          posted.push(JSON.parse(init.body as string));
          return respond(201, { added: [SOKHA], alreadyInClass: ["Dara Kim"], atSchool: [] });
        }
        return respond(200, []);
      }),
    );
    const user = userEvent.setup();
    render(<ClassStudentsPanel classId="c1" archived={false} />);
    expect(await screen.findByText(/No students yet/)).toBeInTheDocument();
    await user.type(screen.getByLabelText("Names, one per line"), "Sokha Chan, S-001{enter}Dara Kim");
    await user.click(screen.getByRole("button", { name: "Add students" }));
    expect(await screen.findByRole("status")).toHaveTextContent("1 student added. Already in the class, so not added again: Dara Kim.");
    expect(posted[0]).toEqual({ students: [{ name: "Sokha Chan", number: "S-001" }, { name: "Dara Kim", number: null }], sameNameIsNew: false });
  });

  it("brings chosen students from another class as the same records", async () => {
    const posted: { url: string; body: unknown }[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (init?.method === "POST") {
          posted.push({ url: path, body: JSON.parse(init.body as string) });
          return respond(200, { added: [SOKHA], alreadyInClass: [] });
        }
        if (path === "/schools/classes/last-year/students") return respond(200, [SOKHA, DARA]);
        return respond(200, []);
      }),
    );
    const user = userEvent.setup();
    render(
      <ClassStudentsPanel
        classId="c1"
        archived={false}
        otherClasses={[
          { id: "last-year", schoolId: "sch", name: "Grade 1 (2025)", archived: true },
          { id: "c2", schoolId: "sch", name: "Grade 2B", archived: false },
        ]}
      />,
    );
    const from = await screen.findByLabelText("From the class");
    // Classes in use first; an archived one says so.
    expect(within(from).getAllByRole("option").map((o) => o.textContent)).toEqual(["Choose a class", "Grade 2B", "Grade 1 (2025) (archived)"]);
    await user.selectOptions(from, "last-year");
    const bring = await screen.findByRole("button", { name: "Bring 0 students here" });
    expect(bring).toBeDisabled();
    await user.click(screen.getByRole("checkbox", { name: /Sokha Chan/ }));
    await user.click(screen.getByRole("button", { name: "Bring 1 student here" }));
    expect(await screen.findByText("1 student is now in this class.")).toBeInTheDocument();
    expect(posted[0]).toEqual({ url: "/schools/classes/c1/students/move", body: { fromClassId: "last-year", studentIds: ["s1"] } });
  });

  it("holds back a name already in another class, and adds it only when they're different students", async () => {
    const posted: { students: { name: string; number: string | null }[]; sameNameIsNew: boolean }[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init?: RequestInit) => {
        if (init?.method === "POST") {
          const body = JSON.parse(init.body as string);
          posted.push(body);
          return body.sameNameIsNew
            ? respond(201, { added: [SOKHA], alreadyInClass: [], atSchool: [] })
            : respond(201, { added: [DARA], alreadyInClass: [], atSchool: [{ name: "Sokha Chan", classId: "c9", className: "Grade 1 (2025)" }] });
        }
        return respond(200, []);
      }),
    );
    const user = userEvent.setup();
    render(<ClassStudentsPanel classId="c1" archived={false} />);
    await user.type(await screen.findByLabelText("Names, one per line"), "Sokha Chan, S-001{enter}Dara Kim");
    await user.click(screen.getByRole("button", { name: "Add students" }));
    expect(await screen.findByText("1 student added.")).toBeInTheDocument();
    expect(screen.getByText("Already at the school in another class, so not added yet:")).toBeInTheDocument();
    expect(screen.getByText("Sokha Chan, in Grade 1 (2025)")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Add as a new student" }));
    expect(await screen.findByText("1 student added.")).toBeInTheDocument();
    expect(screen.queryByText(/not added yet/)).not.toBeInTheDocument();
    expect(posted[0].sameNameIsNew).toBe(false);
    // Only the held name is sent again, with its number, confirmed as a new student.
    expect(posted[1]).toEqual({ students: [{ name: "Sokha Chan", number: "S-001" }], sameNameIsNew: true });
  });

  it("reads names typed with a comma or pasted as two spreadsheet columns", () => {
    expect(parseStudentLines("Sokha Chan, S-001\r\nDara Kim\n\n  Chan, Sreymom , \nVannak Ly\tS-004\nBopha Kim\t\nPisey Ouk\tS-006\tGrade 1\n\tS-007")).toEqual([
      { name: "Sokha Chan", number: "S-001" },
      { name: "Dara Kim", number: null },
      { name: "Chan, Sreymom", number: null },
      { name: "Vannak Ly", number: "S-004" },
      { name: "Bopha Kim", number: null },
      { name: "Pisey Ouk", number: "S-006" },
    ]);
  });

  it("offers no move without another class, nor in an archived class", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => respond(200, [])));
    const { unmount } = render(<ClassStudentsPanel classId="c1" archived={false} />);
    expect(await screen.findByText(/No students yet/)).toBeInTheDocument();
    expect(screen.queryByText("Bring students from another class")).not.toBeInTheDocument();
    unmount();
    render(
      <ClassStudentsPanel classId="c1" archived otherClasses={[{ id: "c2", schoolId: "sch", name: "Grade 2B", archived: false }]} />,
    );
    expect(await screen.findByText(/No students yet/)).toBeInTheDocument();
    expect(screen.queryByText("Bring students from another class")).not.toBeInTheDocument();
  });
});
