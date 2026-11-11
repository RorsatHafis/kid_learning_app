/**
 * M12 attendance (owner decisions 2026-11-11): the day's register - mark the rest present, save what changed, and a
 * saved mark changes only with a reason the teacher gives.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ClassAttendancePanel, ClassStudentsPanel } from "./ClassAttendance";

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
      vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === "POST") {
          posted.push(JSON.parse(init.body as string));
          return respond(201, { added: [SOKHA], alreadyInClass: ["Dara Kim"] });
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
    expect(posted[0]).toEqual({ students: [{ name: "Sokha Chan", number: "S-001" }, { name: "Dara Kim", number: null }] });
  });
});
