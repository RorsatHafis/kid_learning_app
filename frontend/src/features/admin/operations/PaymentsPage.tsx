import { useState, type FormEvent } from "react";
import { X } from "lucide-react";
import { adminPaymentsApi, orderPriceText, type PaymentOrder, type PaymentStatus } from "@/lib/api/payments";
import { toMinorUnits } from "@/lib/api/subscriptions";
import { useAsync } from "@/lib/hooks/useAsync";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { usePageInRange, useUrlFilters } from "@/lib/hooks/useUrlFilters";
import { Button } from "@/components/ui/Button";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { FormField } from "@/features/auth/FormField";
import { useMessages } from "@/i18n/catalog";
import { formatDate, formatDateTime } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { Panel } from "../studio/shared";
import { errorText } from "../studio/studioLogic";
import { adminPaymentMessages } from "./payments.messages";

const STATUSES: PaymentStatus[] = ["PENDING", "SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED", "REFUNDED"];
const PAGE_SIZE = 25;

/**
 * Payments, for an administrator (Y43): every order with what it froze at checkout, its ledger, and the
 * actions the server allows and audits. Nothing here can set an amount; a transfer is confirmed only
 * with the receipt's own reference.
 */
export function PaymentsPage() {
  const m = useMessages(adminPaymentMessages);
  useDocumentTitle(m.title);
  const { params, page, update, setPage } = useUrlFilters();
  const status = (STATUSES as string[]).includes(params.get("status") ?? "") ? (params.get("status") as PaymentStatus) : null;
  const [version, setVersion] = useState(0);
  const list = useAsync(() => adminPaymentsApi.list(status, page, PAGE_SIZE), [status, page, version]);
  const openId = params.get("open");
  const pages = list.data ? Math.max(1, Math.ceil(list.data.total / PAGE_SIZE)) : 1;
  usePageInRange(list.data, setPage);
  const open = (id: string | undefined) => update({ open: id }, true);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={m.title} description={m.lead} />
      <div className={cn("grid grid-cols-1 gap-6", openId && "xl:grid-cols-[minmax(0,1fr)_440px]")}>
        <Panel title={status ? m.status[status] : m.all} flush>
          <div className="px-5 pb-4 sm:px-6">
            <label className="flex w-fit items-center gap-2 text-sm text-ink">
              <span className="font-semibold">{m.show}</span>
              <select
                value={status ?? ""}
                onChange={(e) => update({ status: e.target.value || undefined, open: undefined })}
                className="min-h-11 rounded-[12px] border border-line-strong bg-white px-3 py-2 text-sm focus:border-tonle-600 focus:outline-none focus:ring-2 focus:ring-tonle-600/15"
              >
                <option value="">{m.all}</option>
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {m.status[s]}
                  </option>
                ))}
              </select>
            </label>
          </div>
          {list.isLoading && !list.data && (
            <div className="px-5 pb-5 sm:px-6">
              <LoadingState label={m.loading} />
            </div>
          )}
          {list.error && (
            <div className="px-5 pb-5 sm:px-6">
              <ErrorState error={list.error} onRetry={list.refetch} />
            </div>
          )}
          {list.data && list.data.items.length === 0 && (
            <div className="px-5 pb-5 sm:px-6">
              <EmptyState title={status ? m.noneMatch : m.none} />
            </div>
          )}
          {list.data && list.data.items.length > 0 && (
            <>
              <div
                aria-hidden
                className="hidden grid-cols-[minmax(0,1fr)_9rem_8rem_8rem] gap-x-4 border-y border-line bg-paper px-6 py-2 text-[0.8125rem] text-ink-faint md:grid"
              >
                <span>{m.columns.customer}</span>
                <span>{m.columns.amount}</span>
                <span>{m.columns.status}</span>
                <span>{m.columns.when}</span>
              </div>
              <ul aria-label={m.title} className="divide-y divide-line border-t border-line md:border-t-0">
                {list.data.items.map((order) => (
                  <li key={order.id}>
                    <button
                      type="button"
                      onClick={() => open(order.id === openId ? undefined : order.id)}
                      aria-expanded={order.id === openId}
                      aria-label={m.open(order.reference)}
                      className={cn(
                        "grid w-full gap-x-4 gap-y-1 px-5 py-3.5 text-left transition-colors hover:bg-paper focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-tonle-600 md:grid-cols-[minmax(0,1fr)_9rem_8rem_8rem] md:items-center md:px-6",
                        order.id === openId && "bg-tonle-50/60",
                      )}
                    >
                      <span className="min-w-0">
                        <span className="block truncate font-medium text-tonle-950">{order.planName}</span>
                        <span className="block text-xs text-ink-faint">
                          {order.customerType === "SCHOOL" ? m.school : m.family} · <span className="font-mono">{order.reference}</span> ·{" "}
                          {m.method[order.method]}
                        </span>
                      </span>
                      <span className="tabular text-sm text-ink">{orderPriceText(order)}</span>
                      <StatusBadge status={order.status} />
                      <span className="text-sm text-ink-soft">{formatDate(order.createdAt)}</span>
                    </button>
                  </li>
                ))}
              </ul>
              {pages > 1 && (
                <nav className="flex items-center justify-between gap-3 border-t border-line px-5 py-3 sm:px-6" aria-label={m.title}>
                  <Button size="sm" variant="ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>
                    {m.previous}
                  </Button>
                  <span className="text-sm text-ink-soft">{m.pageOf(page + 1, pages)}</span>
                  <Button size="sm" variant="ghost" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>
                    {m.next}
                  </Button>
                </nav>
              )}
            </>
          )}
        </Panel>
        {openId && (
          <PaymentDetail
            key={openId}
            orderId={openId}
            onClose={() => open(undefined)}
            onChanged={() => setVersion((v) => v + 1)}
          />
        )}
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: PaymentStatus }) {
  const m = useMessages(adminPaymentMessages);
  const tone =
    status === "SUCCEEDED"
      ? "bg-palm-soft text-palm-deep ring-palm/30"
      : status === "PENDING"
        ? "bg-light-soft text-light-deep ring-light/40"
        : status === "REFUNDED" || status === "FAILED"
          ? "bg-brick-soft text-brick-deep ring-brick/30"
          : "bg-paper text-ink-soft ring-line";
  return (
    <span className={cn("inline-flex w-fit items-center rounded-[6px] px-2 py-0.5 text-xs font-semibold ring-1 ring-inset", tone)}>
      {m.status[status]}
    </span>
  );
}

function PaymentDetail({ orderId, onClose, onChanged }: { orderId: string; onClose: () => void; onChanged: () => void }) {
  const all = useMessages(adminPaymentMessages);
  const m = all.detail;
  const [override, setOverride] = useState<PaymentOrder | null>(null);
  const loaded = useAsync(() => adminPaymentsApi.get(orderId), [orderId]);
  const order = override ?? loaded.data;
  const [receipt, setReceipt] = useState("");
  // M8: what the receipt shows - typed from it, never filled in from the order, so the server's check means something.
  const [received, setReceived] = useState("");
  const [currency, setCurrency] = useState("");
  const [note, setNote] = useState("");
  const [reason, setReason] = useState("");
  const [mode, setMode] = useState<"reject" | "refund" | null>(null);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ ok: boolean; text: string } | null>(null);

  async function run(call: () => Promise<PaymentOrder>) {
    setBusy(true);
    setFeedback(null);
    try {
      setOverride(await call());
      setFeedback({ ok: true, text: m.done });
      setMode(null);
      setReason("");
      onChanged();
    } catch (err) {
      setFeedback({ ok: false, text: errorText(err, m.failed) });
    } finally {
      setBusy(false);
    }
  }

  function confirm(e: FormEvent) {
    e.preventDefault();
    if (!order) return;
    const code = (currency.trim() || order.currency).toUpperCase();
    const minor = toMinorUnits(received, code);
    if (minor === null) {
      setFeedback({ ok: false, text: m.amountInvalid });
      return;
    }
    void run(() => adminPaymentsApi.confirm(orderId, receipt.trim(), minor, code, note.trim() || null));
  }

  function withReason(e: FormEvent) {
    e.preventDefault();
    if (mode === "reject") void run(() => adminPaymentsApi.reject(orderId, reason.trim()));
    if (mode === "refund") void run(() => adminPaymentsApi.refund(orderId, reason.trim()));
  }

  return (
    <Panel
      title={order ? order.reference : m.reference}
      className="self-start"
      action={
        <Button variant="ghost" size="sm" onClick={onClose} aria-label={m.close}>
          <X size={16} aria-hidden />
        </Button>
      }
    >
      {loaded.isLoading && !order && <LoadingState label={all.loading} />}
      {loaded.error && <ErrorState error={loaded.error} onRetry={loaded.refetch} />}
      {order && (
        <div className="flex flex-col gap-5">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={order.status} />
            <span className="text-sm text-ink-soft">
              {order.customerType === "SCHOOL" ? all.school : order.admin?.familyId ? all.family : all.erased}
            </span>
          </div>
          {order.method === "DEV_SIMULATOR" && <p className="text-sm font-semibold text-light-deep">{m.testOnly}</p>}
          <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
            <Item label={m.plan} value={order.planName} />
            <Item label={m.amount} value={orderPriceText(order)} />
            <Item label={m.method} value={all.method[order.method]} />
            {order.status === "PENDING" && <Item label={m.expires} value={formatDateTime(order.expiresAt)} />}
            {order.periodStart && order.periodEnd && (
              <Item label={m.period} value={`${formatDate(order.periodStart)} – ${formatDate(order.periodEnd)}`} />
            )}
            {order.admin?.providerReference && <Item label={m.providerReference} value={order.admin.providerReference} />}
            {order.customerNote && <Item label={m.customerNote} value={order.customerNote} wide />}
            {order.failureReason && <Item label={m.failure} value={order.failureReason} wide />}
          </dl>

          {order.status === "PENDING" && order.method === "MANUAL" && (
            <form onSubmit={confirm} className="flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line">
              <div>
                <p className="text-sm font-semibold text-ink">{m.confirm}</p>
                <p className="text-sm text-ink-soft">{m.confirmLead}</p>
              </div>
              <FormField label={m.receipt} hint={m.receiptHint} value={receipt} maxLength={200} required onChange={(e) => setReceipt(e.target.value)} />
              <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_7rem]">
                <FormField
                  label={m.received}
                  hint={m.receivedHint(orderPriceText(order))}
                  value={received}
                  inputMode="decimal"
                  required
                  onChange={(e) => setReceived(e.target.value)}
                />
                <FormField
                  label={m.currency}
                  value={currency}
                  placeholder={order.currency}
                  maxLength={3}
                  onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                />
              </div>
              <FormField label={m.note} value={note} maxLength={300} onChange={(e) => setNote(e.target.value)} />
              <Button type="submit" disabled={busy || !receipt.trim() || !received.trim()} className="w-fit">
                {m.confirmAction}
              </Button>
            </form>
          )}

          {order.status === "PENDING" && order.method === "ABA_PAYWAY" && (
            <div className="flex flex-col gap-2">
              <p className="text-sm text-ink-soft">{m.reconcileLead}</p>
              <Button variant="outline" size="sm" className="w-fit" disabled={busy} onClick={() => run(() => adminPaymentsApi.reconcile(orderId))}>
                {m.reconcile}
              </Button>
            </div>
          )}

          {(order.status === "PENDING" && order.method === "MANUAL") || (order.status === "SUCCEEDED" && order.method !== "ABA_PAYWAY") ? (
            <div className="flex flex-col gap-2">
              {mode === null ? (
                <Button
                  variant="ghost"
                  size="sm"
                  className="w-fit"
                  onClick={() => setMode(order.status === "PENDING" ? "reject" : "refund")}
                >
                  {order.status === "PENDING" ? m.reject : m.refund}
                </Button>
              ) : (
                <form onSubmit={withReason} className="flex flex-col gap-3 rounded-[12px] p-4 ring-1 ring-brick/30">
                  <p className="text-sm text-ink-soft">{mode === "reject" ? m.rejectLead : m.refundLead}</p>
                  <FormField label={m.reason} value={reason} maxLength={300} required onChange={(e) => setReason(e.target.value)} />
                  <div className="flex flex-wrap gap-2">
                    <Button type="submit" variant="outline" disabled={busy || !reason.trim()}>
                      {mode === "reject" ? m.rejectAction : m.refundAction}
                    </Button>
                    <Button type="button" variant="ghost" onClick={() => setMode(null)} disabled={busy}>
                      {m.close}
                    </Button>
                  </div>
                </form>
              )}
            </div>
          ) : null}

          {feedback && (
            <p role={feedback.ok ? "status" : "alert"} className={cn("text-sm font-medium", feedback.ok ? "text-palm-deep" : "text-brick-deep")}>
              {feedback.text}
            </p>
          )}

          {order.admin && order.admin.ledger.length > 0 && (
            <section aria-labelledby="payment-ledger" className="flex flex-col gap-2 border-t border-line pt-4">
              <h3 id="payment-ledger" className="text-sm font-semibold text-ink">
                {m.ledger}
              </h3>
              <ol className="flex flex-col gap-2 text-sm">
                {order.admin.ledger.map((entry, i) => (
                  <li key={i} className="flex flex-col">
                    <span className="font-mono text-xs text-ink">{entry.type}</span>
                    <span className="text-xs text-ink-faint">
                      {formatDateTime(entry.at)}
                      {entry.from && entry.to && entry.from !== entry.to && ` · ${all.status[entry.from]} → ${all.status[entry.to]}`}
                      {` · ${m.by} ${entry.actorAccountId ? entry.actorAccountId.slice(0, 8) : m.system}`}
                    </span>
                  </li>
                ))}
              </ol>
            </section>
          )}
        </div>
      )}
    </Panel>
  );
}

function Item({ label, value, wide = false }: { label: string; value: string; wide?: boolean }) {
  return (
    <div className={cn(wide && "col-span-2")}>
      <dt className="text-ink-faint">{label}</dt>
      <dd className="break-words font-medium text-ink">{value}</dd>
    </div>
  );
}

