import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { PrincipalClassesPage, PrincipalHome, PrincipalPlanPage, PrincipalTeachersPage } from "./PrincipalHome";
import { PrincipalClassPage } from "./PrincipalClassPage";
import { SchoolInvitations } from "@/features/teacher/SchoolInvitations";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

type Handler = (method: string, body: unknown) => Response;

/** A fetch double keyed by "METHOD /path"; every call is recorded. */
function server(routes: Record<string, Handler | Response>) {
  const calls: { method: string; path: string; body: unknown }[] = [];
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    const path = String(url).replace(/^.*\/api\/v1/, "");
    const body = init?.body ? JSON.parse(init.body as string) : undefined;
    calls.push({ method, path, body });
    const route = routes[`${method} ${path}`];
    if (!route) return respond(404, { status: 404, detail: `no route ${method} ${path}` });
    return typeof route === "function" ? route(method, body) : route.clone();
  });
  vi.stubGlobal("fetch", fetchMock);
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

const SCHOOL = { id: "s1", name: "Sunrise Primary", principalAccountId: "p1", archived: false };

const NO_LEARNING = { schoolId: "s1", schoolName: "Sunrise Primary", weeks: 4, classes: [] };

/** GET /schools/{id}/plan: a paid school (Y44: its services need a confirmed payment); unlimited unless given limits. */
function schoolPlan(entitlements: { key: string; limit: number | null; used: number | null }[], onDefaultPlan = true, paid = true) {
  return {
    schoolId: "s1",
    schoolName: "Sunrise Primary",
    plan: {
      id: "sp",
      code: "SCHOOL_EARLY_ACCESS",
      name: "School early access",
      description: null,
      audience: "SCHOOL",
      status: "ACTIVE",
      isDefault: onDefaultPlan,
      selfService: false,
      price: null,
    },
    onDefaultPlan,
    subscription: onDefaultPlan
      ? null
      : {
          status: "ACTIVE",
          source: "PROVIDER",
          startedAt: "2026-09-01T00:00:00Z",
          currentPeriodEnd: "2027-09-01T00:00:00Z",
          cancelAtPeriodEnd: false,
          grantsAccess: true,
        },
    entitlements,
    access: { paid, paidUntil: paid ? "2027-09-01T00:00:00Z" : null, paymentPending: false },
  };
}

const SCHOOL_PLAN = schoolPlan([
  { key: "SCHOOL_CHILDREN_MAX", limit: null, used: 0 },
  { key: "SCHOOL_TEACHERS_MAX", limit: null, used: 0 },
  { key: "SCHOOL_CLASSES_MAX", limit: null, used: 0 },
  { key: "TEACHER_STORAGE_MB", limit: 1024, used: null },
]);

const EMPTY_OVERVIEW = {
  schools: [
    {
      schoolId: "s1",
      name: "Sunrise Primary",
      childrenWithAccess: 0,
      classes: 0,
      classesWithoutTeacher: 0,
      classesWithoutCurriculum: 0,
      classesWithoutOpenCode: 0,
      paid: true,
    },
  ],
  classes: [],
  unpaid: [],
};

function classOverview(over: Record<string, unknown>) {
  return {
    classId: "c1",
    className: "Grade 1A",
    schoolId: "s1",
    schoolName: "Sunrise Primary",
    children: 12,
    curricula: [{ curriculumId: "cu1", name: "Maths - Junior", subjectName: "Maths" }],
    teachers: 1,
    joinCode: { open: true, expiresAt: "2026-10-14T00:00:00Z" },
    attention: { childrenNeedingPractice: 0, skillsNeedingPractice: 0, childrenNotStarted: 0 },
    ...over,
  };
}

describe("PrincipalHome", () => {
  it("asks a principal without a school to set one up, then shows the school", async () => {
    let created = false;
    const calls = server({
      "GET /schools/mine": () => respond(200, created ? [SCHOOL] : []),
      "GET /schools/s1/learning": respond(200, NO_LEARNING),
      "GET /schools/s1/plan": respond(200, SCHOOL_PLAN),
      "POST /schools": () => {
        created = true;
        return respond(201, SCHOOL);
      },
      "GET /schools/s1/classes": respond(200, []),
      "GET /schools/overview": respond(200, EMPTY_OVERVIEW),
      "GET /schools/s1/staff": respond(200, []),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <PrincipalHome />
      </MemoryRouter>,
    );

    await user.type(await screen.findByLabelText("School name"), "  Sunrise Primary ");
    await user.click(screen.getByRole("button", { name: "Create school" }));

    expect(await screen.findByRole("heading", { name: "Sunrise Primary" })).toBeInTheDocument();
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ name: "Sunrise Primary" });
    expect(await screen.findByText("Add your first class below, then invite its teacher.")).toBeInTheDocument();
  });

  it("invites a teacher, and shows the server's reason when an address can't be invited", async () => {
    const staff = [
      {
        id: "m1",
        accountId: "t1",
        email: "active@example.com",
        status: "ACTIVE",
        invitedAt: "2026-09-01T00:00:00Z",
        respondedAt: null,
      },
      {
        id: "m2",
        accountId: "t2",
        email: "gone@example.com",
        status: "REMOVED",
        invitedAt: "2026-09-01T00:00:00Z",
        respondedAt: null,
      },
    ];
    server({
      "GET /schools/mine": respond(200, [SCHOOL]),
      "GET /schools/s1/learning": respond(200, NO_LEARNING),
      "GET /schools/s1/plan": respond(200, SCHOOL_PLAN),
      "GET /schools/s1/classes": respond(200, [
        { id: "k1", schoolId: "s1", name: "Grade 1A", teachers: [{ accountId: "t1", email: "active@example.com" }], archived: false },
        { id: "k2", schoolId: "s1", name: "Old class", teachers: [{ accountId: "t1", email: "active@example.com" }], archived: true },
      ]),
      "GET /schools/overview": respond(200, EMPTY_OVERVIEW),
      "GET /schools/s1/staff": respond(200, staff),
      "POST /schools/s1/staff": (_m, body) =>
        (body as { email: string }).email === "parent@example.com"
          ? respond(409, {
              status: 409,
              detail: "This email address can't be invited as a teacher. Ask them for a different address.",
            })
          : respond(201, {
              id: "m3",
              accountId: "t3",
              email: "new@example.com",
              status: "INVITED",
              invitedAt: "2026-09-26T00:00:00Z",
            }),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <PrincipalTeachersPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText("active@example.com")).toBeInTheDocument();
    expect(screen.getByText("Teaching here")).toBeInTheDocument();
    // Y40: which classes each teacher teaches - active ones only - each a way to its page.
    expect(await screen.findByRole("link", { name: "Grade 1A" })).toHaveAttribute("href", "/principal/classes/k1");
    expect(screen.queryByRole("link", { name: "Old class" })).not.toBeInTheDocument();
    expect(screen.queryByText("gone@example.com")).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Teacher's email"), "parent@example.com");
    await user.click(screen.getByRole("button", { name: "Invite" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("can't be invited as a teacher");

    await user.clear(screen.getByLabelText("Teacher's email"));
    await user.type(screen.getByLabelText("Teacher's email"), "new@example.com");
    await user.click(screen.getByRole("button", { name: "Invite" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Invitation sent to new@example.com.");
  });

  it("shows the school's plan and what is in use, and the server's reason when a limit is reached", async () => {
    server({
      "GET /schools/mine": respond(200, [SCHOOL]),
      "GET /schools/s1/learning": respond(200, NO_LEARNING),
      "GET /schools/s1/plan": respond(
        200,
        schoolPlan(
          [
            { key: "SCHOOL_CHILDREN_MAX", limit: 40, used: 12 },
            { key: "SCHOOL_TEACHERS_MAX", limit: 5, used: 1 },
            { key: "SCHOOL_CLASSES_MAX", limit: 3, used: 3 },
            { key: "TEACHER_STORAGE_MB", limit: 1024, used: null },
          ],
          false,
        ),
      ),
      "GET /schools/s1/classes": respond(200, []),
      "GET /schools/overview": respond(200, EMPTY_OVERVIEW),
      "GET /schools/s1/staff": respond(200, []),
      "POST /schools/s1/classes": respond(409, {
        status: 409,
        title: "Plan Limit Reached",
        detail: "Your school's plan includes up to 3 classes in use.",
        entitlement: "SCHOOL_CLASSES_MAX",
        limit: 3,
      }),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <PrincipalPlanPage />
        <PrincipalClassesPage />
      </MemoryRouter>,
    );

    const plan = await screen.findByRole("region", { name: "Your school's plan" });
    expect(await within(plan).findByText("School early access")).toBeInTheDocument();
    expect(within(plan).getByText(/Paid until .*The school's services are on/)).toBeInTheDocument();
    expect(within(plan).getByRole("meter", { name: "Classes in use" })).toHaveAttribute("aria-valuenow", "3");
    expect(within(plan).getByText("3 of 3")).toBeInTheDocument();
    expect(within(plan).getByText("Full: new ones wait until a place is free.")).toBeInTheDocument();
    expect(within(plan).getByText("1024 MB for each teacher")).toBeInTheDocument();

    await user.type(screen.getByLabelText("New class"), "Grade 4");
    await user.click(screen.getByRole("button", { name: "Add class" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Your school's plan includes up to 3 classes in use.");
  });

  it("shows an unpaid school's way to pay instead of its classes, and reads nothing of them (Y44)", async () => {
    const calls = server({
      "GET /schools/mine": respond(200, [SCHOOL]),
      "GET /schools/s1/plan": respond(200, schoolPlan(SCHOOL_PLAN.entitlements, false, false)),
      "GET /schools/s1/payment-plans": respond(200, [
        { id: "yp", code: "SCHOOL_YEAR", name: "School year", description: null, amountMinor: 12000, currency: "USD", billingInterval: "YEAR" },
      ]),
      "GET /payments/methods": respond(200, { methods: ["MANUAL"] }),
      "GET /schools/s1/payments": respond(200, []),
      "POST /schools/s1/payments": respond(201, {}),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <PrincipalClassesPage />
      </MemoryRouter>,
    );

    expect(await screen.findByRole("heading", { name: "Sunrise Primary's subscription needs payment" })).toBeInTheDocument();
    expect(screen.getByText(/Nothing is deleted, and families' own learning carries on/)).toBeInTheDocument();
    expect(calls.some((c) => c.path === "/schools/s1/classes")).toBe(false);

    await user.click(await screen.findByRole("button", { name: /Renew School year/ }));
    const started = calls.find((c) => c.method === "POST" && c.path === "/schools/s1/payments");
    expect(started?.body).toMatchObject({ planId: "yp", method: "MANUAL" });
    expect(Object.keys(started?.body as object).sort()).toEqual(["idempotencyKey", "method", "planId"]);
    expect(await screen.findByRole("status")).toHaveTextContent(/The payment has started/);
  });

  it("lets a new school choose its first plan from those offered, each with its price and limits, and pay for it (M4)", async () => {
    const calls = server({
      "GET /schools/mine": respond(200, [SCHOOL]),
      "GET /schools/s1/plan": respond(200, schoolPlan(SCHOOL_PLAN.entitlements, true, false)),
      "GET /schools/s1/payment-plans": respond(200, [
        {
          id: "small",
          code: "SCHOOL_SMALL",
          name: "Small school",
          description: "For one or two classes",
          amountMinor: 9000,
          currency: "USD",
          billingInterval: "YEAR",
          limits: { SCHOOL_CLASSES_MAX: 2, SCHOOL_TEACHERS_MAX: 2, SCHOOL_CHILDREN_MAX: 60, TEACHER_STORAGE_MB: 512 },
        },
        {
          id: "whole",
          code: "SCHOOL_WHOLE",
          name: "Whole school",
          description: null,
          amountMinor: 30000,
          currency: "USD",
          billingInterval: "YEAR",
          limits: { SCHOOL_CLASSES_MAX: null, SCHOOL_TEACHERS_MAX: 20, SCHOOL_CHILDREN_MAX: 600, TEACHER_STORAGE_MB: null },
        },
      ]),
      "GET /payments/methods": respond(200, { methods: ["MANUAL"] }),
      "GET /schools/s1/payments": respond(200, []),
      "POST /schools/s1/payments": respond(201, {}),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <PrincipalHome />
      </MemoryRouter>,
    );

    expect(await screen.findByRole("heading", { name: "Choose a plan to open Sunrise Primary" })).toBeInTheDocument();
    expect(await screen.findByText("Choose the school's plan")).toBeInTheDocument();
    const small = screen.getByRole("radio", { name: /Small school/ });
    expect(small).toBeChecked();
    expect(screen.getByText("For one or two classes")).toBeInTheDocument();
    expect(screen.getByText("Storage: 512 MB for each teacher")).toBeInTheDocument();
    expect(screen.getByText("Storage: no limit for each teacher")).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: /Whole school/ }));
    await user.click(screen.getByRole("button", { name: /Pay for Whole school/ }));
    const started = calls.find((c) => c.method === "POST" && c.path === "/schools/s1/payments");
    expect(started?.body).toMatchObject({ planId: "whole", method: "MANUAL" });
    expect(await screen.findByRole("status")).toHaveTextContent(/The payment has started/);
  });

  it("says to ask the Bookeyond team when no plan is offered to schools", async () => {
    server({
      "GET /schools/mine": respond(200, [SCHOOL]),
      "GET /schools/s1/plan": respond(200, schoolPlan(SCHOOL_PLAN.entitlements, true, false)),
      "GET /schools/s1/payment-plans": respond(200, []),
      "GET /payments/methods": respond(200, { methods: ["MANUAL"] }),
      "GET /schools/s1/payments": respond(200, []),
    });
    render(
      <MemoryRouter>
        <PrincipalHome />
      </MemoryRouter>,
    );
    expect(await screen.findByText("To start a plan, ask the Bookeyond team for an invoice.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Pay for/ })).not.toBeInTheDocument();
  });

  it("confirms before removing a teacher", async () => {
    const calls = server({
      "GET /schools/mine": respond(200, [SCHOOL]),
      "GET /schools/s1/learning": respond(200, NO_LEARNING),
      "GET /schools/s1/plan": respond(200, SCHOOL_PLAN),
      "GET /schools/s1/classes": respond(200, []),
      "GET /schools/overview": respond(200, EMPTY_OVERVIEW),
      "GET /schools/s1/staff": respond(200, [
        {
          id: "m1",
          accountId: "t1",
          email: "active@example.com",
          status: "ACTIVE",
          invitedAt: "2026-09-01T00:00:00Z",
          respondedAt: null,
        },
      ]),
      "DELETE /schools/s1/staff/m1": respond(204),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <PrincipalTeachersPage />
      </MemoryRouter>,
    );

    await user.click(await screen.findByRole("button", { name: "Remove active@example.com" }));
    expect(calls.some((c) => c.method === "DELETE")).toBe(false);
    const confirm = screen.getByRole("group", { name: "Confirm removing active@example.com" });
    expect(confirm).toHaveTextContent("Remove from the school and all its classes?");
    await user.click(within(confirm).getByRole("button", { name: "Yes" }));
    expect(calls.some((c) => c.method === "DELETE" && c.path === "/schools/s1/staff/m1")).toBe(true);
  });
});

describe("PrincipalClassPage", () => {
  it("offers only accepted teachers who aren't already assigned, and explains how children join", async () => {
    const calls = server({
      "GET /schools/classes/c1": respond(200, {
        id: "c1",
        schoolId: "s1",
        name: "Grade 1A",
        teachers: [{ accountId: "t1", email: "a@example.com" }],
      }),
      "GET /schools/s1/staff": respond(200, [
        {
          id: "m1",
          accountId: "t1",
          email: "a@example.com",
          status: "ACTIVE",
          invitedAt: "2026-09-01T00:00:00Z",
          respondedAt: null,
        },
        {
          id: "m2",
          accountId: "t2",
          email: "b@example.com",
          status: "ACTIVE",
          invitedAt: "2026-09-01T00:00:00Z",
          respondedAt: null,
        },
        {
          id: "m3",
          accountId: "t3",
          email: "c@example.com",
          status: "INVITED",
          invitedAt: "2026-09-01T00:00:00Z",
          respondedAt: null,
        },
      ]),
      "GET /schools/classes/c1/learning": respond(200, { curricula: [], members: [], skills: [] }),
      "GET /schools/classes/c1/curricula": respond(200, []),
      "GET /schools/curricula/assignable": respond(200, []),
      "GET /schools/classes/c1/join-code": respond(204),
      "POST /schools/classes/c1/teachers": respond(201, {}),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/principal/classes/c1"]}>
        <Routes>
          <Route path="/principal/classes/:classId" element={<PrincipalClassPage />} />
        </Routes>
      </MemoryRouter>,
    );

    const select = await screen.findByLabelText("Assign a teacher");
    const options = within(select)
      .getAllByRole("option")
      .map((o) => o.textContent);
    expect(options).toEqual(["Choose one of your teachers…", "b@example.com"]);
    // Children join with the class code, by their family's choice (PD-01): the empty class says how.
    expect(await screen.findByText(/Share the class code with families/)).toBeInTheDocument();
    expect(await screen.findByText(/No code yet/)).toBeInTheDocument();

    await user.selectOptions(select, "t2");
    await user.click(screen.getByRole("button", { name: "Assign" }));
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ teacherAccountId: "t2" });
  });
});

describe("Class lifecycle (X17)", () => {
  it("archives a class only after saying what it means, then shows it archived without its code or new assignments", async () => {
    let archived = false;
    const calls = server({
      "GET /schools/classes/c1": () =>
        respond(200, {
          id: "c1",
          schoolId: "s1",
          name: "Grade 1A",
          teachers: [{ accountId: "t1", email: "a@example.com" }],
          archived,
        }),
      "GET /schools/s1/staff": respond(200, [
        {
          id: "m2",
          accountId: "t2",
          email: "b@example.com",
          status: "ACTIVE",
          invitedAt: "2026-09-01T00:00:00Z",
          respondedAt: null,
        },
      ]),
      "GET /schools/classes/c1/learning": respond(200, { curricula: [], members: [], skills: [] }),
      "GET /schools/classes/c1/curricula": respond(200, []),
      "GET /schools/curricula/assignable": respond(200, [
        { id: "cu9", name: "Reading", subjectName: "Reading", ageHubName: "Ages 5-6", own: false },
      ]),
      "GET /schools/classes/c1/join-code": respond(204),
      "POST /schools/classes/c1/archive": () => {
        archived = true;
        return respond(200, { id: "c1", schoolId: "s1", name: "Grade 1A", archived: true });
      },
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/principal/classes/c1"]}>
        <Routes>
          <Route path="/principal/classes/:classId" element={<PrincipalClassPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByLabelText("Assign a curriculum")).toBeInTheDocument();
    await user.click(await screen.findByRole("button", { name: /Manage class/ }));
    const dialog = screen.getByRole("dialog", { name: "Manage class" });
    expect(within(dialog).getByText(/Its code stops working/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: /Archive…/ }));
    expect(calls.some((c) => c.path.endsWith("/archive"))).toBe(false);
    await user.click(within(dialog).getByRole("button", { name: "Archive the class" }));
    expect(calls.filter((c) => c.method === "POST" && c.path === "/schools/classes/c1/archive")).toHaveLength(1);

    expect(await screen.findByText(/This class is archived\./)).toBeInTheDocument();
    expect(screen.queryByLabelText("Assign a teacher")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Assign a curriculum")).not.toBeInTheDocument();
    expect(screen.queryByText(/No code yet/)).not.toBeInTheDocument();
  });

  it("keeps archived classes apart on the school's page, saying what they are", async () => {
    server({
      "GET /schools/mine": respond(200, [SCHOOL]),
      "GET /schools/s1/learning": respond(200, NO_LEARNING),
      "GET /schools/s1/plan": respond(200, SCHOOL_PLAN),
      "GET /schools/s1/classes": respond(200, [
        { id: "c1", schoolId: "s1", name: "Grade 1A", teachers: [], archived: false },
        { id: "c9", schoolId: "s1", name: "Last year", teachers: [], archived: true },
      ]),
      "GET /schools/s1/staff": respond(200, []),
      "GET /schools/overview": respond(200, {
        ...EMPTY_OVERVIEW,
        classes: [classOverview({ archived: false }), classOverview({ classId: "c9", className: "Last year", archived: true })],
      }),
    });
    render(
      <MemoryRouter>
        <PrincipalHome />
        <PrincipalClassesPage />
      </MemoryRouter>,
    );
    const old = await screen.findByRole("link", { name: /Last year/ });
    expect(old.closest("details")).not.toBeNull();
    expect(old).toHaveTextContent("Archived: no one can join; its children keep their learning.");
    expect(screen.getByText("Archived classes (1)")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Grade 1A/ }).closest("details")).toBeNull();
    expect(screen.getByRole("button", { name: /Manage school/ })).toBeInTheDocument();
  });

  it("archives the whole school only after its consequences are stated, then says so and can restore it (Y17)", async () => {
    let archived = false;
    const calls = server({
      "GET /schools/mine": () => respond(200, [{ ...SCHOOL, archived }]),
      "GET /schools/s1/learning": respond(200, NO_LEARNING),
      "GET /schools/s1/plan": respond(200, SCHOOL_PLAN),
      "GET /schools/s1/classes": respond(200, []),
      "GET /schools/s1/staff": respond(200, []),
      "GET /schools/overview": respond(200, EMPTY_OVERVIEW),
      "POST /schools/s1/archive": () => {
        archived = true;
        return respond(200, { ...SCHOOL, archived: true });
      },
      "POST /schools/s1/restore": () => {
        archived = false;
        return respond(200, SCHOOL);
      },
    });
    render(
      <MemoryRouter>
        <PrincipalHome />
      </MemoryRouter>,
    );
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: /Manage school/ }));
    let dialog = screen.getByRole("dialog", { name: "Manage school" });
    expect(within(dialog).getByText(/Families keep the access they gave/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: /Archive…/ }));
    expect(calls.some((c) => c.path.endsWith("/archive"))).toBe(false);
    await user.click(within(dialog).getByRole("button", { name: "Archive the school" }));
    expect(calls.filter((c) => c.method === "POST" && c.path === "/schools/s1/archive")).toHaveLength(1);
    expect(await screen.findByText(/This school is archived\./)).toBeInTheDocument();

    dialog = screen.getByRole("dialog", { name: "Manage school" });
    await user.click(await within(dialog).findByRole("button", { name: /Restore the school/ }));
    expect(calls.filter((c) => c.method === "POST" && c.path === "/schools/s1/restore")).toHaveLength(1);
    await screen.findByText("Restored.");
    expect(screen.queryByText(/This school is archived\./)).not.toBeInTheDocument();
  });
});

describe("Staff homes (X7)", () => {
  it("shows a principal who the school reaches, what is left to set up, and each class's attention", async () => {
    server({
      "GET /schools/mine": respond(200, [SCHOOL]),
      "GET /schools/s1/learning": respond(200, NO_LEARNING),
      "GET /schools/s1/plan": respond(200, SCHOOL_PLAN),
      "GET /schools/s1/classes": respond(200, [
        { id: "c1", schoolId: "s1", name: "Grade 1A", teachers: [{ accountId: "t1", email: "a@example.com" }] },
        { id: "c2", schoolId: "s1", name: "Grade 2B", teachers: [] },
      ]),
      "GET /schools/s1/staff": respond(200, []),
      "GET /schools/overview": respond(200, {
        schools: [
          {
            schoolId: "s1",
            name: "Sunrise Primary",
            childrenWithAccess: 12,
            classes: 2,
            classesWithoutTeacher: 1,
            classesWithoutCurriculum: 1,
            classesWithoutOpenCode: 1,
          },
        ],
        classes: [
          classOverview({ attention: { childrenNeedingPractice: 3, skillsNeedingPractice: 2, childrenNotStarted: 1 } }),
          classOverview({
            classId: "c2",
            className: "Grade 2B",
            children: 0,
            curricula: [],
            teachers: 0,
            joinCode: { open: false, expiresAt: null },
          }),
        ],
      }),
    });
    render(
      <MemoryRouter>
        <PrincipalHome />
        <PrincipalClassesPage />
      </MemoryRouter>,
    );

    const glance = await screen.findByRole("region", { name: "At a glance" });
    expect(within(glance).getByText("children with access")).toBeInTheDocument();
    expect(within(glance).getByText("1 class needs a teacher")).toBeInTheDocument();
    expect(within(glance).getByText("1 class has nothing to learn assigned")).toBeInTheDocument();
    expect(within(glance).getByText("1 class has no working class code")).toBeInTheDocument();

    const first = screen.getByRole("link", { name: /Grade 1A/ });
    expect(first).toHaveAttribute("href", "/principal/classes/c1");
    expect(first).toHaveTextContent("12 children · Maths - Junior · a@example.com");
    expect(first).toHaveTextContent("3 children need practice on 2 skills");
    expect(first).toHaveTextContent("1 child hasn't started yet");
    const second = screen.getByRole("link", { name: /Grade 2B/ });
    expect(second).toHaveTextContent("No teacher yet");
    expect(second).toHaveTextContent("Nothing assigned yet: choose what the class learns.");
  });

  it("gives a teacher their classes with a plain line when no skill needs practice", async () => {
    server({
      "GET /schools/invitations/mine": respond(200, []),
      "GET /schools/overview": respond(200, {
        schools: [],
        classes: [classOverview({}), classOverview({ classId: "c3", className: "Grade 3", children: 0 })],
      }),
    });
    const { TeacherHome } = await import("@/features/teacher/TeacherHome");
    render(
      <MemoryRouter>
        <TeacherHome />
      </MemoryRouter>,
    );

    const first = await screen.findByRole("link", { name: /Grade 1A/ });
    expect(first).toHaveAttribute("href", "/teacher/classes/c1");
    expect(first).toHaveTextContent("Sunrise Primary");
    expect(first).toHaveTextContent("No skill needs practice right now");
    expect(first).not.toHaveTextContent("teacher");
    expect(screen.getByRole("link", { name: /Grade 3/ })).toHaveTextContent(/No children yet\. The class code works until/);
  });

  it("lists a teacher's schools and leaves one only after saying what that means (Y21)", async () => {
    let left = false;
    const calls = server({
      "GET /schools/invitations/mine": respond(200, []),
      "GET /schools/overview": respond(200, { schools: [], classes: [classOverview({})] }),
      "GET /schools/staff/mine": () =>
        respond(200, left ? [] : [{ id: "m1", schoolId: "s1", schoolName: "Sunrise Primary", joinedAt: "2026-09-26T00:00:00Z" }]),
      "POST /schools/staff/mine/m1/leave": () => {
        left = true;
        return respond(204);
      },
    });
    const { TeacherHome } = await import("@/features/teacher/TeacherHome");
    render(
      <MemoryRouter>
        <TeacherHome />
      </MemoryRouter>,
    );
    const user = userEvent.setup();

    const schools = await screen.findByRole("region", { name: "Your schools" });
    await user.click(within(schools).getByRole("button", { name: /Leave… Sunrise Primary/ }));
    expect(calls.some((c) => c.path.endsWith("/leave"))).toBe(false);
    expect(within(schools).getByRole("group", { name: "Leave Sunrise Primary" })).toHaveTextContent(
      /Nothing the children have changes/,
    );
    await user.click(within(schools).getByRole("button", { name: "Leave the school" }));

    expect(await screen.findByText("You have left Sunrise Primary.")).toBeInTheDocument();
    expect(calls.filter((c) => c.method === "POST" && c.path === "/schools/staff/mine/m1/leave")).toHaveLength(1);
    expect(calls.filter((c) => c.path === "/schools/overview").length).toBeGreaterThan(1);
  });
});

describe("SchoolInvitations", () => {
  it("lets a teacher accept an invitation", async () => {
    let answered = false;
    const calls = server({
      "GET /schools/invitations/mine": () =>
        respond(
          200,
          answered ? [] : [{ id: "i1", schoolId: "s1", schoolName: "Sunrise Primary", invitedAt: "2026-09-26T00:00:00Z" }],
        ),
      "POST /schools/invitations/i1/accept": () => {
        answered = true;
        return respond(204);
      },
    });
    const onAccepted = vi.fn();
    const user = userEvent.setup();
    render(<SchoolInvitations onAccepted={onAccepted} />);

    expect(await screen.findByText("Sunrise Primary")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Accept" }));

    expect(await screen.findByRole("status")).toHaveTextContent("You've joined Sunrise Primary.");
    expect(onAccepted).toHaveBeenCalled();
    expect(calls.some((c) => c.method === "POST" && c.path === "/schools/invitations/i1/accept")).toBe(true);
    expect(screen.queryByRole("button", { name: "Accept" })).not.toBeInTheDocument();
  });

  it("says why an archived school's invitation can't be accepted (Y17)", async () => {
    const detail = "This school is archived: no one can join it until its principal restores it.";
    server({
      "GET /schools/invitations/mine": respond(200, [
        { id: "i1", schoolId: "s1", schoolName: "Sunrise Primary", invitedAt: "2026-09-26T00:00:00Z" },
      ]),
      "POST /schools/invitations/i1/accept": respond(409, { status: 409, detail }),
    });
    const user = userEvent.setup();
    render(<SchoolInvitations onAccepted={vi.fn()} />);

    await user.click(await screen.findByRole("button", { name: "Accept" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(detail);
    expect(screen.getByRole("button", { name: "Decline" })).toBeEnabled();
  });
});
