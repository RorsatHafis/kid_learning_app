import { apiClient } from "./client";
import { messagesNow } from "@/i18n/catalog";
import { intlLocale } from "@/i18n/format";
import { apiMessages } from "./messages";

// ---- subscription/web/SubscriptionDtos.java ----

/** The product's levers (subscription/entity/EntitlementKey.java). */
export type EntitlementKey = "CHILDREN_MAX" | "JOURNEYS_PER_CHILD_MAX";
export type PlanStatus = "DRAFT" | "ACTIVE" | "RETIRED";
export type BillingInterval = "MONTH" | "YEAR";
export type SubscriptionStatus = "TRIALING" | "ACTIVE" | "PAST_DUE" | "CANCELED" | "EXPIRED";
export type SubscriptionSource = "ADMIN_GRANT" | "PROVIDER";

export interface Price {
  amountMinor: number;
  currency: string;
  interval: BillingInterval;
}

export interface PlanSummary {
  id: string;
  code: string;
  name: string;
  description: string | null;
  status: PlanStatus;
  isDefault: boolean;
  selfService: boolean;
  /** Null until the owner sets a price. */
  price: Price | null;
}

/** `limit` null = unlimited; `used` only in a family's view. */
export interface Entitlement {
  key: EntitlementKey;
  limit: number | null;
  used: number | null;
}

export interface PlanResponse {
  plan: PlanSummary;
  entitlements: Entitlement[];
  familiesOnPlan: number;
}

/** An operator's view; `id` is the audit log's resource for this subscription. */
export interface SubscriptionState {
  id: string;
  status: SubscriptionStatus;
  source: SubscriptionSource;
  startedAt: string;
  /** Null = open-ended. */
  currentPeriodEnd: string | null;
  cancelAtPeriodEnd: boolean;
  note: string | null;
  /** Whether it gives the family its plan right now. */
  grantsAccess: boolean;
}

export interface FamilyPlan {
  plan: PlanSummary;
  /** No subscription grants a plan now, so the platform's default plan applies. */
  onDefaultPlan: boolean;
  subscription: SubscriptionState | null;
  entitlements: Entitlement[];
}

/** A plan as a family compares it. */
export interface PlanOffer {
  plan: PlanSummary;
  entitlements: Entitlement[];
}

/** The family's own view (`GET /family/plan`): no operator note, plus what follows and what else exists. */
export interface OwnFamilyPlan {
  plan: PlanSummary;
  onDefaultPlan: boolean;
  subscription: Omit<SubscriptionState, "id" | "note"> | null;
  entitlements: Entitlement[];
  /** The plan that applies once the subscription ends, when an end is set. */
  afterwards: PlanOffer | null;
  /** Other plans the family may ask for (Y11; nothing is bought online, PD-09). */
  choices: PlanOffer[];
  /** The family's open request for one of them, if any. */
  request: { planId: string; planName: string; requestedAt: string } | null;
}

/** subscription/service/PlanRequestService.AdminRow - an open request, oldest first. */
export interface PlanRequestRow {
  id: string;
  familyId: string;
  familyName: string;
  parentEmail: string;
  planName: string;
  requestedAt: string;
}

export interface FamilyAccountSummary {
  familyId: string;
  familyName: string;
  parents: { accountId: string; email: string }[];
  childCount: number;
  plan: FamilyPlan;
}

/** What a plan limit is called, in the interface language: "Children". */
export function entitlementLabel(key: EntitlementKey, short = false): string {
  const m = messagesNow(apiMessages);
  return (short ? m.entitlementShort : m.entitlement)[key];
}

/** How many decimals a currency's amounts have (USD 2, JPY 0); null for a code that isn't a currency. */
export function currencyDigits(currency: string): number | null {
  try {
    return new Intl.NumberFormat("en", { style: "currency", currency }).resolvedOptions().maximumFractionDigits ?? 2;
  } catch {
    return null;
  }
}

/**
 * "$4.99 / month". Amounts are in the currency's minor units (V42), so the number of decimals
 * comes from the currency itself, not an assumed 2.
 */
export function priceText(price: Price | null): string | null {
  if (!price) return null;
  const m = messagesNow(apiMessages);
  const per = price.interval === "MONTH" ? m.perMonth : m.perYear;
  const digits = currencyDigits(price.currency);
  if (digits === null) return per(m.minorUnits(price.currency, price.amountMinor));
  const format = new Intl.NumberFormat(intlLocale(), { style: "currency", currency: price.currency });
  return per(format.format(price.amountMinor / 10 ** digits));
}

export const familyPlanApi = {
  get: () => apiClient.get<OwnFamilyPlan>("/family/plan"),
  // Y11: ask for an offered plan / withdraw the request; each returns the family's plan as it now stands.
  request: (planId: string) => apiClient.post<OwnFamilyPlan>("/family/plan/request", { planId }),
  withdraw: () => apiClient.delete<OwnFamilyPlan>("/family/plan/request"),
};

export const planRequestsApi = {
  open: (page = 0, size = 25) =>
    apiClient.get<{ items: PlanRequestRow[]; page: number; size: number; total: number }>(
      `/admin/plan-requests?page=${page}&size=${size}`,
    ),
  // 409 when the request was answered meanwhile.
  decline: (requestId: string, message: string) => apiClient.post<void>(`/admin/plan-requests/${requestId}/decline`, { message }),
};

export const plansApi = {
  list: () => apiClient.get<PlanResponse[]>("/admin/plans"),
  create: (body: { code: string; name: string; description?: string }) => apiClient.post<PlanResponse>("/admin/plans", body),
  revise: (
    planId: string,
    body: {
      name: string;
      description: string | null;
      selfService: boolean;
      priceAmountMinor: number | null;
      priceCurrency: string | null;
      billingInterval: BillingInterval | null;
      limits: Record<EntitlementKey, number | null>;
    },
  ) => apiClient.put<PlanResponse>(`/admin/plans/${planId}`, body),
  activate: (planId: string) => apiClient.post<PlanResponse>(`/admin/plans/${planId}/activate`),
  retire: (planId: string) => apiClient.post<PlanResponse>(`/admin/plans/${planId}/retire`),
  makeDefault: (planId: string) => apiClient.post<PlanResponse>(`/admin/plans/${planId}/make-default`),
  findFamily: (parentEmail: string) =>
    apiClient.get<FamilyAccountSummary>(`/admin/families?parentEmail=${encodeURIComponent(parentEmail)}`),
  grant: (familyId: string, body: { planId: string; periodEnd: string | null; note: string | null }) =>
    apiClient.post<FamilyAccountSummary>(`/admin/families/${familyId}/subscription`, body),
  /** `periodEnd` null = open-ended. */
  changePeriod: (familyId: string, periodEnd: string | null, reason: string) =>
    apiClient.put<FamilyAccountSummary>(`/admin/families/${familyId}/subscription/period`, { periodEnd, reason }),
  end: (familyId: string, reason: string) =>
    apiClient.post<FamilyAccountSummary>(`/admin/families/${familyId}/subscription/end`, { reason }),
};
