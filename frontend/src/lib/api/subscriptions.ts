import { apiClient } from "./client";
import { messagesNow } from "@/i18n/catalog";
import { intlLocale } from "@/i18n/format";
import { apiMessages } from "./messages";

// ---- subscription/web/SubscriptionDtos.java ----

/** The product's levers (subscription/entity/EntitlementKey.java); each belongs to one audience. */
export type FamilyEntitlementKey = "CHILDREN_MAX" | "JOURNEYS_PER_CHILD_MAX";
export type SchoolEntitlementKey = "SCHOOL_CHILDREN_MAX" | "SCHOOL_TEACHERS_MAX" | "SCHOOL_CLASSES_MAX" | "TEACHER_STORAGE_MB";
export type EntitlementKey = FamilyEntitlementKey | SchoolEntitlementKey;
/** Who a plan is for (V59): a family's plan or a school's. */
export type PlanAudience = "FAMILY" | "SCHOOL";
export const ENTITLEMENT_KEYS: Record<PlanAudience, EntitlementKey[]> = {
  FAMILY: ["CHILDREN_MAX", "JOURNEYS_PER_CHILD_MAX"],
  SCHOOL: ["SCHOOL_CHILDREN_MAX", "SCHOOL_TEACHERS_MAX", "SCHOOL_CLASSES_MAX", "TEACHER_STORAGE_MB"],
};
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
  audience: PlanAudience;
  status: PlanStatus;
  isDefault: boolean;
  selfService: boolean;
  /** Null until the owner sets a price. */
  price: Price | null;
  /**
   * Its words in Khmer (V82) - in an administrator's view only; either may be null (not translated). A family's or
   * principal's view has `name` and `description` already in their language, and no `khmer`.
   */
  khmer?: { name: string | null; description: string | null } | null;
}

/** `limit` null = unlimited (TEACHER_STORAGE_MB is in MB); `used` only in a family's or school's view. */
export interface Entitlement {
  key: EntitlementKey;
  limit: number | null;
  used: number | null;
}

export interface PlanResponse {
  plan: PlanSummary;
  entitlements: Entitlement[];
  /** Families (family plan) or schools (school plan) whose subscription names it. */
  subscribers: number;
  /** The plan this one was duplicated from (V79): its earlier version. */
  basedOn: { id: string; code: string; name: string } | null;
  /** V80: when new customers can take it up (null = no bound on that side); subscribers are never affected. */
  availableFrom: string | null;
  availableUntil: string | null;
  /** Whether someone new can take it up now (decided by the server: active and inside its dates). */
  offeredNow: boolean;
}

/** Subscriptions naming one plan, by standing now (SubscriptionReportService.PlanCounts). */
export interface PlanCounts {
  planId: string;
  code: string;
  name: string;
  audience: PlanAudience;
  status: PlanStatus;
  isDefault: boolean;
  active: number;
  trialing: number;
  pastDue: number;
  cancelling: number;
  /** In force and ending within `endingWithinDays`, whatever the standing. */
  endingSoon: number;
  /** No longer in force (for a school: ended, refunded or never paid). */
  ended: number;
}

/** Who is on which plan now. Families are only counted; schools are listed when their paid period ends soon. */
export interface SubscriptionSummary {
  at: string;
  endingWithinDays: number;
  plans: PlanCounts[];
  familiesOnDefault: number;
  schoolsWithoutPaidPlan: number;
  schoolsEndingSoon: { schoolId: string; schoolName: string; planName: string; paidUntil: string }[];
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

/** Y44: whether the school's services are paid for now; only a confirmed payment makes them so. */
export interface SchoolAccess {
  paid: boolean;
  /** When the paid period ends; null when not paid. */
  paidUntil: string | null;
  /** A payment of the school is waiting to be confirmed (it grants nothing yet). */
  paymentPending: boolean;
}

/** A school's plan: `GET /schools/{id}/plan` (its principal; no operator note) or the admin's view. */
export interface SchoolPlan {
  schoolId: string;
  schoolName: string;
  plan: PlanSummary;
  /** No subscription grants a plan now, so the schools' default plan applies. */
  onDefaultPlan: boolean;
  subscription: SubscriptionState | null;
  /** `used`: what the school holds now (storage is per teacher, so TEACHER_STORAGE_MB has none). */
  entitlements: Entitlement[];
  access: SchoolAccess;
}

export type OwnSchoolPlan = Omit<SchoolPlan, "subscription"> & {
  subscription: Omit<SubscriptionState, "id" | "note"> | null;
};

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
  create: (body: { code: string; name: string; description?: string; audience?: PlanAudience }) =>
    apiClient.post<PlanResponse>("/admin/plans", body),
  revise: (
    planId: string,
    body: {
      name: string;
      description: string | null;
      selfService: boolean;
      priceAmountMinor: number | null;
      priceCurrency: string | null;
      billingInterval: BillingInterval | null;
      limits: Partial<Record<EntitlementKey, number | null>>;
      /** Left out: the Khmer stays as it is; blank parts are removed. */
      khmer?: { name?: string | null; description?: string | null };
    },
  ) => apiClient.put<PlanResponse>(`/admin/plans/${planId}`, body),
  activate: (planId: string) => apiClient.post<PlanResponse>(`/admin/plans/${planId}/activate`),
  retire: (planId: string) => apiClient.post<PlanResponse>(`/admin/plans/${planId}/retire`),
  makeDefault: (planId: string) => apiClient.post<PlanResponse>(`/admin/plans/${planId}/make-default`),
  /** A new draft with the plan's words, price and limits (V79); the original and its subscribers are untouched. */
  duplicate: (planId: string, body: { code: string; name: string }) =>
    apiClient.post<PlanResponse>(`/admin/plans/${planId}/duplicate`, body),
  summary: () => apiClient.get<SubscriptionSummary>("/admin/subscriptions/summary"),
  /** V80: offer dates; either may be null. */
  setAvailability: (planId: string, availableFrom: string | null, availableUntil: string | null) =>
    apiClient.put<PlanResponse>(`/admin/plans/${planId}/availability`, { availableFrom, availableUntil }),
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

/** A school's plan: the administrator gives, moves or ends it; the principal reads it. */
export const schoolPlansApi = {
  own: (schoolId: string) => apiClient.get<OwnSchoolPlan>(`/schools/${schoolId}/plan`),
  get: (schoolId: string) => apiClient.get<SchoolPlan>(`/admin/schools/${schoolId}/subscription`),
  /** `periodEnd` null = open-ended. */
  changePeriod: (schoolId: string, periodEnd: string | null, reason: string) =>
    apiClient.put<SchoolPlan>(`/admin/schools/${schoolId}/subscription/period`, { periodEnd, reason }),
  end: (schoolId: string, reason: string) =>
    apiClient.post<SchoolPlan>(`/admin/schools/${schoolId}/subscription/end`, { reason }),
};

/** M8: "120.00" in USD is 12000, "40000" in KHR is 40000; null for anything that isn't an amount in that currency's units. */
export function toMinorUnits(text: string, currency: string): number | null {
  const digits = currencyDigits(currency);
  const value = text.trim().replace(/,/g, "");
  if (digits === null || !new RegExp(`^\\d+(\\.\\d{1,${Math.max(digits, 1)}})?$`).test(value)) return null;
  const [whole, fraction = ""] = value.split(".");
  if (digits === 0 && fraction.length > 0) return null;
  return Number(whole) * 10 ** digits + Number((fraction + "0".repeat(digits)).slice(0, digits) || "0");
}
