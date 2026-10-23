import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { CreditCard, PauseCircle } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { useMessages } from "@/i18n/catalog";
import { paymentsApi, type PaymentMethod, type SchoolPlanChoice } from "@/lib/api/payments";
import { entitlementLabel, priceText, type OwnSchoolPlan } from "@/lib/api/subscriptions";
import { useAsync } from "@/lib/hooks/useAsync";
import { errorText } from "@/features/admin/studio/studioLogic";
import { paymentMessages } from "@/features/parent/payments.messages";
import { schoolPlanMessages } from "./plan.messages";
import { planSourceText } from "./planUsage";
import { SchoolInvoices } from "./SchoolInvoices";

/**
 * Paying for a school's plan. Renewing (Y44): the plan its last confirmed payment bought, at that plan's price now.
 * Choosing (M4): before a first payment, or after the paid period ended, the school plans the Bookeyond team offers
 * schools, each with its price and what it includes. Without any, this says how to get one. The server decides the
 * amount; the plan opens only once the payment is confirmed.
 */
export function SchoolRenew({ schoolId, first, onStarted }: { schoolId: string; first: boolean; onStarted: () => void }) {
  const m = useMessages(schoolPlanMessages);
  const pay = useMessages(paymentMessages);
  const choices = useAsync(() => paymentsApi.schoolRenewals(schoolId), [schoolId]);
  const methods = useAsync(() => paymentsApi.methods(), []);
  const available = [...(methods.data?.methods ?? [])].sort((a, b) => Number(a === "DEV_SIMULATOR") - Number(b === "DEV_SIMULATOR"));
  const [method, setMethod] = useState<PaymentMethod | null>(null);
  const [planId, setPlanId] = useState<string | null>(null);
  const [key] = useState(() => crypto.randomUUID());
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ ok: boolean; text: string } | null>(null);
  const plans = choices.data ?? [];
  const plan = plans.find((p) => p.id === planId) ?? plans[0];
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

  const priceOf = (p: SchoolPlanChoice) => priceText({ amountMinor: p.amountMinor, currency: p.currency, interval: p.billingInterval }) ?? "";
  const choosing = first || plans.length > 1;
  return (
    <form onSubmit={start} className="flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line">
      <p className="text-sm font-semibold text-ink">{choosing ? m.chooseTitle : m.renewTitle}</p>
      {choosing && <p className="max-w-prose text-sm text-ink-soft">{m.chooseLead}</p>}
      {choosing && (
        <fieldset className="grid gap-2 sm:grid-cols-2">
          <legend className="sr-only">{m.choosePlan}</legend>
          {plans.map((option) => (
            <label
              key={option.id}
              className="flex cursor-pointer gap-3 rounded-[12px] bg-white p-3.5 ring-1 ring-line has-[:checked]:ring-2 has-[:checked]:ring-tonle-600"
            >
              <input
                type="radio"
                name={`plan-${schoolId}`}
                value={option.id}
                checked={plan.id === option.id}
                onChange={() => setPlanId(option.id)}
                className="mt-1 accent-tonle-700"
              />
              <span className="flex flex-col gap-1">
                <span className="font-semibold text-ink">
                  {option.name} <span className="tabular font-normal text-ink-soft">· {priceOf(option)}</span>
                </span>
                {option.description && <span className="text-sm text-ink-soft">{option.description}</span>}
                <PlanLimits limits={option.limits} />
              </span>
            </label>
          ))}
        </fieldset>
      )}
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
        <CreditCard size={16} aria-hidden /> {busy ? m.renewing : choosing ? m.payFor(plan.name, priceOf(plan)) : m.renew(plan.name, priceOf(plan))}
      </Button>
      {feedback && (
        <p role={feedback.ok ? "status" : "alert"} className={feedback.ok ? "text-sm text-palm-deep" : "text-sm font-medium text-brick-deep"}>
          {feedback.text}
        </p>
      )}
    </form>
  );
}

/** What a plan includes, one short line per limit. */
function PlanLimits({ limits }: { limits: SchoolPlanChoice["limits"] }) {
  const m = useMessages(schoolPlanMessages);
  const keys = (["SCHOOL_CLASSES_MAX", "SCHOOL_TEACHERS_MAX", "SCHOOL_CHILDREN_MAX"] as const).filter((k) => k in limits);
  return (
    <ul className="flex flex-col gap-0.5 text-sm text-ink-soft">
      {keys.map((k) => (
        <li key={k}>{m.limitItem(entitlementLabel(k, true), limits[k] ?? null)}</li>
      ))}
      {"TEACHER_STORAGE_MB" in limits && <li>{m.storageItem(limits.TEACHER_STORAGE_MB ?? null)}</li>}
    </ul>
  );
}

/**
 * What a principal sees instead of the school's services while they aren't paid (Y44): why, that nothing is lost,
 * and the way back - a payment waiting, the invoice and its instructions, or a renewal.
 */
export function SchoolPaymentRequired({ schoolId, schoolName, plan }: { schoolId: string; schoolName: string; plan: OwnSchoolPlan }) {
  const m = useMessages(schoolPlanMessages);
  const [version, setVersion] = useState(0);
  // M4: a school that has never paid is opening, not paused.
  const first = plan.subscription?.source !== "PROVIDER";
  return (
    <section aria-labelledby="school-paused" className="flex flex-col gap-4 rounded-[20px] bg-white px-5 py-6 shadow-card sm:px-7">
      <div className="flex gap-3">
        <PauseCircle size={28} aria-hidden className="mt-0.5 shrink-0 text-light-deep" />
        <div className="flex flex-col gap-1.5">
          <h2 id="school-paused" className="font-display text-xl font-semibold text-tonle-950">
            {first ? m.openTitle(schoolName) : m.pausedTitle(schoolName)}
          </h2>
          <p className="max-w-prose text-[0.9375rem] leading-relaxed text-ink-soft">
            {plan.access.paymentPending ? m.pausedPending : first ? m.openLead : m.pausedLead}
          </p>
          <p className="max-w-prose text-sm text-ink">{planSourceText(plan, m)}</p>
        </div>
      </div>
      {!plan.access.paymentPending && (
        <SchoolRenew schoolId={schoolId} first={first} onStarted={() => setVersion((v) => v + 1)} />
      )}
      <SchoolInvoices key={version} schoolId={schoolId} />
      <Link to="/principal/plan" className="w-fit text-sm font-semibold text-tonle-700 underline-offset-4 hover:underline">
        {m.seePlan}
      </Link>
    </section>
  );
}
