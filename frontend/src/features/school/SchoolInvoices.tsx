import { useId, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { useMessages } from "@/i18n/catalog";
import { formatDate, formatDateTime } from "@/i18n/format";
import { orderPriceText, paymentsApi, type PaymentOrder } from "@/lib/api/payments";
import { useAsync } from "@/lib/hooks/useAsync";
import { errorText } from "@/features/admin/studio/studioLogic";
import { invoiceMessages } from "./invoices.messages";

/**
 * A school's invoices, for its principal (Y43): the Bookeyond team issues them at the plan's price; the
 * principal sees what to pay and how, and may say how it was paid. Only the team confirms a payment.
 */
export function SchoolInvoices({ schoolId }: { schoolId: string }) {
  const m = useMessages(invoiceMessages);
  const invoices = useAsync(() => paymentsApi.schoolOrders(schoolId), [schoolId]);
  const titleId = useId();
  if (!invoices.data || invoices.data.length === 0) return null;
  const pending = invoices.data.find((order) => order.status === "PENDING");
  const settled = invoices.data.filter((order) => order.status !== "PENDING");

  return (
    <section aria-labelledby={titleId} className="flex flex-col gap-3 border-t border-line pt-4">
      <h3 id={titleId} className="text-sm font-semibold text-ink">
        {m.title}
      </h3>
      {pending && <PendingInvoice key={pending.id} orderId={pending.id} />}
      {settled.length > 0 && (
        <ul className="divide-y divide-line text-sm">
          {settled.map((order) => (
            <li key={order.id} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-2">
              <span className="text-ink">
                {order.planName} · <span className="tabular">{orderPriceText(order)}</span>
                <span className="block text-xs text-ink-faint">
                  {formatDate(order.createdAt)} · <span className="font-mono">{order.reference}</span>
                </span>
              </span>
              <span className={order.status === "SUCCEEDED" ? "font-semibold text-palm-deep" : "font-semibold text-ink-soft"}>
                {m.status[order.status]}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function PendingInvoice({ orderId }: { orderId: string }) {
  const m = useMessages(invoiceMessages);
  const order = useAsync(() => paymentsApi.order(orderId), [orderId]);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ ok: boolean; text: string } | null>(null);
  const data: PaymentOrder | null = order.data;

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setFeedback(null);
    try {
      await paymentsApi.note(orderId, note.trim());
      setFeedback({ ok: true, text: m.saved });
    } catch (err) {
      setFeedback({ ok: false, text: errorText(err, m.failed) });
    } finally {
      setBusy(false);
    }
  }

  if (!data) return null;
  return (
    <div className="flex flex-col gap-3 rounded-[12px] bg-light-soft/30 p-4 ring-1 ring-light/40">
      <p className="flex flex-wrap items-baseline justify-between gap-x-4">
        <span className="font-semibold text-tonle-950">{m.waiting(data.planName)}</span>
        <span className="tabular font-semibold text-ink">{orderPriceText(data)}</span>
      </p>
      <p className="text-sm text-ink">
        {m.reference}: <span className="font-mono font-semibold tracking-wider">{data.reference}</span> ·{" "}
        <span className="text-ink-soft">{m.payBy(formatDateTime(data.expiresAt))}</span>
      </p>
      <p className="text-sm text-ink-soft">{m.lead}</p>
      {data.checkout?.instructions && (
        <p className="whitespace-pre-line rounded-[10px] bg-white px-3 py-2 text-sm text-ink ring-1 ring-line">{data.checkout.instructions}</p>
      )}
      <form onSubmit={save} className="flex flex-col items-start gap-2">
        <div className="w-full">
          <FormField label={m.noteLabel} hint={m.noteHint} value={note} maxLength={300} onChange={(e) => setNote(e.target.value)} />
        </div>
        <Button type="submit" variant="outline" disabled={busy || !note.trim()}>
          {m.save}
        </Button>
      </form>
      {feedback && (
        <p role={feedback.ok ? "status" : "alert"} className={feedback.ok ? "text-sm text-palm-deep" : "text-sm text-brick-deep"}>
          {feedback.text}
        </p>
      )}
    </div>
  );
}
