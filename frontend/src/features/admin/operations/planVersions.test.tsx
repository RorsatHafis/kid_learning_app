import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { PlansPage } from "./PlansPage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

/** A fetch double keyed by "METHOD /path" (query string ignored); every call is recorded. */
function server(routes: Record<string, (body: unknown) => Response>) {
  const calls: { method: string; path: string; body: unknown }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      const path = String(url).replace(/^.*\/api\/v1/, "").replace(/\?.*$/, "");
      const body = init?.body ? JSON.parse(init.body as string) : undefined;
      calls.push({ method, path, body });
      const route = routes[`${method} ${path}`];
      return route ? route(body) : respond(404, { status: 404, title: "Not Found", detail: `no route ${method} ${path}` });
    }),
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

function plan(id: string, audience: "FAMILY" | "SCHOOL", over: Record<string, unknown> = {}, basedOn: unknown = null) {
  const keys = audience === "FAMILY" ? ["CHILDREN_MAX", "JOURNEYS_PER_CHILD_MAX"] : ["SCHOOL_CHILDREN_MAX", "SCHOOL_TEACHERS_MAX"];
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
      price: { amountMinor: 500, currency: "USD", interval: "MONTH" },
      ...over,
    },
    entitlements: keys.map((key) => ({ key, limit: 3, used: null })),
    subscribers: 4,
    basedOn,
  };
}

function counts(planId: string, name: string, audience: "FAMILY" | "SCHOOL", over: Record<string, unknown> = {}) {
  return {
    planId,
    code: planId.toUpperCase(),
    name,
    audience,
    status: "ACTIVE",
    isDefault: false,
    active: 0,
    trialing: 0,
    pastDue: 0,
    cancelling: 0,
    endingSoon: 0,
    ended: 0,
    ...over,
  };
}

const SUMMARY = {
  at: "2026-10-29T00:00:00Z",
  endingWithinDays: 30,
  plans: [
    counts("early", "Early access", "FAMILY", { isDefault: true }),
    counts("family", "Family 2026", "FAMILY", { active: 3, endingSoon: 1, ended: 2 }),
    counts("draft", "Unfinished", "FAMILY", { status: "DRAFT" }),
    counts("pilot", "School pilot", "SCHOOL", { active: 1, endingSoon: 1 }),
  ],
  familiesOnDefault: 12,
  schoolsWithoutPaidPlan: 2,
  schoolsEndingSoon: [{ schoolId: "s1", schoolName: "Sunrise Primary", planName: "School pilot", paidUntil: "2026-11-08T00:00:00Z" }],
};

const PLANS = [
  plan("early", "FAMILY", { isDefault: true, name: "Early access", price: null }),
  plan("family", "FAMILY", { name: "Family 2026" }),
  plan("pilot", "SCHOOL", { name: "School pilot" }),
];

describe("Plan versions and the subscriptions summary", () => {
  it("duplicates a plan into a new draft version and opens the copy", async () => {
    const copy = plan("family_2027", "FAMILY", { name: "Family 2027", status: "DRAFT" }, { id: "family", code: "FAMILY", name: "Family 2026" });
    let listed = PLANS;
    const calls = server({
      "GET /admin/plans": () => respond(200, listed),
      "GET /admin/plan-requests": () => respond(200, { items: [], page: 0, size: 100, total: 0 }),
      "GET /admin/subscriptions/summary": () => respond(200, SUMMARY),
      "POST /admin/plans/family/duplicate": () => {
        listed = [...PLANS, copy];
        return respond(201, copy);
      },
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <PlansPage />
      </MemoryRouter>,
    );

    const list = await screen.findByRole("list", { name: "Plans" });
    await user.click(within(list).getByText("Family 2026"));
    await user.click(screen.getByRole("button", { name: "Duplicate as a new version…" }));
    const form = screen.getByRole("form", { name: "A new version of Family 2026" });
    expect(within(form).getByText(/keeps it, its limits and the price they paid/)).toBeInTheDocument();
    const name = within(form).getByLabelText("Name of the new version");
    expect(name).toHaveValue("Family 2026");
    await user.clear(name);
    await user.type(name, "Family 2027");
    await user.type(within(form).getByLabelText(/Code of the new version/), "family_2027");
    await user.click(within(form).getByRole("button", { name: "Create the draft" }));

    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ code: "FAMILY_2027", name: "Family 2027" });
    // The copy opens as a draft, saying which plan it is a version of; the list shows where it came from.
    expect(await screen.findByText("A new version of Family 2026 (FAMILY)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save draft" })).toBeInTheDocument();
    expect(within(list).getByText(/from FAMILY/)).toBeInTheDocument();
  });

  it("counts families by plan and standing without listing them, and lists schools whose paid plan ends soon", async () => {
    server({
      "GET /admin/plans": () => respond(200, PLANS),
      "GET /admin/plan-requests": () => respond(200, { items: [], page: 0, size: 100, total: 0 }),
      "GET /admin/subscriptions/summary": () => respond(200, SUMMARY),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <PlansPage />
      </MemoryRouter>,
    );

    const table = await screen.findByRole("table", { name: /Subscriptions by plan and standing/ });
    const family = within(table).getByRole("row", { name: /Family 2026/ });
    expect(within(family).getAllByRole("cell").map((c) => c.textContent)).toEqual(["3", "1", "0", "0", "0", "2"]);
    expect(within(table).queryByText("Unfinished")).not.toBeInTheDocument();
    expect(within(table).queryByText("School pilot")).not.toBeInTheDocument();
    expect(screen.getByText("12 families are on Early access, the default, with no subscription in force.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "For schools" }));
    const schools = await screen.findByRole("table", { name: /Subscriptions by plan and standing/ });
    expect(within(schools).getByRole("row", { name: /School pilot/ })).toBeInTheDocument();
    expect(screen.getByText(/2 schools in use have no paid plan/)).toBeInTheDocument();
    const ending = screen.getByRole("list", { name: "Schools whose paid plan ends within 30 days" });
    expect(within(ending).getByRole("link", { name: "Sunrise Primary" })).toHaveAttribute("href", "/admin/schools/s1");
  });
});
