import { apiClient } from "./client";
import { priceText, type BillingInterval } from "./subscriptions";

// ---- payment/web/PaymentDtos.java (Y43) ----
// A request names a plan and a way of paying, never an amount or a status: the server freezes the plan's
// own price into the order and decides every outcome.

export type PaymentMethod = "DEV_SIMULATOR" | "MANUAL" | "ABA_PAYWAY";
export type PaymentStatus = "PENDING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | "EXPIRED" | "REFUNDED";

/** How to pay: instructions to show, or a form the browser posts to the provider's hosted checkout. */
export interface PaymentCheckout {
  instructions: string | null;
  action: string | null;
  fields: Record<string, string> | null;
}

export interface LedgerEntry {
  type: string;
  from: PaymentStatus | null;
  to: PaymentStatus | null;
  actorAccountId: string | null;
  detail: Record<string, unknown> | null;
  at: string;
}

export interface PaymentOrder {
  id: string;
  /** Quoted on a transfer and shown to support: "BK…". */
  reference: string;
  customerType: "FAMILY" | "SCHOOL";
  schoolId: string | null;
  planCode: string;
  planName: string;
  amountMinor: number;
  currency: string;
  billingInterval: BillingInterval;
  limits: Record<string, number>;
  method: PaymentMethod;
  status: PaymentStatus;
  createdAt: string;
  expiresAt: string;
  completedAt: string | null;
  periodStart: string | null;
  periodEnd: string | null;
  failureReason: string | null;
  customerNote: string | null;
  /** Only on a single order that can still be paid. */
  checkout: PaymentCheckout | null;
  /** Administrators only. */
  admin: { familyId: string | null; providerReference: string | null; planVersion: number; ledger: LedgerEntry[] } | null;
}

/** A school plan its principal may renew, at the plan's own price. */
export interface SchoolPlanChoice {
  id: string;
  code: string;
  name: string;
  description: string | null;
  amountMinor: number;
  currency: string;
  billingInterval: BillingInterval;
}

export interface PaymentOrderPage {
  items: PaymentOrder[];
  total: number;
  page: number;
  size: number;
}

/** "$5.00 / month" for what the order froze. */
export function orderPriceText(order: PaymentOrder): string {
  return priceText({ amountMinor: order.amountMinor, currency: order.currency, interval: order.billingInterval }) ?? "";
}

export const paymentsApi = {
  /** The ways of paying switched on here; empty = nothing can be bought online. */
  methods: () => apiClient.get<{ methods: PaymentMethod[] }>("/payments/methods"),
  familyOrders: () => apiClient.get<PaymentOrder[]>("/family/payments"),
  /** The same `idempotencyKey` again returns the same order; 409 while another payment is in progress. */
  start: (planId: string, method: PaymentMethod, idempotencyKey: string) =>
    apiClient.post<PaymentOrder>("/family/payments", { planId, method, idempotencyKey }),
  order: (orderId: string) => apiClient.get<PaymentOrder>(`/payments/${orderId}`),
  cancel: (orderId: string) => apiClient.post<PaymentOrder>(`/payments/${orderId}/cancel`),
  note: (orderId: string, note: string) => apiClient.post<PaymentOrder>(`/payments/${orderId}/note`, { note }),
  /** Development and tests only: refused unless the simulator is on, which production never allows. */
  simulate: (orderId: string, outcome: "SUCCEEDED" | "FAILED" | "CANCELLED") =>
    apiClient.post<PaymentOrder>(`/payments/${orderId}/simulate`, { outcome }),
  schoolOrders: (schoolId: string) => apiClient.get<PaymentOrder[]>(`/schools/${schoolId}/payments`),
  /** Y44: what the principal may renew (the school's own paid plan), and renewing it. */
  schoolRenewals: (schoolId: string) => apiClient.get<SchoolPlanChoice[]>(`/schools/${schoolId}/payment-plans`),
  renewSchool: (schoolId: string, planId: string, method: PaymentMethod, idempotencyKey: string) =>
    apiClient.post<PaymentOrder>(`/schools/${schoolId}/payments`, { planId, method, idempotencyKey }),
};

export const adminPaymentsApi = {
  list: (status: PaymentStatus | null, page = 0, size = 25) =>
    apiClient.get<PaymentOrderPage>(`/admin/payments?${status ? `status=${status}&` : ""}page=${page}&size=${size}`),
  get: (orderId: string) => apiClient.get<PaymentOrder>(`/admin/payments/${orderId}`),
  confirm: (orderId: string, receiptReference: string, note: string | null) =>
    apiClient.post<PaymentOrder>(`/admin/payments/${orderId}/confirm`, { receiptReference, note }),
  reject: (orderId: string, reason: string) => apiClient.post<PaymentOrder>(`/admin/payments/${orderId}/reject`, { reason }),
  refund: (orderId: string, reason: string) => apiClient.post<PaymentOrder>(`/admin/payments/${orderId}/refund`, { reason }),
  reconcile: (orderId: string) => apiClient.post<PaymentOrder>(`/admin/payments/${orderId}/reconcile`),
  invoiceSchool: (schoolId: string, planId: string, idempotencyKey: string) =>
    apiClient.post<PaymentOrder>(`/admin/schools/${schoolId}/invoices`, { planId, idempotencyKey }),
};
