import { useAsync } from "@/lib/hooks/useAsync";
import { CustomerPayments } from "./CustomerPayments";
import { plansApi, schoolPlansApi, type SchoolPlan } from "@/lib/api/subscriptions";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { useMessages } from "@/i18n/catalog";
import { SchoolPlanUsage } from "@/features/school/SchoolPlanCard";
import { planSourceText } from "@/features/school/planUsage";
import { schoolPlanMessages } from "@/features/school/plan.messages";
import { cn } from "@/lib/utils/cn";
import { Panel } from "../studio/shared";
import { AuditLog } from "./AuditLog";
import { SchoolInvoice } from "./SchoolInvoice";
import { adminSchoolPlanMessages } from "./schoolPlan.messages";

/**
 * A school's plan, for an administrator: whether its services are paid for (Y44), what its plan includes and
 * holds, and invoicing it. A school's plan starts only when its payment is confirmed (Payments page), so there is
 * no "give a plan" here; nothing the school holds is removed when it isn't paid.
 */
export function SchoolPlanPanel({ schoolId }: { schoolId: string }) {
  const current = useAsync(() => schoolPlansApi.get(schoolId), [schoolId]);
  const plans = useAsync(() => plansApi.list(), []);
  const m = useMessages(adminSchoolPlanMessages);
  const plan = current.data;

  return (
    <Panel title={m.title}>
      {current.isLoading && !plan && <LoadingState label={m.loading} />}
      {current.error && <ErrorState error={current.error} onRetry={current.refetch} />}
      {plan && <SchoolPlanState plan={plan} />}
      {plan && <SchoolInvoice schoolId={schoolId} plans={plans.data ?? []} />}
      {plan && (
        <CustomerPayments
          customer={{ schoolId }}
          words={{ title: m.paymentsTitle, none: m.paymentsNone, loading: m.paymentsLoading, all: m.paymentsAll }}
        />
      )}
    </Panel>
  );
}

function SchoolPlanState({ plan }: { plan: SchoolPlan }) {
  const m = useMessages(adminSchoolPlanMessages);
  const shared = useMessages(schoolPlanMessages);
  const sub = plan.subscription;
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3">
        <p className="font-semibold text-ink">{plan.plan.name}</p>
        <p
          className={cn(
            "rounded-[12px] px-4 py-3 text-sm ring-1",
            plan.access.paid ? "bg-palm-soft/50 text-palm-deep ring-palm/30" : "bg-light-soft/50 text-ink ring-light/40",
          )}
        >
          {planSourceText(plan, shared)}
        </p>
        {sub?.note && <p className="text-sm text-ink-faint">{m.note(sub.note)}</p>}
        <SchoolPlanUsage entitlements={plan.entitlements} />
      </div>
      {sub && (
        <section aria-labelledby="school-subscription-history" className="flex flex-col gap-2">
          <h3 id="school-subscription-history" className="text-sm font-semibold text-ink">
            {m.history}
          </h3>
          <AuditLog filter={{ resourceId: sub.id }} emptyTitle={m.noHistory} />
        </section>
      )}
    </div>
  );
}
