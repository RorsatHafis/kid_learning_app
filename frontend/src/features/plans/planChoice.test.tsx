/**
 * Owner requirement 2026-11-11: plans are chosen before registering. The plans page shows each audience's plans from
 * the records and leads to registration with only the plan's code; registration shows the chosen plan, sends the code
 * (never a price) and - for a paid family plan - goes to its checkout once the account exists.
 */
import { withLegal } from "@/test/withLegal";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "@/lib/auth/AuthContext";
import { RegisterPage } from "@/features/auth/RegisterPage";
import { RegisterContinuePage } from "@/features/auth/RegisterContinuePage";
import { PlansPage } from "./PlansPage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const PLANS = [
  { code: "FREE", audience: "FAMILY", name: "Free", description: null, isDefault: true, amountMinor: null, currency: null, billingInterval: null, limits: [{ key: "CHILDREN_MAX", limit: 1 }], subjects: ["MATH"], chooseable: true },
  { code: "PLUS", audience: "FAMILY", name: "Family Plus", description: "For bigger families.", isDefault: false, amountMinor: 700, currency: "USD", billingInterval: "MONTH", limits: [{ key: "CHILDREN_MAX", limit: 4 }], subjects: ["MATH", "KHMER"], chooseable: true },
  { code: "ASK", audience: "FAMILY", name: "Partner", description: null, isDefault: false, amountMinor: null, currency: null, billingInterval: null, limits: [], subjects: ["MATH"], chooseable: false },
  { code: "SCHOOL_YEAR", audience: "SCHOOL", name: "School year", description: null, isDefault: false, amountMinor: 12000, currency: "USD", billingInterval: "YEAR", limits: [], subjects: null, chooseable: true },
];
const SUBJECTS = [
  { code: "MATH", name: "Maths", description: null, available: true, minAge: 4, maxAge: 9 },
  { code: "KHMER", name: "Khmer", description: null, available: true, minAge: 4, maxAge: 9 },
];

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/plans" element={<PlansPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/register/continue" element={<RegisterContinuePage />} />
          <Route path="/parent" element={<p>parent home</p>} />
          <Route path="/parent/account" element={<p>family plan checkout</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  window.sessionStorage.clear();
  window.localStorage.clear();
});
afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState(null, "", "/");
});

describe("choosing a plan before registering", () => {
  it("shows each audience's plans as the records hold them and leads to registration with the plan's code", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) =>
        String(url).includes("/site/plans") ? respond(200, PLANS) : String(url).includes("/site/subjects") ? respond(200, SUBJECTS) : respond(200, []),
      ),
    );
    const user = userEvent.setup();
    renderAt("/plans");

    const plus = await screen.findByRole("article", { name: "Family Plus" });
    expect(within(plus).getByText(/\$7\.00/)).toBeInTheDocument();
    expect(within(plus).getByText("Every subject families can learn now")).toBeInTheDocument();
    expect(within(screen.getByRole("article", { name: "Free" })).getByText("Maths")).toBeInTheDocument();
    expect(within(plus).getByRole("link", { name: "Choose Family Plus" })).toHaveAttribute("href", "/register?plan=PLUS&as=parent");
    // A plan families can't choose themselves is asked for after registering.
    expect(within(screen.getByRole("article", { name: "Partner" })).queryByRole("link", { name: /Choose/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("article", { name: "School year" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "For a school" }));
    expect(within(await screen.findByRole("article", { name: "School year" })).getByRole("link", { name: "Choose School year" }))
      .toHaveAttribute("href", "/register?plan=SCHOOL_YEAR&as=principal");
  });

  it("registration shows the chosen plan and sends only its code", async () => {
    const sent: { url: string; body: Record<string, unknown> }[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        if (String(url).includes("/site/plans")) return respond(200, PLANS);
        sent.push({ url: String(url), body: init?.body ? JSON.parse(init.body as string) : {} });
        return respond(202);
      }),
    );
    const user = userEvent.setup();
    renderAt("/register?plan=PLUS&as=parent");

    const summary = await screen.findByRole("region", { name: "Your plan" });
    expect(within(summary).getByText(/Family Plus/)).toBeInTheDocument();
    expect(within(summary).getByText(/Nothing is charged now/)).toBeInTheDocument();
    // The plan settles the kind of account: no role choice.
    expect(screen.queryByRole("button", { name: "I'm a principal" })).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Email"), "parent@example.com");
    await user.click(screen.getByRole("button", { name: "Email me a link" }));
    expect(await screen.findByText("Check your email")).toBeInTheDocument();
    expect(sent.find((s) => s.url.includes("/auth/registration/start"))?.body).toEqual({ email: "parent@example.com", preferredLanguage: "en", planCode: "PLUS" });
  });

  it("a plan no longer on offer is said so, and the address can still be sent without it", async () => {
    vi.stubGlobal("fetch", vi.fn(async (url: string) => (String(url).includes("/site/plans") ? respond(200, PLANS) : respond(202))));
    renderAt("/register?plan=GONE&as=parent");
    expect(await screen.findByText(/That plan isn't offered any more/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "I'm a parent" })).toBeInTheDocument();
  });

  it("after registering with a paid family plan, the family goes to its checkout", async () => {
    const plan = { id: "p-plus", code: "PLUS", audience: "FAMILY", name: "Family Plus", description: null, isDefault: false, amountMinor: 700, currency: "USD", billingInterval: "MONTH", offered: true };
    vi.stubGlobal(
      "fetch",
      withLegal(async (url: string) => {
        if (String(url).includes("/auth/registration/verify")) {
          return respond(200, { registrationGrant: "g", email: "parent@example.com", grantExpiresAt: "2099-01-01T00:00:00Z", plan });
        }
        return respond(201, {
          accountId: "a1", email: "parent@example.com", platformRole: "PARENT", accessToken: "a", refreshToken: "r",
          expiresAt: "2099-01-01T00:00:00Z", refreshTokenExpiresAt: "2099-01-01T00:00:00Z", preferredLanguage: "en",
        });
      }),
    );
    window.history.replaceState(null, "", "/register/continue#token=t1");
    const user = userEvent.setup();
    renderAt("/register/continue");

    expect(await screen.findByRole("region", { name: "Your plan" })).toHaveTextContent("Family Plus");
    expect(screen.queryByRole("button", { name: "I'm a principal" })).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("Password", { exact: true }), "Parent-Pass-2026-ok");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("family plan checkout")).toBeInTheDocument();
  });
});
