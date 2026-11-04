/**
 * When a family's payments (or the ways of paying) can't be read, the screen says so and offers no new payment: a
 * payment already waiting, with the reference the family quotes, must never look absent. Trying again shows it.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import type { OwnFamilyPlan, PlanSummary } from "@/lib/api/subscriptions";
import type { PaymentOrder } from "@/lib/api/payments";

const api = vi.hoisted(() => ({ plan: vi.fn(), methods: vi.fn(), familyOrders: vi.fn(), order: vi.fn() }));

vi.mock("@/lib/api/subscriptions", async (original) => ({
  ...(await original<typeof import("@/lib/api/subscriptions")>()),
  familyPlanApi: { get: api.plan, request: vi.fn(), withdraw: vi.fn() },
}));
vi.mock("@/lib/api/payments", async (original) => ({
  ...(await original<typeof import("@/lib/api/payments")>()),
  paymentsApi: { methods: api.methods, familyOrders: api.familyOrders, order: api.order, start: vi.fn(), simulate: vi.fn(), cancel: vi.fn(), note: vi.fn() },
}));

import { FamilyPlanWithPayments } from "./FamilyPayments";

const summary = (over: Partial<PlanSummary>): PlanSummary => ({
  id: "p", code: "P", name: "Plan", description: null, audience: "FAMILY", status: "ACTIVE", isDefault: false, selfService: true, price: null,
  ...over,
});

const family: OwnFamilyPlan = {
  plan: summary({ id: "early", name: "Early access", isDefault: true, selfService: false }),
  onDefaultPlan: true,
  subscription: null,
  entitlements: [{ key: "CHILDREN_MAX", limit: 2, used: 1 }],
  afterwards: null,
  choices: [
    {
      plan: summary({ id: "plus", name: "Family Plus", price: { amountMinor: 500, currency: "USD", interval: "MONTH" } }),
      entitlements: [{ key: "CHILDREN_MAX", limit: 5, used: null }],
    },
  ],
  request: null,
};

const pending: PaymentOrder = {
  id: "o1", reference: "BKTEST000001", customerType: "FAMILY", schoolId: null, planCode: "PLUS", planName: "Family Plus",
  amountMinor: 500, currency: "USD", billingInterval: "MONTH", limits: { CHILDREN_MAX: 5 }, method: "MANUAL", status: "PENDING",
  createdAt: "2026-10-20T03:00:00Z", expiresAt: "2026-10-20T03:30:00Z", completedAt: null, periodStart: null, periodEnd: null,
  failureReason: null, customerNote: null, checkout: { instructions: "TEST INSTRUCTIONS", action: null, fields: null }, admin: null,
};

describe("the family's payments when they can't be read", () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset());
    api.plan.mockResolvedValue(family);
    api.methods.mockResolvedValue({ methods: ["MANUAL"] });
    api.order.mockResolvedValue(pending);
  });

  it("says so, offers no new payment, and shows the waiting payment once they load", async () => {
    api.familyOrders.mockRejectedValueOnce(new TypeError("Failed to fetch")).mockResolvedValue({ items: [pending], page: 0, size: 10, total: 1 });
    const user = userEvent.setup();
    render(<FamilyPlanWithPayments />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/didn.t load|went wrong/i);
    expect(await screen.findByRole("button", { name: /Choose Family Plus/ })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByRole("heading", { name: "Payment in progress" })).toBeInTheDocument();
    expect(screen.getByText("BKTEST000001")).toBeInTheDocument();
  });

  it("says so when the ways of paying can't be read, and offers no payment", async () => {
    api.methods.mockRejectedValue(new TypeError("Failed to fetch"));
    api.familyOrders.mockResolvedValue({ items: [], page: 0, size: 10, total: 0 });
    render(<FamilyPlanWithPayments />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/didn.t load|went wrong/i);
    expect(screen.queryByRole("button", { name: /Choose Family Plus/ })).toBeNull();
  });
});
