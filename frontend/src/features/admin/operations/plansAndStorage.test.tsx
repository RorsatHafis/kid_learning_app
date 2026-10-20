import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { PlansPage } from "./PlansPage";
import { SchoolPlanPanel } from "./SchoolPlanPanel";
import { StoragePage } from "./StoragePage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

type Handler = (body: unknown) => Response;

/** A fetch double keyed by "METHOD /path" (query string ignored); every call is recorded. */
function server(routes: Record<string, Handler | Response>) {
  const calls: { method: string; path: string; body: unknown }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      const path = String(url).replace(/^.*\/api\/v1/, "").replace(/\?.*$/, "");
      const body = init?.body ? JSON.parse(init.body as string) : undefined;
      calls.push({ method, path, body });
      const route = routes[`${method} ${path}`];
      if (!route) return respond(404, { status: 404, title: "Not Found", detail: `no route ${method} ${path}` });
      return typeof route === "function" ? route(body) : route.clone();
    }),
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

const NO_HISTORY = { entries: [], nextBefore: null, nextBeforeId: null };

function plan(id: string, audience: "FAMILY" | "SCHOOL", over: Record<string, unknown> = {}) {
  const keys =
    audience === "FAMILY"
      ? ["CHILDREN_MAX", "JOURNEYS_PER_CHILD_MAX"]
      : ["SCHOOL_CHILDREN_MAX", "SCHOOL_TEACHERS_MAX", "SCHOOL_CLASSES_MAX", "TEACHER_STORAGE_MB"];
  return {
    plan: {
      id,
      code: id.toUpperCase(),
      name: `${id} plan`,
      description: null,
      audience,
      status: "ACTIVE",
      isDefault: false,
      selfService: false,
      price: null,
      ...over,
    },
    entitlements: keys.map((key) => ({ key, limit: key === "TEACHER_STORAGE_MB" ? 1024 : 5, used: null })),
    subscribers: 2,
  };
}

const PLANS = [
  plan("early", "FAMILY", { isDefault: true, name: "Early access" }),
  plan("family", "FAMILY", { name: "Family plus" }),
  plan("schooldefault", "SCHOOL", { isDefault: true, name: "School early access" }),
  plan("pilot", "SCHOOL", { name: "School pilot" }),
];

describe("PlansPage", () => {
  it("shows family and school plans apart, and creates a plan for the audience on screen", async () => {
    const calls = server({
      "GET /admin/plans": respond(200, PLANS),
      "GET /admin/plan-requests": respond(200, { items: [], page: 0, size: 100, total: 0 }),
      "POST /admin/plans": respond(201, plan("new", "SCHOOL", { status: "DRAFT", name: "Big school" })),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <PlansPage />
      </MemoryRouter>,
    );

    const list = await screen.findByRole("list", { name: "Plans" });
    expect(within(list).getByText("Family plus")).toBeInTheDocument();
    expect(within(list).queryByText("School pilot")).not.toBeInTheDocument();
    expect(within(list).getAllByText("2 families")).toHaveLength(2);

    await user.click(screen.getByRole("button", { name: "For schools" }));
    expect(screen.getByRole("button", { name: "For schools" })).toHaveAttribute("aria-pressed", "true");
    expect(within(list).getByText("School pilot")).toBeInTheDocument();
    expect(within(list).queryByText("Family plus")).not.toBeInTheDocument();
    expect(within(list).getAllByText("Storage: 1024 MB")).toHaveLength(2);
    expect(within(list).getAllByText("2 schools")).toHaveLength(2);
    // Families are found and given plans only under family plans.
    expect(screen.queryByLabelText("Parent's email")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "New plan" }));
    expect(screen.getByRole("heading", { name: "New school plan" })).toBeInTheDocument();
    await user.type(screen.getByLabelText("Name schools will see"), "Big school");
    await user.type(screen.getByLabelText("Code (capital letters, numbers and _)"), "big_school");
    await user.click(screen.getByRole("button", { name: "Create draft plan" }));
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ code: "BIG_SCHOOL", name: "Big school", audience: "SCHOOL" });
  });
});

const ADMIN_VIEW = {
  schoolId: "s1",
  schoolName: "Sunrise Primary",
  plan: PLANS[2].plan,
  onDefaultPlan: true,
  subscription: null,
  entitlements: [
    { key: "SCHOOL_CHILDREN_MAX", limit: null, used: 14 },
    { key: "SCHOOL_TEACHERS_MAX", limit: null, used: 2 },
    { key: "SCHOOL_CLASSES_MAX", limit: null, used: 3 },
    { key: "TEACHER_STORAGE_MB", limit: 1024, used: null },
  ],
  access: { paid: false, paidUntil: null, paymentPending: false },
};

describe("SchoolPlanPanel", () => {
  it("says the school isn't paid, offers no plan without payment, and invoices a priced school plan (Y44)", async () => {
    const priced = plan("year", "SCHOOL", { name: "School year", price: { amountMinor: 12000, currency: "USD", interval: "YEAR" } });
    const calls = server({
      "GET /admin/schools/s1/subscription": respond(200, ADMIN_VIEW),
      "GET /admin/plans": respond(200, [...PLANS, priced]),
      "POST /admin/schools/s1/invoices": respond(201, { id: "o1", reference: "BKINVOICE0001" }),
      "GET /admin/audit-events": respond(200, NO_HISTORY),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <SchoolPlanPanel schoolId="s1" />
      </MemoryRouter>,
    );

    expect(await screen.findByText(/This school hasn't paid for a plan yet/)).toBeInTheDocument();
    // No way to give a plan without payment: the only choice is an invoice at a priced school plan.
    expect(screen.queryByRole("button", { name: /Give the plan/ })).not.toBeInTheDocument();
    const choice = await screen.findByRole("combobox", { name: "School plan" });
    expect(within(choice).getAllByRole("option").map((o) => o.textContent)).toEqual(["School year · $120.00 / year"]);
    await user.click(screen.getByRole("button", { name: "Create invoice" }));
    const invoiced = calls.find((c) => c.method === "POST");
    expect(invoiced?.path).toBe("/admin/schools/s1/invoices");
    expect(invoiced?.body).toMatchObject({ planId: "year" });
    expect(await screen.findByRole("status")).toHaveTextContent("Invoice BKINVOICE0001 created");
  });
});

const MB = 1024 * 1024;

const OVERVIEW = {
  storedBytes: 900 * MB,
  platformBytes: 300 * MB,
  platformFiles: 40,
  authorBytes: 650 * MB,
  owners: [
    {
      accountId: "t1",
      email: "full@example.com",
      role: "TEACHER",
      files: 120,
      usedBytes: 950 * MB,
      allowance: { limitMb: 1024, limitBytes: 1024 * MB, source: "SCHOOL_PLAN" },
      state: "NEARLY_FULL",
    },
    {
      accountId: "t2",
      email: "light@example.com",
      role: "TEACHER",
      files: 3,
      usedBytes: 2 * MB,
      allowance: { limitMb: 1024, limitBytes: 1024 * MB, source: "DEFAULT_PLAN" },
      state: "OK",
    },
  ],
};

describe("StoragePage", () => {
  it("shows the totals and who is nearly full, and sets an exception only after confirming it with a reason", async () => {
    const calls = server({
      "GET /admin/storage": respond(200, OVERVIEW),
      "GET /admin/accounts/t1/storage": respond(200, {
        usedBytes: 950 * MB,
        allowance: OVERVIEW.owners[0].allowance,
        remainingBytes: 74 * MB,
        state: "NEARLY_FULL",
        slices: [],
        largest: [{ id: "a1", title: "Song of the river", type: "AUDIO", sizeBytes: 4 * MB, archived: true }],
      }),
      "PUT /admin/accounts/t1/storage-allowance": respond(200, {
        usedBytes: 950 * MB,
        allowance: { limitMb: 2048, limitBytes: 2048 * MB, source: "OVERRIDE" },
        remainingBytes: 1098 * MB,
        state: "OK",
        slices: [],
        largest: [],
      }),
      "GET /admin/audit-events": respond(200, NO_HISTORY),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <StoragePage />
      </MemoryRouter>,
    );

    expect(await screen.findByText("900 MB")).toBeInTheDocument();
    expect(screen.getByText(/40 files · shared, counts against no one/)).toBeInTheDocument();
    const authors = screen.getByRole("list", { name: "Authors' media" });
    expect(within(authors).getByText("Nearly full")).toBeInTheDocument();
    expect(within(authors).getByText("950 MB of 1 GB")).toBeInTheDocument();
    expect(within(authors).getAllByRole("meter", { name: "Used" })[0]).toHaveAttribute("aria-valuenow", "93");

    await user.selectOptions(screen.getByLabelText("Show"), "attention");
    expect(within(screen.getByRole("list", { name: "Authors' media" })).queryByText("light@example.com")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Open full@example.com" }));
    expect(await screen.findByText("Song of the river")).toBeInTheDocument();
    const limit = screen.getByLabelText("Allowance in MB");
    await user.clear(limit);
    await user.type(limit, "2048");
    expect(screen.getByRole("button", { name: "Review…" })).toBeDisabled();
    await user.type(screen.getByLabelText("Reason (kept in the history)"), "Many recordings for the pilot");
    await user.click(screen.getByRole("button", { name: "Review…" }));
    expect(screen.getByText(/Set full@example.com's allowance to 2 GB\? .*nothing they keep is removed/)).toBeInTheDocument();
    expect(calls.some((c) => c.method === "PUT")).toBe(false);
    await user.click(screen.getByRole("button", { name: "Yes, set it" }));
    expect(calls.find((c) => c.method === "PUT")?.body).toEqual({ limitMb: 2048, note: "Many recordings for the pilot" });
    expect(await screen.findByRole("status")).toHaveTextContent("Saved.");
  });
});
