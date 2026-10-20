import { entitlementLabel, schoolPlansApi, type Entitlement } from "@/lib/api/subscriptions";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { schoolPlanMessages } from "./plan.messages";
import { fullness, planSourceText } from "./planUsage";
import { SchoolInvoices } from "./SchoolInvoices";

/** One line per limit: what the school holds against what its plan includes. */
export function SchoolPlanUsage({ entitlements }: { entitlements: Entitlement[] }) {
  const m = useMessages(schoolPlanMessages);
  return (
    <ul className="grid gap-x-8 gap-y-4 sm:grid-cols-2">
      {entitlements.map((e) => {
        if (e.key === "TEACHER_STORAGE_MB") {
          return (
            <li key={e.key} className="flex flex-col gap-1">
              <span className="text-sm text-ink-soft">{entitlementLabel(e.key, true)}</span>
              <span className="font-semibold text-ink">{e.limit === null ? m.perTeacherOpen : m.perTeacher(e.limit)}</span>
            </li>
          );
        }
        const used = e.used ?? 0;
        const state = fullness(used, e.limit);
        const label = entitlementLabel(e.key);
        return (
          <li key={e.key} className="flex flex-col gap-1.5">
            <span className="flex items-baseline justify-between gap-3">
              <span className="text-sm text-ink-soft">{label}</span>
              <span className="tabular font-semibold text-ink">{e.limit === null ? m.usageOpen(used) : m.usage(used, e.limit)}</span>
            </span>
            {e.limit !== null && (
              <span
                role="meter"
                aria-label={label}
                aria-valuemin={0}
                aria-valuemax={e.limit}
                aria-valuenow={Math.min(used, e.limit)}
                aria-valuetext={m.usage(used, e.limit)}
                className="h-1.5 overflow-hidden rounded-full bg-line"
              >
                <span
                  className={cn(
                    "block h-full rounded-full transition-[width] duration-500 ease-out motion-reduce:transition-none",
                    state === "OK" ? "bg-tonle-600" : "bg-light",
                  )}
                  style={{ width: `${e.limit === 0 ? 100 : Math.min(100, (used / e.limit) * 100)}%` }}
                />
              </span>
            )}
            {state !== "OK" && (
              <span className="text-xs font-medium text-light-deep">
                {state === "OVER" ? m.over : state === "FULL" ? m.full : m.nearly}
              </span>
            )}
          </li>
        );
      })}
    </ul>
  );
}

/**
 * The principal's view of the school's plan: what it includes and how much of it is in use, so a
 * limit is never a surprise. Changing it is arranged with the Bookeyond team (no payment, PD-09).
 */
export function PrincipalSchoolPlan({ schoolId, version }: { schoolId: string; version: number }) {
  const plan = useAsync(() => schoolPlansApi.own(schoolId), [schoolId, version]);
  const m = useMessages(schoolPlanMessages);
  return (
    <section aria-labelledby="school-plan-heading" className="flex flex-col gap-4 rounded-[16px] border border-line bg-white px-5 py-5 sm:px-6">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 id="school-plan-heading" className="text-lg font-semibold text-tonle-950">
          {m.title}
        </h2>
        {plan.data && (
          <p className="text-sm font-semibold text-tonle-700">
            {plan.data.plan.name}
            {plan.data.onDefaultPlan && <span className="font-normal text-ink-soft"> {m.defaultSuffix}</span>}
          </p>
        )}
      </div>
      {plan.isLoading && !plan.data && <LoadingState label={m.loading} />}
      {plan.error && <ErrorState error={plan.error} onRetry={plan.refetch} />}
      {plan.data && (
        <>
          <p className="text-sm text-ink-soft">{planSourceText(plan.data, m)}</p>
          <SchoolPlanUsage entitlements={plan.data.entitlements} />
          <p className="text-sm leading-relaxed text-ink-soft">
            {m.keepsAll} {m.noPurchase}
          </p>
          <SchoolInvoices schoolId={schoolId} />
        </>
      )}
    </section>
  );
}
