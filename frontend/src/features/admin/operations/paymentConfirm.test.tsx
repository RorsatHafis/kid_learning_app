/**
 * M8: an administrator confirms a transfer with the amount and currency the receipt shows (typed, never filled in from
 * the order) after re-entering their password; a malformed amount is caught before anything is sent, the server's
 * refusal of a wrong amount is shown, and the confirmation is sent once more only with the step-up proof.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { StepUpProvider } from "@/components/StepUpProvider";
import { clearStepUp } from "@/lib/api/client";
import { PaymentsPage } from "./PaymentsPage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const ORDER = {
  id: "o1",
  reference: "BKTEST0001",
  customerType: "FAMILY",
  schoolId: null,
  planCode: "FAMILY_PLUS",
  planName: "Family Plus",
  amountMinor: 12000,
  currency: "USD",
  billingInterval: "YEAR",
  limits: {},
  method: "MANUAL",
  status: "PENDING",
  createdAt: "2026-10-23T00:00:00Z",
  expiresAt: "2026-11-06T00:00:00Z",
  completedAt: null,
  periodStart: null,
  periodEnd: null,
  failureReason: null,
  customerNote: null,
  checkout: null,
  admin: { familyId: "f1", providerReference: null, planVersion: 1, ledger: [] },
};

afterEach(() => {
  vi.unstubAllGlobals();
  clearStepUp();
});

describe("confirming a transfer (M8)", () => {
  it("sends the receipt's amount and currency, once more only with the step-up proof", async () => {
    const confirms: { body: Record<string, unknown>; stepUp: string | null }[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        const headers = (init?.headers ?? {}) as Record<string, string>;
        if (path.startsWith("/admin/payments?")) return respond(200, { items: [ORDER], total: 1, page: 0, size: 25 });
        if (path === "/admin/payments/o1" && (init?.method ?? "GET") === "GET") return respond(200, ORDER);
        if (path === "/account/step-up") {
          return respond(200, { stepUpToken: "proof-1", expiresAt: new Date(Date.now() + 300000).toISOString() });
        }
        if (path === "/admin/payments/o1/confirm") {
          const body = JSON.parse(init?.body as string);
          confirms.push({ body, stepUp: headers["X-Step-Up"] ?? null });
          if (!headers["X-Step-Up"]) {
            return respond(403, { status: 403, title: "Password Needed", detail: "Enter your password again to do this", stepUpRequired: true });
          }
          if (body.amountReceivedMinor !== 12000) {
            return respond(409, {
              status: 409,
              title: "Conflict",
              detail: "The amount received doesn't match this payment: check the receipt",
            });
          }
          return respond(200, { ...ORDER, status: "SUCCEEDED", admin: { ...ORDER.admin, providerReference: "BANK-77" } });
        }
        return respond(404, { status: 404 });
      }),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/admin/payments?open=o1"]}>
        <StepUpProvider>
          <PaymentsPage />
        </StepUpProvider>
      </MemoryRouter>,
    );

    await user.type(await screen.findByLabelText("Receipt reference"), "BANK-77");
    expect(screen.getByText(/This payment is for \$120\.00/)).toBeInTheDocument();
    // Malformed: caught here, nothing sent.
    await user.type(screen.getByLabelText("Amount on the receipt"), "12.345");
    await user.click(screen.getByRole("button", { name: "Confirm payment received" }));
    expect(await screen.findByText("Enter the amount as the receipt shows it, for example 120.00.")).toBeInTheDocument();
    expect(confirms).toHaveLength(0);

    // The password is asked for; the confirmation goes again with the proof, and the server's refusal is shown.
    await user.clear(screen.getByLabelText("Amount on the receipt"));
    await user.type(screen.getByLabelText("Amount on the receipt"), "119.99");
    await user.click(screen.getByRole("button", { name: "Confirm payment received" }));
    await user.type(await screen.findByLabelText("Your password"), "admin-password");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByText(/The amount received doesn't match this payment/)).toBeInTheDocument();
    expect(confirms.map((c) => c.stepUp)).toEqual([null, "proof-1"]);

    // The right amount: confirmed, with the proof already held - no second password, one request.
    await user.clear(screen.getByLabelText("Amount on the receipt"));
    await user.type(screen.getByLabelText("Amount on the receipt"), "120.00");
    await user.click(screen.getByRole("button", { name: "Confirm payment received" }));
    expect(await screen.findByText("Done. The payment and the plan are up to date.")).toBeInTheDocument();
    expect(confirms).toHaveLength(3);
    expect(confirms[2]).toEqual({
      body: { receiptReference: "BANK-77", amountReceivedMinor: 12000, currency: "USD", note: null },
      stepUp: "proof-1",
    });
  });
});
