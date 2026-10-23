import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { CreditCard, FlaskConical, RefreshCw, X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { FormField } from "@/features/auth/FormField";
import { useMessages } from "@/i18n/catalog";
import { formatDate, formatDateTime } from "@/i18n/format";
import { familyPlanApi, priceText, type PlanOffer } from "@/lib/api/subscriptions";
import { orderPriceText, paymentsApi, type PaymentMethod, type PaymentOrder } from "@/lib/api/payments";
import { Pager } from "@/components/ui/Pager";
import { TransferInstructions } from "@/features/payments/TransferInstructions";
import type { Page } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { cn } from "@/lib/utils/cn";
import { errorText } from "@/features/admin/studio/studioLogic";
import { PlanDetails } from "./PlanDetails";
import { paymentMessages } from "./payments.messages";

const dateOnly = (iso: string) => formatDate(iso, { day: "numeric", month: "long", year: "numeric" });

/**
 * The family's plan with paying for one (Y43). The browser only ever names a plan and a way of paying:
 * the server sets the amount, and the plan changes only once the payment is confirmed. With no way of
 * paying switched on, the family can still ask for a plan as before (Y11).
 */
export function FamilyPlanWithPayments() {
  const [version, setVersion] = useState(0);
  const plan = useAsync(() => familyPlanApi.get(), [version]);
  const methods = useAsync(() => paymentsApi.methods(), []);
  const orders = useAsync(() => paymentsApi.familyOrders(), [version]);
  const [buying, setBuying] = useState<PlanOffer | null>(null);
  const [outcome, setOutcome] = useState<{ ok: boolean; text: string } | null>(null);
  const [justStarted, setJustStarted] = useState(false);
  const m = useMessages(paymentMessages);
  // A pending payment is always the newest (no new one starts while it waits), so page 0 shows it.
  const pending = orders.data?.items.find((order) => order.status === "PENDING") ?? null;
  // Real ways of paying first; a test payment (never offered in production) last.
  const available = [...(methods.data?.methods ?? [])].sort((a, b) => Number(a === "DEV_SIMULATOR") - Number(b === "DEV_SIMULATOR"));

  function settled(order: PaymentOrder) {
    setBuying(null);
    if (order.status === "SUCCEEDED" && order.periodEnd) setOutcome({ ok: true, text: m.paid(order.planName, dateOnly(order.periodEnd)) });
    else if (order.status === "CANCELLED") setOutcome({ ok: true, text: m.cancelled });
    else if (order.status === "FAILED" || order.status === "EXPIRED") setOutcome({ ok: false, text: m.failed });
    setVersion((v) => v + 1);
  }

  return (
    <div className="flex flex-col gap-5">
      {plan.isLoading && !plan.data && <LoadingState label={m.loading} />}
      {plan.error && <ErrorState error={plan.error} onRetry={plan.refetch} />}
      {outcome && (
        <p
          role="status"
          className={cn(
            "rounded-[12px] px-4 py-3 text-sm font-medium ring-1",
            outcome.ok ? "bg-palm-soft/50 text-palm-deep ring-palm/30" : "bg-brick-soft/40 text-brick-deep ring-brick/30",
          )}
        >
          {outcome.text}
        </p>
      )}
      {pending && <PendingPayment key={pending.id} orderId={pending.id} onSettled={settled} focusOnShow={justStarted} />}
      {buying && available.length > 0 && (
        <Checkout
          offer={buying}
          renewing={!!plan.data && !plan.data.onDefaultPlan && plan.data.plan.id === buying.plan.id}
          smaller={buying.entitlements.some((e) => {
            const used = plan.data?.entitlements.find((now) => now.key === e.key)?.used;
            return e.limit !== null && used != null && used > e.limit;
          })}
          methods={available}
          onClose={() => setBuying(null)}
          onStarted={() => {
            setBuying(null);
            setJustStarted(true);
            setVersion((v) => v + 1);
          }}
        />
      )}
      {plan.data && (
        <PlanDetails
          plan={plan.data}
          onChanged={() => setVersion((v) => v + 1)}
          purchase={{
            methods: available,
            locked: pending !== null || buying !== null,
            onChoose: (offer) => {
              setOutcome(null);
              setBuying(offer);
            },
          }}
        />
      )}
      {orders.data && orders.data.total > (pending ? 1 : 0) && <History key={version} first={orders.data} />}
    </div>
  );
}

function Checkout({
  offer,
  renewing,
  smaller,
  methods,
  onClose,
  onStarted,
}: {
  offer: PlanOffer;
  /** Another period of the plan the family has: it follows on from the current one. */
  renewing: boolean;
  /** The plan includes less than the family holds now. */
  smaller: boolean;
  methods: PaymentMethod[];
  onClose: () => void;
  onStarted: () => void;
}) {
  const m = useMessages(paymentMessages);
  const heading = useRef<HTMLHeadingElement>(null);
  const name = useId();
  const [method, setMethod] = useState<PaymentMethod>(methods[0]);
  // One key for this checkout: pressing Continue twice, or retrying after a lost reply, is the same order.
  const [key] = useState(() => crypto.randomUUID());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const price = offer.plan.price!;

  useEffect(() => heading.current?.focus(), []);

  async function start(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await paymentsApi.start(offer.plan.id, method, key);
      onStarted();
    } catch (err) {
      setError(errorText(err, m.error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby={`${name}-title`} className="flex flex-col gap-4 rounded-[16px] bg-white p-5 ring-1 ring-tonle-200 sm:p-6">
      <h3 id={`${name}-title`} ref={heading} tabIndex={-1} className="font-display text-lg font-semibold text-tonle-950 focus:outline-none">
        {m.checkoutTitle(offer.plan.name)}
      </h3>
      <form onSubmit={start} className="flex flex-col gap-4">
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-sm font-semibold text-ink">{m.howToPay}</legend>
          {methods.map((option) => (
            <label
              key={option}
              className={cn(
                "flex cursor-pointer gap-3 rounded-[12px] px-4 py-3 ring-1 transition-colors",
                method === option ? "bg-tonle-50/70 ring-tonle-600" : "ring-line hover:bg-paper",
              )}
            >
              <input
                type="radio"
                name={name}
                value={option}
                checked={method === option}
                onChange={() => setMethod(option)}
                className="mt-1 size-4 accent-tonle-700"
              />
              <span>
                <span className="block font-semibold text-ink">
                  {m.method[option]}
                  {option === "DEV_SIMULATOR" && <FlaskConical size={14} aria-hidden className="ml-1.5 inline text-light-deep" />}
                </span>
                <span className="block text-sm text-ink-soft">{m.methodHint[option]}</span>
              </span>
            </label>
          ))}
        </fieldset>
        <div className="flex items-baseline justify-between gap-3 border-t border-line pt-3">
          <span className="text-sm text-ink-soft">{m.total}</span>
          <span className="tabular font-display text-xl font-semibold text-tonle-950">{priceText(price)}</span>
        </div>
        <p className="text-sm text-ink-soft">
          {(renewing ? m.extendsWhen : m.startsWhen)(price.interval === "MONTH" ? m.month : m.year)}
        </p>
        {smaller && !renewing && (
          <p className="rounded-[12px] bg-light-soft/60 px-4 py-3 text-sm leading-relaxed text-ink ring-1 ring-light/40">{m.smaller}</p>
        )}
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <div className="flex flex-wrap gap-2">
          <Button type="submit" variant="accent" disabled={busy}>
            <CreditCard size={16} aria-hidden /> {busy ? m.starting : m.start}
          </Button>
          <Button type="button" variant="ghost" onClick={onClose} disabled={busy}>
            {m.back}
          </Button>
        </div>
      </form>
    </section>
  );
}

/** The payment in progress: what to pay, how, and the way out. */
function PendingPayment({
  orderId,
  onSettled,
  focusOnShow,
}: {
  orderId: string;
  onSettled: (order: PaymentOrder) => void;
  /** Just started here: take the family to it. */
  focusOnShow: boolean;
}) {
  const m = useMessages(paymentMessages);
  const [version, setVersion] = useState(0);
  const order = useAsync(() => paymentsApi.order(orderId), [orderId, version]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [noteSaved, setNoteSaved] = useState(false);
  const titleId = useId();
  const heading = useRef<HTMLHeadingElement>(null);
  const data = order.data;
  const hasData = data !== null;
  const settledRef = useRef(onSettled);
  settledRef.current = onSettled;

  useEffect(() => {
    if (focusOnShow && hasData) heading.current?.focus();
  }, [focusOnShow, hasData]);

  // "Check again" (or a reply from PayWay meanwhile) may find it settled.
  useEffect(() => {
    if (data && data.status !== "PENDING") settledRef.current(data);
  }, [data]);

  async function act(call: () => Promise<PaymentOrder>): Promise<boolean> {
    setBusy(true);
    setError(null);
    try {
      const next = await call();
      if (next.status !== "PENDING") onSettled(next);
      return true;
    } catch (err) {
      setError(errorText(err, m.error));
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function saveNote(e: FormEvent) {
    e.preventDefault();
    setNoteSaved(false);
    setNoteSaved(await act(() => paymentsApi.note(orderId, note.trim())));
  }

  if (order.error) return <ErrorState error={order.error} onRetry={order.refetch} />;
  if (!data) return <LoadingState label={m.loading} />;
  const checkout = data.checkout;

  return (
    <section aria-labelledby={titleId} className="flex flex-col gap-4 rounded-[16px] bg-white p-5 ring-1 ring-light/50 sm:p-6">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h3 id={titleId} ref={heading} tabIndex={-1} className="font-display text-lg font-semibold text-tonle-950 focus:outline-none">
          {m.inProgress}
        </h3>
        <span className="text-sm text-ink-soft">{m.payBy(formatDateTime(data.expiresAt))}</span>
      </div>
      <dl className="grid gap-3 sm:grid-cols-3">
        <div>
          <dt className="text-sm text-ink-faint">{data.planName}</dt>
          <dd className="tabular font-semibold text-ink">{orderPriceText(data)}</dd>
        </div>
        <div>
          <dt className="text-sm text-ink-faint">{m.reference}</dt>
          <dd className="font-mono text-base font-semibold tracking-wider text-tonle-950">{data.reference}</dd>
        </div>
        <div>
          <dt className="text-sm text-ink-faint">{m.payingBy}</dt>
          <dd className="font-semibold text-ink">{m.method[data.method]}</dd>
        </div>
      </dl>

      {data.method === "MANUAL" && (
        <>
          <p className="text-sm leading-relaxed text-ink">{m.manualLead}</p>
          {checkout?.instructions && <TransferInstructions order={data} />}
          <form onSubmit={saveNote} className="flex flex-col items-start gap-2">
            <div className="w-full">
              <FormField
                label={m.noteLabel}
                hint={m.noteHint}
                value={note}
                maxLength={300}
                onChange={(e) => {
                  setNote(e.target.value);
                  setNoteSaved(false);
                }}
              />
            </div>
            <Button type="submit" variant="outline" disabled={busy || !note.trim()}>
              {m.saveNote}
            </Button>
          </form>
          {noteSaved && (
            <p role="status" className="text-sm text-palm-deep">
              {m.noteSaved}
            </p>
          )}
        </>
      )}

      {data.method === "ABA_PAYWAY" && checkout?.action && checkout.fields && (
        <form method="post" action={checkout.action} className="flex flex-col items-start gap-2">
          {Object.entries(checkout.fields).map(([field, value]) => (
            <input key={field} type="hidden" name={field} value={value} />
          ))}
          <p className="text-sm text-ink-soft">{m.paywayLead}</p>
          <Button type="submit" variant="accent">
            <CreditCard size={16} aria-hidden /> {m.payway}
          </Button>
        </form>
      )}

      {data.method === "DEV_SIMULATOR" && (
        <div className="flex flex-col gap-2 rounded-[12px] border border-dashed border-light/70 bg-light-soft/30 px-4 py-3">
          <p className="flex items-center gap-2 text-sm font-semibold text-light-deep">
            <FlaskConical size={15} aria-hidden /> {m.testMode}
          </p>
          {checkout?.instructions && <p className="text-sm text-ink-soft">{checkout.instructions}</p>}
          <div className="flex flex-wrap gap-2">
            {(["SUCCEEDED", "FAILED", "CANCELLED"] as const).map((outcome) => (
              <Button key={outcome} size="sm" variant="outline" disabled={busy} onClick={() => act(() => paymentsApi.simulate(orderId, outcome))}>
                {m.simulate[outcome]}
              </Button>
            ))}
          </div>
        </div>
      )}

      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-2 border-t border-line pt-3">
        <Button size="sm" variant="ghost" onClick={() => setVersion((v) => v + 1)} disabled={busy || order.isLoading}>
          <RefreshCw size={15} aria-hidden /> {m.checkAgain}
        </Button>
        <Button size="sm" variant="ghost" onClick={() => act(() => paymentsApi.cancel(orderId))} disabled={busy}>
          <X size={15} aria-hidden /> {m.cancel}
        </Button>
      </div>
    </section>
  );
}

/** Every payment the family made, a page at a time (M7: none is cut off). */
function History({ first }: { first: Page<PaymentOrder> }) {
  const m = useMessages(paymentMessages);
  const titleId = useId();
  const [page, setPage] = useState(0);
  const later = useAsync(() => (page === 0 ? Promise.resolve(first) : paymentsApi.familyOrders(page)), [page, first]);
  const shown = page === 0 ? first : later.data;
  const orders = shown?.items ?? [];
  return (
    <section aria-labelledby={titleId} className="flex flex-col gap-2 border-t border-line pt-4">
      <h3 id={titleId} className="text-sm font-semibold text-ink">
        {m.history}
      </h3>
      <ul className="divide-y divide-line text-sm">
        {orders
          .filter((order) => order.status !== "PENDING")
          .map((order) => (
            <li key={order.id} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-2">
              <span className="text-ink">
                {order.planName} · <span className="tabular">{orderPriceText(order)}</span>
                <span className="block text-xs text-ink-faint">
                  {formatDate(order.createdAt)} · <span className="font-mono">{order.reference}</span>
                </span>
              </span>
              <span className={cn("font-semibold", order.status === "SUCCEEDED" ? "text-palm-deep" : "text-ink-soft")}>
                {m.status[order.status]}
              </span>
            </li>
          ))}
      </ul>
      {later.error && page > 0 && <ErrorState error={later.error} onRetry={later.refetch} />}
      <Pager page={page} size={first.size} total={first.total} onPage={setPage} label={m.history} />
    </section>
  );
}
