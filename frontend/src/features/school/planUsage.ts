import type { OwnSchoolPlan, SchoolPlan } from "@/lib/api/subscriptions";
import { formatDate } from "@/i18n/format";
import type { schoolPlanMessages } from "./plan.messages";

const day = (iso: string) => formatDate(iso, { day: "numeric", month: "short", year: "numeric" });

/** How full a counted limit is: OK, NEARLY (80% or more), FULL, or OVER (after a plan change). */
export function fullness(used: number, limit: number | null): "OK" | "NEARLY" | "FULL" | "OVER" {
  if (limit === null) return "OK";
  if (used > limit) return "OVER";
  if (used === limit) return "FULL";
  return used >= limit * 0.8 ? "NEARLY" : "OK";
}

/** Y44: the school's paid state in words - paid until when, a payment waiting, a paid period over, or never paid. */
export function planSourceText(plan: SchoolPlan | OwnSchoolPlan, m: typeof schoolPlanMessages.en): string {
  const access = plan.access;
  if (access.paid) return access.paidUntil ? m.paidUntil(day(access.paidUntil)) : m.paidUntil("—");
  if (access.paymentPending) return m.paymentPending;
  const sub = plan.subscription;
  if (sub?.source === "PROVIDER") return m.paidEnded(sub.currentPeriodEnd ? day(sub.currentPeriodEnd) : null);
  return m.notPaid;
}

