/**
 * A plan chosen before registering (2026-11-11): shown on the registration pages, and - while a paid one waits for its
 * payment - in the family's and the principal's areas, with the way to pay and the way back to the free plan.
 */
import { useState } from "react";
import { Link } from "react-router-dom";
import { CreditCard, Sparkles } from "lucide-react";
import { Button, buttonClasses } from "@/components/ui/Button";
import { useMessages } from "@/i18n/catalog";
import { chosenPrice, planChoiceApi, type ChosenPlan } from "@/lib/api/planChoice";
import { useAsync } from "@/lib/hooks/useAsync";
import { plansMessages } from "./plans.messages";

/** The plan as chosen, on the registration pages: name, price, and when it starts. */
export function ChosenPlanSummary({ plan, changeTo }: { plan: Pick<ChosenPlan, "name" | "audience" | "isDefault" | "amountMinor" | "currency" | "billingInterval">; changeTo?: string }) {
  const m = useMessages(plansMessages);
  const price = chosenPrice(plan);
  const note = price === null ? m.freeNote : plan.audience === "SCHOOL" ? m.schoolPaidNote : m.paidNote;
  return (
    <section aria-label={m.yourPlan} className="flex flex-col gap-1.5 rounded-[14px] bg-tonle-50/70 p-4 ring-1 ring-inset ring-tonle-100">
      <div className="flex items-baseline justify-between gap-3">
        <p className="text-xs font-semibold text-ink-soft">{m.yourPlan}</p>
        {changeTo && (
          <Link to={changeTo} className="text-sm font-semibold text-tonle-700 underline-offset-4 hover:underline">
            {m.change}
          </Link>
        )}
      </div>
      <p className="font-display text-lg font-bold leading-snug text-tonle-950 [overflow-wrap:anywhere]">{plan.name}</p>
      <p className="tabular font-semibold text-ink">{price ?? m.nothingToPay}</p>
      <p className="text-sm leading-relaxed text-ink-soft">{note}</p>
    </section>
  );
}

/**
 * A paid plan chosen before registering, still waiting for its payment. Families go to their plan's checkout; a
 * principal to the school's Plan page. Either can keep the free plan instead. Nothing shows when nothing waits.
 */
export function PlanChoiceNotice({
  payAt,
  school = false,
  onKept,
  level = 2,
}: {
  /** The heading's level where it stands (3 inside the family's plan section). */
  level?: 2 | 3;
  /** Where paying happens; none when the notice is already there (the family's plan section). */
  payAt?: string;
  school?: boolean;
  onKept?: () => void;
}) {
  const m = useMessages(plansMessages);
  const choice = useAsync(() => planChoiceApi.mine(), []);
  const [hidden, setHidden] = useState(false);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const plan = choice.data;
  if (!plan || hidden) return null;
  const price = chosenPrice(plan);
  const Heading = level === 3 ? "h3" : "h2";

  async function keepFree() {
    setBusy(true);
    setFailed(false);
    try {
      await planChoiceApi.setAside();
      setHidden(true);
      onKept?.();
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="plan-choice-title" className="flex flex-col gap-4 rounded-[20px] bg-white p-5 shadow-card ring-1 ring-light/40 sm:p-6">
      <div className="flex items-start gap-3">
        <Sparkles size={20} aria-hidden className="mt-1 shrink-0 text-light-deep" />
        <div className="flex min-w-0 flex-col gap-1">
          <Heading id="plan-choice-title" className="font-display text-xl font-bold text-tonle-950 [overflow-wrap:anywhere]">
            {m.waitingTitle(plan.name)}
            {price && <span className="tabular font-sans text-base font-semibold text-ink"> · {price}</span>}
          </Heading>
          <p className="leading-relaxed text-ink-soft">{!plan.offered ? m.waitingGone : school ? m.waitingSchool : m.waitingFamily}</p>
        </div>
      </div>
      {failed && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {m.keepFreeFailed}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        {plan.offered && payAt && (
          <Link to={payAt} className={buttonClasses("accent", "md")}>
            <CreditCard size={16} aria-hidden /> {school ? m.goToPlan : m.continuePayment}
          </Link>
        )}
        <Button variant={plan.offered ? "ghost" : "outline"} size="md" disabled={busy} onClick={() => void keepFree()}>
          {busy ? m.keepingFree : plan.offered ? m.keepFree : m.dismiss}
        </Button>
      </div>
    </section>
  );
}
