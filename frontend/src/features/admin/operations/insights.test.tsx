/**
 * Administrator insight pages: what each school still has to set up, an empty platform explained, and the learning
 * loop with the skills nothing practises.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { SchoolsPage } from "./SchoolsPage";
import { LearningOpsPage } from "./LearningOpsPage";
import { ReportsPage } from "./ReportsPage";
import { SystemPage } from "./SystemPage";
import { formatBytes, formatUptime } from "./systemText";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

function serve(body: unknown) {
  const fetch = vi.fn(async () => respond(200, body));
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

function school(overrides: Record<string, unknown>) {
  return {
    id: "s1",
    name: "Riverside Primary",
    status: "ACTIVE",
    createdAt: "2026-09-01T00:00:00Z",
    principalEmail: "principal@example.com",
    principalStatus: "ACTIVE",
    classes: 3,
    teachers: 2,
    childrenWithAccess: 41,
    classesWithoutTeacher: 0,
    classesWithoutCurriculum: 0,
    ...overrides,
  };
}

afterEach(() => vi.unstubAllGlobals());

describe("SchoolsPage", () => {
  it("says what is still to set up at each school, and links to the school", async () => {
    serve({
      items: [
        school({}),
        school({ id: "s2", name: "Hill School", classesWithoutTeacher: 1, classesWithoutCurriculum: 2 }),
        school({ id: "s3", name: "Old Mill School", status: "ARCHIVED", classes: 0 }),
      ],
      page: 0,
      size: 25,
      total: 3,
    });
    render(
      <MemoryRouter initialEntries={["/admin/schools"]}>
        <SchoolsPage />
      </MemoryRouter>,
    );

    const list = await screen.findByRole("region", { name: "Schools" });
    const riverside = within(list).getByRole("link", { name: /Riverside Primary/ });
    expect(riverside).toHaveAttribute("href", "/s1");
    expect(within(riverside).getByText("Set up")).toBeInTheDocument();
    expect(within(riverside).getByText(/3 classes · 2 teachers · 41 children with access/)).toBeInTheDocument();
    const hill = within(list).getByRole("link", { name: /Hill School/ });
    expect(within(hill).getByText(/1 class has no teacher · 2 classes assign nothing to learn/)).toBeInTheDocument();
    // Y17: an archived school is said to be archived, not listed as having nothing set up.
    const mill = within(list).getByRole("link", { name: /Old Mill School/ });
    expect(within(mill).getByText("Archived: takes nothing new")).toBeInTheDocument();
    expect(within(mill).queryByText(/no classes yet/)).not.toBeInTheDocument();
  });

  it("explains an empty platform rather than showing an empty list", async () => {
    serve({ items: [], page: 0, size: 25, total: 0 });
    render(
      <MemoryRouter>
        <SchoolsPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText("No schools yet")).toBeInTheDocument();
  });
});

function operations(overrides: Record<string, unknown>) {
  return {
    lastSevenDays: {
      attemptsStarted: 120,
      attemptsCompleted: 96,
      childrenLearning: 31,
      answers: 800,
      answersCorrect: 600,
      attemptsLeftOpen: 4,
      placementsBySource: { CURRICULUM: 40, ADAPTIVE: 12, REVIEW: 9, TEACHER_ASSIGNED: 3 },
      reviewItemsActive: 50,
      reviewDueNow: 7,
    },
    coverageGaps: [],
    ageGroupsWithNothingOffered: [],
    ...overrides,
  };
}

describe("LearningOpsPage", () => {
  it("reports the loop and names each skill nothing practises", async () => {
    serve(
      operations({
        coverageGaps: [{ skillId: "k1", skill: "Counting to 20", subject: "Maths", curriculum: "Early numbers" }],
        ageGroupsWithNothingOffered: ["Ages 7-8"],
      }),
    );
    render(
      <MemoryRouter>
        <LearningOpsPage />
      </MemoryRouter>,
    );

    const week = await screen.findByRole("region", { name: "The last 7 days" });
    expect(within(week).getByText("75% correct")).toBeInTheDocument();
    expect(within(week).getByText("of 120 started")).toBeInTheDocument();
    const gaps = screen.getByRole("region", { name: "Content gaps" });
    expect(within(gaps).getByText("Counting to 20")).toBeInTheDocument();
    expect(within(gaps).getByText(/Nothing is offered to families for: Ages 7-8/)).toBeInTheDocument();
    expect(within(gaps).getByRole("link", { name: "Open the Content Studio" })).toHaveAttribute("href", "/admin/studio");
    expect(screen.getByText(/50 skills under review, 7 due now/)).toBeInTheDocument();
  });

  it("says so when every offered skill can be practised", async () => {
    serve(operations({}));
    render(
      <MemoryRouter>
        <LearningOpsPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText(/Every skill in an offered curriculum has a published activity/)).toBeInTheDocument();
  });
});

describe("ReportsPage", () => {
  it("leads each chart with this week and carries every figure in a table", async () => {
    const weeks = Array.from({ length: 12 }, (_, i) => ({
      weekOf: new Date(Date.UTC(2026, 6, 13 + i * 7)).toISOString().slice(0, 10),
      newFamilies: i,
      childrenLearning: i * 2,
      activitiesCompleted: i * 5,
      answers: i * 40,
      newSchools: 0,
    }));
    serve(weeks);
    render(
      <MemoryRouter>
        <ReportsPage />
      </MemoryRouter>,
    );

    const answers = await screen.findByRole("region", { name: "Answers" });
    // The headline (the scale above the bars repeats it when this week is the highest).
    expect(within(answers).getAllByText("440")[0]).toHaveClass("font-display");
    expect(within(answers).getByText(/400 the week before/)).toBeInTheDocument();
    const table = screen.getByRole("table");
    expect(within(table).getAllByRole("row")).toHaveLength(13);
    expect(within(table).getByRole("columnheader", { name: "New schools" })).toBeInTheDocument();

    // The same figures download as CSV, one row per week, oldest first.
    let saved: Blob | null = null;
    Object.assign(URL, {
      createObjectURL: (blob: Blob) => {
        saved = blob;
        return "blob:report";
      },
      revokeObjectURL: vi.fn(),
    });
    await userEvent.setup().click(screen.getByRole("button", { name: "Download CSV" }));
    const text = await new Promise<string>((resolve) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.readAsText(saved as unknown as Blob);
    });
    const lines = text.trim().split(/\r\n/);
    expect(lines).toHaveLength(13);
    expect(lines[0]).toBe('"Week of (Monday, UTC)",Children learning,Activities finished,Answers,New families,New schools');
    expect(lines[12]).toBe("2026-09-28,22,55,440,11,0");
  });
});

describe("SystemPage", () => {
  const status = {
    version: "development build",
    profiles: "prod",
    schemaVersion: "48",
    databaseBytes: 52_428_800,
    mediaFiles: 12,
    mediaBytes: 3_145_728,
    mediaFreeBytes: 500 * 1024 ** 2,
    mailHost: "smtp.example.org",
    publicUrl: "https://app.example.org",
    uptimeSeconds: 90_000,
    javaVersion: "21.0.4",
    heapUsedBytes: 200 * 1024 ** 2,
    heapMaxBytes: 400 * 1024 ** 2,
    checkedAt: "2026-10-02T09:00:00Z",
  };

  it("shows the build and storage, and warns when media space runs low", async () => {
    serve(status);
    render(
      <MemoryRouter>
        <SystemPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText("V48")).toBeInTheDocument();
    expect(screen.getByText("12 (3.0 MB)")).toBeInTheDocument();
    expect(screen.getByText("200 MB of 400 MB (50%)")).toBeInTheDocument();
    expect(screen.getByText(/Less than 1 GB is free where media is stored/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open the activity log/ })).toHaveAttribute("href", "/admin/activity");
  });

  it("formats sizes and uptime for a person", () => {
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(1536)).toBe("1.5 KB");
    expect(formatBytes(5 * 1024 ** 3)).toBe("5.0 GB");
    expect(formatUptime(90_000)).toBe("1 d 1 h");
    expect(formatUptime(3_700)).toBe("1 h 1 min");
    expect(formatUptime(59)).toBe("0 min");
  });
});
