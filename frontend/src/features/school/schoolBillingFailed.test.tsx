/**
 * A school's billing parts say when they couldn't be read, with Try again - the only way to pay, and a payment waiting
 * with its reference, never just vanish.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import type { PaymentOrder } from "@/lib/api/payments";

const api = vi.hoisted(() => ({ schoolOrders: vi.fn(), schoolRenewals: vi.fn(), methods: vi.fn(), order: vi.fn() }));
vi.mock("@/lib/api/payments", async (original) => ({
  ...(await original<typeof import("@/lib/api/payments")>()),
  paymentsApi: { ...api, renewSchool: vi.fn(), note: vi.fn(), cancel: vi.fn() },
}));

import { SchoolInvoices } from "./SchoolInvoices";
import { SchoolRenew } from "./SchoolBilling";

const pending: PaymentOrder = {
  id: "o1", reference: "BKSCH0000042", customerType: "SCHOOL", schoolId: "s1", planCode: "SCHOOL", planName: "School",
  amountMinor: 9000, currency: "USD", billingInterval: "YEAR", limits: {}, method: "MANUAL", status: "PENDING",
  createdAt: "2026-11-01T03:00:00Z", expiresAt: "2026-11-08T03:00:00Z", completedAt: null, periodStart: null, periodEnd: null,
  failureReason: null, customerNote: null, checkout: { instructions: "TEST INSTRUCTIONS", action: null, fields: null }, admin: null,
};

describe("a school's billing when it can't be read", () => {
  beforeEach(() => Object.values(api).forEach((fn) => fn.mockReset()));

  it("the payments say so, and Try again shows the payment waiting with its reference", async () => {
    api.schoolOrders.mockRejectedValueOnce(new TypeError("Failed to fetch")).mockResolvedValue({ items: [pending], page: 0, size: 10, total: 1 });
    api.order.mockResolvedValue(pending);
    const user = userEvent.setup();
    render(<SchoolInvoices schoolId="s1" />);

    expect(await screen.findByText("The school's payments couldn't be loaded.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect((await screen.findAllByText("BKSCH0000042")).length).toBeGreaterThan(0);
  });

  it("the ways to pay say so, and Try again offers them", async () => {
    api.schoolRenewals.mockRejectedValueOnce(new TypeError("Failed to fetch")).mockResolvedValue([
      { id: "p1", code: "SCHOOL", name: "School", description: null, amountMinor: 9000, currency: "USD", billingInterval: "YEAR", limits: {} },
    ]);
    api.methods.mockResolvedValue({ methods: ["MANUAL"] });
    const user = userEvent.setup();
    render(<SchoolRenew schoolId="s1" first onStarted={vi.fn()} />);

    expect(await screen.findByText("The ways to pay couldn't be loaded.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText(/School/, { selector: "label *" })).toBeInTheDocument();
    expect(screen.queryByText("The ways to pay couldn't be loaded.")).not.toBeInTheDocument();
  });
});
