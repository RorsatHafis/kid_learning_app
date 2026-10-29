import { Link } from "react-router-dom";
import { useAsync } from "@/lib/hooks/useAsync";
import { adminPaymentsApi, orderPriceText } from "@/lib/api/payments";
import { formatDate } from "@/i18n/format";
import { adminPaymentMessages } from "./payments.messages";
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
      {plan && <SchoolPayments schoolId={schoolId} />}
    </Panel>
  );
}

/** The school's latest invoices and payments, each opening on the Payments page (where it is confirmed or refunded). */
function SchoolPayments({ schoolId }: { schoolId: string }) {
  const m = useMessages(adminSchoolPlanMessages);
  const pm = useMessages(adminPaymentMessages);
  const latest = useAsync(() => adminPaymentsApi.list(null, 0, 5, schoolId), [schoolId]);
  const orders = latest.data?.items ?? [];
  return (
    <section aria-labelledby="school-payments" className="mt-5 flex flex-col gap-2 border-t border-line pt-4">
      <h3 id="school-payments" className="text-sm font-semibold text-ink">
        {m.paymentsTitle}
      </h3>
      {latest.isLoading && !latest.data && <LoadingState label={m.paymentsLoading} />}
      {latest.error && <ErrorState error={latest.error} onRetry={latest.refetch} />}
      {latest.data && orders.length === 0 && <p className="text-sm text-ink-soft">{m.paymentsNone}</p>}
      {orders.length > 0 && (
        <ul className="flex flex-col divide-y divide-line">
          {orders.map((order) => (
            <li key={order.id} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-2.5">
              <Link
                to={`/admin/payments?school=${schoolId}&open=${order.id}`}
                className="min-w-0 font-medium text-tonle-700 underline-offset-2 [overflow-wrap:anywhere] hover:underline"
              >
                {m.paymentRow(order.planName, order.reference)}
              </Link>
              <span className="text-sm text-ink-soft">
                {orderPriceText(order)} · {pm.status[order.status]} · {formatDate(order.createdAt)}
              </span>
            </li>
          ))}
        </ul>
      )}
      {latest.data && latest.data.total > orders.length && (
        <Link to={`/admin/payments?school=${schoolId}`} className="w-fit text-sm font-semibold text-tonle-700 underline-offset-2 hover:underline">
          {m.paymentsAll(latest.data.total)}
        </Link>
      )}
    </section>
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
