import { apiClient } from "./client";
import { priceText, type BillingInterval } from "./subscriptions";

// ---- subscription/choice/PlanChoiceController.java (2026-11-11) ----
// A plan chosen before registering, as the plan record holds it now. The browser only ever names a plan by its code;
// price, limits and audience are the record's, and a choice grants nothing: a paid plan starts from a confirmed payment.

export interface ChosenPlan {
  id: string;
  code: string;
  audience: "FAMILY" | "SCHOOL";
  name: string;
  description: string | null;
  isDefault: boolean;
  /** Null: nothing to pay. */
  amountMinor: number | null;
  currency: string | null;
  billingInterval: BillingInterval | null;
  /** Still on offer to choose today (a payment re-checks it). */
  offered: boolean;
}

/** The plan's price ("$7.00 / month"), or null when there is nothing to pay. */
export function chosenPrice(plan: Pick<ChosenPlan, "amountMinor" | "currency" | "billingInterval">): string | null {
  if (plan.amountMinor === null || !plan.currency || !plan.billingInterval) return null;
  return priceText({ amountMinor: plan.amountMinor, currency: plan.currency, interval: plan.billingInterval });
}

export const planChoiceApi = {
  /** The signed-in person's chosen plan still waiting for its payment; null when nothing waits (204). */
  mine: async () => (await apiClient.get<ChosenPlan | undefined>("/account/plan-choice")) ?? null,
  /** Keep the free plan instead. */
  setAside: () => apiClient.delete<void>("/account/plan-choice"),
};
