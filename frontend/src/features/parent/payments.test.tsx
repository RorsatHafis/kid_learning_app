/**
 * Paying for a family plan (Y43): only the plan and the way of paying are sent, the outcome is said plainly, and
 * the request flow stays when no payment method is on.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import type { OwnFamilyPlan, PlanSummary } from "@/lib/api/subscriptions";
import type { PaymentOrder } from "@/lib/api/payments";

const api = vi.hoisted(() => ({
  plan: vi.fn(),
  methods: vi.fn(),
  familyOrders: vi.fn(),
  start: vi.fn(),
  order: vi.fn(),
  simulate: vi.fn(),
  cancel: vi.fn(),
}));

vi.mock("@/lib/api/subscriptions", async (original) => ({
  ...(await original<typeof import("@/lib/api/subscriptions")>()),
  familyPlanApi: { get: api.plan, request: vi.fn(), withdraw: vi.fn() },
}));
vi.mock("@/lib/api/payments", async (original) => ({
  ...(await original<typeof import("@/lib/api/payments")>()),
  paymentsApi: {
    methods: api.methods,
    familyOrders: api.familyOrders,
    start: api.start,
    order: api.order,
    simulate: api.simulate,
    cancel: api.cancel,
    note: vi.fn(),
  },
}));

import { FamilyPlanWithPayments } from "./FamilyPayments";

const summary = (over: Partial<PlanSummary>): PlanSummary => ({
  id: "p",
  code: "P",
  name: "Plan",
  description: null,
  audience: "FAMILY",
  status: "ACTIVE",
  isDefault: false,
  selfService: true,
  price: null,
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
    { plan: summary({ id: "big", name: "Big Family" }), entitlements: [{ key: "CHILDREN_MAX", limit: 8, used: null }] },
  ],
  request: null,
};

const pending: PaymentOrder = {
  id: "o1",
  reference: "BKTEST000001",
  customerType: "FAMILY",
  schoolId: null,
  planCode: "PLUS",
  planName: "Family Plus",
  amountMinor: 500,
  currency: "USD",
  billingInterval: "MONTH",
  limits: { CHILDREN_MAX: 5 },
  method: "DEV_SIMULATOR",
  status: "PENDING",
  createdAt: "2026-10-20T03:00:00Z",
  expiresAt: "2026-10-20T03:30:00Z",
  completedAt: null,
  periodStart: null,
  periodEnd: null,
  failureReason: null,
  customerNote: null,
  checkout: { instructions: "TEST PAYMENT - a simulation.", action: null, fields: null },
  admin: null,
};

describe("paying for a family plan", () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset());
    api.plan.mockResolvedValue(family);
    api.methods.mockResolvedValue({ methods: ["DEV_SIMULATOR", "MANUAL"] });
  });

  it("offers a priced plan for payment, sends only the plan and the way of paying, and keeps asking for an unpriced one", async () => {
    api.familyOrders.mockResolvedValueOnce([]).mockResolvedValue([pending]);
    api.start.mockResolvedValue(pending);
    api.order.mockResolvedValue(pending);
    const user = userEvent.setup();
    render(<FamilyPlanWithPayments />);

    expect(await screen.findByRole("button", { name: /Choose Family Plus/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Ask for Big Family/ })).toBeInTheDocument();
    expect(screen.getByText(/pay for it here/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Choose Family Plus/ }));
    expect(screen.getByRole("heading", { name: "Pay for Family Plus" })).toHaveFocus();
    await user.click(screen.getByRole("radio", { name: /Bank transfer/ }));
    await user.click(screen.getByRole("radio", { name: /Test payment/ }));
    await user.click(screen.getByRole("button", { name: /Continue/ }));

    expect(api.start).toHaveBeenCalledTimes(1);
    const [planId, method, key, ...rest] = api.start.mock.calls[0];
    expect([planId, method, rest]).toEqual(["plus", "DEV_SIMULATOR", []]);
    expect(typeof key).toBe("string");

    expect(await screen.findByRole("heading", { name: "Payment in progress" })).toBeInTheDocument();
    expect(screen.getByText("BKTEST000001")).toBeInTheDocument();
    expect(screen.getByText("Test mode: no money moves")).toBeInTheDocument();
    // While one is in progress, another can't be started.
    expect(screen.getByRole("button", { name: /Choose Family Plus/ })).toBeDisabled();
  });

  it("says plainly when a payment went through, and when it didn't", async () => {
    api.familyOrders.mockResolvedValueOnce([pending]).mockResolvedValue([{ ...pending, status: "FAILED" }]);
    api.order.mockResolvedValue(pending);
    api.simulate.mockResolvedValue({ ...pending, status: "FAILED", checkout: null });
    const user = userEvent.setup();
    render(<FamilyPlanWithPayments />);

    await user.click(await screen.findByRole("button", { name: "Pretend it failed" }));
    expect(await screen.findByText(/didn't go through\. Your plan is unchanged/)).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Payment in progress" })).not.toBeInTheDocument();
  });

  it("keeps the request flow when no way of paying is switched on", async () => {
    api.methods.mockResolvedValue({ methods: [] });
    api.familyOrders.mockResolvedValue([]);
    render(<FamilyPlanWithPayments />);

    expect(await screen.findByRole("button", { name: /Ask for Family Plus/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Choose/ })).not.toBeInTheDocument();
    expect(screen.getByText(/can't be bought online yet/)).toBeInTheDocument();
  });
});
