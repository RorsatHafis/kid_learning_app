import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { Branches } from "./Branches";

vi.mock("@/lib/auth/AuthContext", () => ({ useAuth: () => ({ session: { accountId: "owner" } }) }));

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

function server(routes: Record<string, unknown>) {
  const calls: string[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const key = `${init?.method ?? "GET"} ${String(url).replace(/^.*\/api\/v1/, "").replace(/\?.*$/, "")}`;
      calls.push(key);
      return key in routes ? respond(200, routes[key]) : respond(404, { status: 404, title: "Not Found", detail: key });
    }),
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

const branches = [
  { id: "north", name: "North", principalAccountId: "owner", archived: false },
  { id: "south", name: "South", principalAccountId: "someone", archived: false },
];

const OVERVIEW = {
  organizationId: "org",
  name: "Lotus Group",
  branchesInUse: 2,
  branchesPaid: 1,
  children: 40,
  teachers: 5,
  classes: 4,
  branches: [
    { schoolId: "north", name: "North", archived: false, runByOwner: true, classes: 3, teachers: 4, children: 31,
      paid: true, paidUntil: "2027-03-01T00:00:00Z", paymentPending: false, currentYear: "2026-2027" },
    { schoolId: "south", name: "South", archived: false, runByOwner: false, classes: 1, teachers: 1, children: 10,
      paid: false, paidUntil: null, paymentPending: true, currentYear: null },
  ],
};

describe("Branches (M12 B2: the owner's overview)", () => {
  it("shows the owner each branch's size, year and payment, and totals counting a child once", async () => {
    server({
      "GET /organizations/mine": [{ id: "org", name: "Lotus Group", owner: true, branches }],
      "GET /organizations/org/overview": OVERVIEW,
    });
    render(
      <MemoryRouter>
        <Branches schoolId="north" onAdded={() => {}} />
      </MemoryRouter>,
    );

    expect(await screen.findByText("2 branches (1 paid) · 40 children · 5 teachers · 4 classes")).toBeInTheDocument();
    expect(screen.getByText("A child or teacher in two branches is counted once.")).toBeInTheDocument();
    const [north, south] = screen.getAllByRole("listitem").filter((li) => li.parentElement?.tagName === "UL" && li.querySelector("ul"));
    expect(within(north).getByText("3 classes · 4 teachers · 31 children")).toBeInTheDocument();
    expect(within(north).getByText("Year 2026-2027")).toBeInTheDocument();
    expect(within(north).getByText(/Paid until/)).toBeInTheDocument();
    expect(within(south).getByText("1 class · 1 teacher · 10 children")).toBeInTheDocument();
    expect(within(south).getByText("No current academic year")).toBeInTheDocument();
    expect(within(south).getByText("Payment waiting to be confirmed")).toBeInTheDocument();
  });

  it("asks for no overview when the account runs a branch without owning the organization", async () => {
    const calls = server({ "GET /organizations/mine": [{ id: "org", name: "Lotus Group", owner: false, branches: [branches[1]] }] });
    render(
      <MemoryRouter>
        <Branches schoolId="south" onAdded={() => {}} />
      </MemoryRouter>,
    );

    expect(await screen.findByText("This is your school's only branch.")).toBeInTheDocument();
    expect(calls).not.toContain("GET /organizations/org/overview");
    expect(screen.queryByText(/children/)).not.toBeInTheDocument();
  });
});
