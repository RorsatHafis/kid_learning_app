/**
 * M6: a refusal from the family's plan says nothing is lost and names the real plans that include more - with a
 * price only when the plan has one - or says plainly that none is offered.
 */
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { PlanLimitNote } from "./PlanLimitNote";
import type { ApiProblem } from "@/lib/api/types";

const LIMIT: ApiProblem = {
  title: "Plan Limit Reached",
  status: 409,
  detail: "Your family's plan includes one child.",
  entitlement: "CHILDREN_MAX",
  limit: 1,
  reason: "PLAN_LIMIT",
  audience: "FAMILY",
  dataKept: true,
};

function show(problem: ApiProblem) {
  render(
    <MemoryRouter>
      <PlanLimitNote problem={problem} />
    </MemoryRouter>,
  );
}

describe("PlanLimitNote (M6)", () => {
  it("lists the plans that include more, with a price only when one is set", () => {
    show({
      ...LIMIT,
      unlockedBy: [
        { planId: "p", name: "Family Plus", limit: 4, priceAmountMinor: 500, priceCurrency: "USD", billingInterval: "MONTH" },
        { planId: "o", name: "Open", limit: null, priceAmountMinor: null, priceCurrency: null, billingInterval: null },
      ],
    });
    expect(screen.getByText(/your children's learning and progress stay as they are/)).toBeInTheDocument();
    expect(screen.getByText("Plans that include more:")).toBeInTheDocument();
    expect(screen.getByText(/^Family Plus \(Children: up to 4\) · \$5\.00/)).toBeInTheDocument();
    expect(screen.getByText("Open (Children: no limit)")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "See what your plan includes" })).toHaveAttribute("href", "/parent/account#plan");
  });

  it("says when no plan with more is offered, instead of promising one", () => {
    show({ ...LIMIT, unlockedBy: [] });
    expect(screen.getByText("No plan with more is offered right now.")).toBeInTheDocument();
    expect(screen.queryByText("Plans that include more:")).not.toBeInTheDocument();
  });
});
