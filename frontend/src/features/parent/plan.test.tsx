import { describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import type { OwnFamilyPlan, PlanOffer, PlanSummary } from "@/lib/api/subscriptions";
import { priceText } from "@/lib/api/subscriptions";
import { PlanDetails } from "./PlanDetails";

const summary = (over: Partial<PlanSummary>): PlanSummary => ({
  id: "p",
  code: "P",
  name: "Plan",
  description: null,
  audience: "FAMILY",
  status: "ACTIVE",
  isDefault: false,
  selfService: false,
  price: null,
  ...over,
});

const earlyAccess: PlanOffer = {
  plan: summary({ id: "default", code: "EARLY_ACCESS", name: "Early access", isDefault: true }),
  entitlements: [
    { key: "CHILDREN_MAX", limit: 2, used: null },
    { key: "JOURNEYS_PER_CHILD_MAX", limit: null, used: null },
  ],
};

const NOW = new Date(2026, 8, 30, 10, 0);

function granted(over: Partial<OwnFamilyPlan> = {}): OwnFamilyPlan {
  return {
    plan: summary({ id: "pilot", name: "Pilot" }),
    onDefaultPlan: false,
    subscription: {
      status: "ACTIVE",
      source: "ADMIN_GRANT",
      startedAt: "2026-09-01T00:00:00Z",
      currentPeriodEnd: new Date(2026, 9, 3, 23, 59, 59).toISOString(),
      cancelAtPeriodEnd: false,
      grantsAccess: true,
    },
    entitlements: [
      { key: "CHILDREN_MAX", limit: 4, used: 3 },
      { key: "JOURNEYS_PER_CHILD_MAX", limit: 3, used: 1 },
    ],
    afterwards: earlyAccess,
    choices: [],
    request: null,
    ...over,
  };
}

describe("PlanDetails", () => {
  it("says when a granted plan ends, what follows, and that the family keeps what it has", () => {
    render(<PlanDetails plan={granted()} now={NOW} />);

    expect(screen.getByText(/Until .*2026 · ends in 3 days/)).toBeInTheDocument();
    expect(screen.getByText("Given to your family by Bookeyond Kid.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /^After / })).toBeInTheDocument();
    expect(screen.getByText(/Nothing your children have done is removed/)).toHaveTextContent(
      "It includes less than your family has now: you keep everything, but can't add more beyond it.",
    );
  });

  it("explains a family over its plan's limit without suggesting anything is taken away", () => {
    render(
      <PlanDetails
        plan={granted({
          entitlements: [
            { key: "CHILDREN_MAX", limit: 2, used: 3 },
            { key: "JOURNEYS_PER_CHILD_MAX", limit: 1, used: 1 },
          ],
          afterwards: null,
        })}
        now={NOW}
      />,
    );
    expect(
      screen.getByText(/Your family has 3 children and this plan includes 2. Every child keeps learning/),
    ).toBeInTheDocument();
    // At the limit is not over it.
    expect(screen.queryByText(/journeys at once/)).not.toBeInTheDocument();
  });

  it("shows an open-ended default plan plainly, and other plans without offering a checkout", () => {
    render(
      <PlanDetails
        plan={{
          plan: earlyAccess.plan,
          onDefaultPlan: true,
          subscription: null,
          entitlements: [
            { key: "CHILDREN_MAX", limit: null, used: 1 },
            { key: "JOURNEYS_PER_CHILD_MAX", limit: null, used: 1 },
          ],
          afterwards: null,
          choices: [
            {
              plan: summary({
                id: "family",
                name: "Family",
                selfService: true,
                price: { amountMinor: 499, currency: "USD", interval: "MONTH" },
              }),
              entitlements: [{ key: "CHILDREN_MAX", limit: 4, used: null }],
            },
          ],
          request: null,
        }}
        now={NOW}
      />,
    );
    expect(screen.getByText("1 added · no limit")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /^After / })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Other plans" })).toBeInTheDocument();
    expect(screen.getByText(/Plans can't be bought online yet\. Ask for one/)).toBeInTheDocument();
    expect(screen.getByText(priceText({ amountMinor: 499, currency: "USD", interval: "MONTH" })!)).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("describes an ended plan as over, and a plan ending today as today", () => {
    const { rerender } = render(
      <PlanDetails
        plan={granted({
          onDefaultPlan: true,
          subscription: { ...granted().subscription!, status: "EXPIRED", grantsAccess: false },
          afterwards: null,
        })}
        now={NOW}
      />,
    );
    expect(screen.getByText(/An earlier plan ended on/)).toBeInTheDocument();

    rerender(
      <PlanDetails
        plan={granted({
          subscription: { ...granted().subscription!, currentPeriodEnd: new Date(2026, 8, 30, 15, 0).toISOString() },
        })}
        now={NOW}
      />,
    );
    expect(screen.getByText(/ends today/)).toBeInTheDocument();
  });
});

describe("priceText", () => {
  it("uses the currency's own minor units", () => {
    expect(priceText({ amountMinor: 499, currency: "USD", interval: "MONTH" })).toMatch(/4\.99 \/ month$/);
    expect(priceText({ amountMinor: 500, currency: "JPY", interval: "YEAR" })).toMatch(/500 \/ year$/);
    expect(priceText(null)).toBeNull();
  });

  it("asks for an offered plan and withdraws the request, each time showing the plan as it now stands (Y11)", async () => {
    const family = { id: "family", code: "FAMILY", name: "Family", selfService: true };
    const offer: PlanOffer = { plan: summary(family), entitlements: [{ key: "CHILDREN_MAX", limit: 4, used: null }] };
    const asked = granted({ choices: [offer], request: { planId: "family", planName: "Family", requestedAt: "2026-09-29T08:00:00Z" } });
    const calls: { method: string; url: string; body: unknown }[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        calls.push({ method: init?.method ?? "GET", url: String(url), body: init?.body ? JSON.parse(String(init.body)) : undefined });
        return new Response(JSON.stringify(init?.method === "DELETE" ? granted({ choices: [offer] }) : asked), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }),
    );
    const onChanged = vi.fn();
    const user = userEvent.setup();
    const { rerender } = render(<PlanDetails plan={granted({ choices: [offer] })} now={NOW} onChanged={onChanged} />);

    await user.click(screen.getByRole("button", { name: "Ask for Family" }));
    expect(calls[0]).toMatchObject({ method: "POST", body: { planId: "family" } });
    expect(String(calls[0].url)).toContain("/family/plan/request");
    expect(onChanged).toHaveBeenCalledWith(asked);

    rerender(<PlanDetails plan={asked} now={NOW} onChanged={onChanged} />);
    expect(screen.getByRole("status")).toHaveTextContent(/You asked for this plan on/);
    expect(screen.queryByRole("button", { name: "Ask for Family" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Withdraw the request" }));
    expect(calls[1].method).toBe("DELETE");
    vi.unstubAllGlobals();
  });
});
