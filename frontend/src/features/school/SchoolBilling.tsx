import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { CreditCard, PauseCircle } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { useMessages } from "@/i18n/catalog";
import { paymentsApi, type PaymentMethod } from "@/lib/api/payments";
import { priceText, type OwnSchoolPlan } from "@/lib/api/subscriptions";
import { useAsync } from "@/lib/hooks/useAsync";
import { errorText } from "@/features/admin/studio/studioLogic";
import { paymentMessages } from "@/features/parent/payments.messages";
import { schoolPlanMessages } from "./plan.messages";
import { planSourceText } from "./planUsage";
import { SchoolInvoices } from "./SchoolInvoices";

/**
 * Renewing a school's plan (Y44): the plan its last confirmed payment bought, at that plan's price now, by a way of
 * paying that is switched on. A school's first plan is invoiced by the Bookeyond team, so before a first payment
 * this says how to get one. The server decides the amount; the plan opens only once the payment is confirmed.
 */
export function SchoolRenew({ schoolId, onStarted }: { schoolId: string; onStarted: () => void }) {
  const m = useMessages(schoolPlanMessages);
  const pay = useMessages(paymentMessages);
  const choices = useAsync(() => paymentsApi.schoolRenewals(schoolId), [schoolId]);
  const methods = useAsync(() => paymentsApi.methods(), []);
  const available = [...(methods.data?.methods ?? [])].sort((a, b) => Number(a === "DEV_SIMULATOR") - Number(b === "DEV_SIMULATOR"));
  const [method, setMethod] = useState<PaymentMethod | null>(null);
  const [key] = useState(() => crypto.randomUUID());
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ ok: boolean; text: string } | null>(null);
  const plan = choices.data?.[0];
  const chosen = method ?? available[0] ?? null;

  if (!choices.data || !methods.data) return null;
  if (!plan || !chosen) return <p className="text-sm text-ink-soft">{m.askTeam}</p>;

  async function start(e: FormEvent) {
    e.preventDefault();
    if (!plan || !chosen) return;
    setBusy(true);
    setFeedback(null);
    try {
      await paymentsApi.renewSchool(schoolId, plan.id, chosen, key);
      setFeedback({ ok: true, text: m.renewed });
      onStarted();
    } catch (err) {
      setFeedback({ ok: false, text: errorText(err, m.renewFailed) });
    } finally {
      setBusy(false);
    }
  }

  const price = priceText({ amountMinor: plan.amountMinor, currency: plan.currency, interval: plan.billingInterval }) ?? "";
  return (
    <form onSubmit={start} className="flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line">
      <p className="text-sm font-semibold text-ink">{m.renewTitle}</p>
      {available.length > 1 && (
        <fieldset className="flex flex-wrap gap-2">
          <legend className="mb-2 text-sm text-ink-soft">{m.renewMethod}</legend>
          {available.map((option) => (
            <label key={option} className="flex min-h-11 cursor-pointer items-center gap-2 rounded-full px-4 text-sm ring-1 ring-line has-[:checked]:bg-tonle-50 has-[:checked]:ring-tonle-600">
              <input type="radio" name={`renew-${schoolId}`} value={option} checked={chosen === option} onChange={() => setMethod(option)} className="accent-tonle-700" />
              {pay.method[option]}
            </label>
          ))}
        </fieldset>
      )}
      <Button type="submit" variant="accent" className="w-fit" disabled={busy || feedback?.ok === true}>
        <CreditCard size={16} aria-hidden /> {busy ? m.renewing : m.renew(plan.name, price)}
      </Button>
      {feedback && (
        <p role={feedback.ok ? "status" : "alert"} className={feedback.ok ? "text-sm text-palm-deep" : "text-sm font-medium text-brick-deep"}>
          {feedback.text}
        </p>
      )}
    </form>
  );
}

/**
 * What a principal sees instead of the school's services while they aren't paid (Y44): why, that nothing is lost,
 * and the way back - a payment waiting, the invoice and its instructions, or a renewal.
 */
export function SchoolPaymentRequired({ schoolId, schoolName, plan }: { schoolId: string; schoolName: string; plan: OwnSchoolPlan }) {
  const m = useMessages(schoolPlanMessages);
  const [version, setVersion] = useState(0);
  return (
    <section aria-labelledby="school-paused" className="flex flex-col gap-4 rounded-[20px] bg-white px-5 py-6 shadow-card sm:px-7">
      <div className="flex gap-3">
        <PauseCircle size={28} aria-hidden className="mt-0.5 shrink-0 text-light-deep" />
        <div className="flex flex-col gap-1.5">
          <h2 id="school-paused" className="font-display text-xl font-semibold text-tonle-950">
            {m.pausedTitle(schoolName)}
          </h2>
          <p className="max-w-prose text-[0.9375rem] leading-relaxed text-ink-soft">
            {plan.access.paymentPending ? m.pausedPending : m.pausedLead}
          </p>
          <p className="max-w-prose text-sm text-ink">{planSourceText(plan, m)}</p>
        </div>
      </div>
      {!plan.access.paymentPending && <SchoolRenew schoolId={schoolId} onStarted={() => setVersion((v) => v + 1)} />}
      <SchoolInvoices key={version} schoolId={schoolId} />
      <Link to="/principal/plan" className="w-fit text-sm font-semibold text-tonle-700 underline-offset-4 hover:underline">
        {m.seePlan}
      </Link>
    </section>
  );
}
