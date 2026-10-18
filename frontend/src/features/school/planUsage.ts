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

/** Where the plan comes from: arranged (until when), ended, or the schools' default. */
export function planSourceText(plan: SchoolPlan | OwnSchoolPlan, m: typeof schoolPlanMessages.en): string {
  const sub = plan.subscription;
  if (sub?.grantsAccess) {
    return sub.currentPeriodEnd ? m.givenUntil(day(sub.startedAt), day(sub.currentPeriodEnd)) : m.givenOpen(day(sub.startedAt));
  }
  if (sub) return m.ended(sub.currentPeriodEnd ? day(sub.currentPeriodEnd) : null);
  return m.onDefault;
}

