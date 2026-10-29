import { Link } from "react-router-dom";
import { useAsync } from "@/lib/hooks/useAsync";
import { adminPaymentsApi, orderPriceText } from "@/lib/api/payments";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { useMessages } from "@/i18n/catalog";
import { formatDate } from "@/i18n/format";
import { adminPaymentMessages } from "./payments.messages";

/**
 * One customer's latest invoices and payments (a school's, or a family's once an administrator has found it), each
 * opening on the Payments page filtered to that customer - where a payment is confirmed, rejected or refunded.
 */
export function CustomerPayments({
  customer,
  words,
}: {
  customer: { schoolId: string } | { familyId: string };
  words: { title: string; none: string; loading: string; all: (n: number) => string };
}) {
  const pm = useMessages(adminPaymentMessages);
  const schoolId = "schoolId" in customer ? customer.schoolId : null;
  const familyId = "familyId" in customer ? customer.familyId : null;
  const latest = useAsync(() => adminPaymentsApi.list(null, 0, 5, { schoolId, familyId }), [schoolId, familyId]);
  const orders = latest.data?.items ?? [];
  const filter = schoolId ? `school=${schoolId}` : `family=${familyId}`;
  const headingId = `payments-${schoolId ?? familyId}`;
  return (
    <section aria-labelledby={headingId} className="mt-5 flex flex-col gap-2 border-t border-line pt-4">
      <h3 id={headingId} className="text-sm font-semibold text-ink">
        {words.title}
      </h3>
      {latest.isLoading && !latest.data && <LoadingState label={words.loading} />}
      {latest.error && <ErrorState error={latest.error} onRetry={latest.refetch} />}
      {latest.data && orders.length === 0 && <p className="text-sm text-ink-soft">{words.none}</p>}
      {orders.length > 0 && (
        <ul className="flex flex-col divide-y divide-line">
          {orders.map((order) => (
            <li key={order.id} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-2.5">
              <Link
                to={`/admin/payments?${filter}&open=${order.id}`}
                className="min-w-0 font-medium text-tonle-700 underline-offset-2 [overflow-wrap:anywhere] hover:underline"
              >
                {order.planName} · {order.reference}
              </Link>
              <span className="text-sm text-ink-soft">
                {orderPriceText(order)} · {pm.status[order.status]} · {formatDate(order.createdAt)}
              </span>
            </li>
          ))}
        </ul>
      )}
      {latest.data && latest.data.total > orders.length && (
        <Link to={`/admin/payments?${filter}`} className="w-fit text-sm font-semibold text-tonle-700 underline-offset-2 hover:underline">
          {words.all(latest.data.total)}
        </Link>
      )}
    </section>
  );
}
