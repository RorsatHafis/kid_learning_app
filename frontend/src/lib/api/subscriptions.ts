import { apiClient } from "./client";

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

export interface SubscriptionState {
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

export interface FamilyAccountSummary {
  familyId: string;
  familyName: string;
  parents: { accountId: string; email: string }[];
  childCount: number;
  plan: FamilyPlan;
}

export const ENTITLEMENT_LABEL: Record<EntitlementKey, string> = {
  CHILDREN_MAX: "Children",
  JOURNEYS_PER_CHILD_MAX: "Journeys per child at a time",
};

export const familyPlanApi = {
  get: () => apiClient.get<FamilyPlan>("/family/plan"),
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
  end: (familyId: string, reason: string) =>
    apiClient.post<FamilyAccountSummary>(`/admin/families/${familyId}/subscription/end`, { reason }),
};
