import { useState } from "react";
import { Info, Send, Undo2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { familyPlanApi } from "@/lib/api/subscriptions";
import { ENTITLEMENT_LABEL, priceText, type Entitlement, type OwnFamilyPlan, type PlanOffer } from "@/lib/api/subscriptions";

const DAY = 24 * 60 * 60 * 1000;
const dateOnly = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" });

const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();

/** "ends today" / "ends tomorrow" / "ends in 5 days" (calendar days) within two weeks of the end; otherwise nothing. */
function soon(iso: string, now: Date): string | null {
  const days = Math.round((startOfDay(new Date(iso)) - startOfDay(now)) / DAY);
  if (days > 14 || days < 0) return null;
  if (days === 0) return "ends today";
  return days === 1 ? "ends tomorrow" : `ends in ${days} days`;
}

/**
 * When the plan runs, in words that match its recorded state. TRIALING, PAST_DUE and CANCELED are
 * only set by a payment provider (none exists yet, PD-09); they're described so a family is never
 * shown a wrong state when one does.
 */
function periodText(plan: OwnFamilyPlan, now: Date): { main: string | null; note: string | null } {
  const sub = plan.subscription;
  if (!sub) return { main: null, note: null };
  if (!sub.grantsAccess) {
    return { main: null, note: sub.currentPeriodEnd ? `An earlier plan ended on ${dateOnly(sub.currentPeriodEnd)}.` : null };
  }
  const end = sub.currentPeriodEnd;
  const closing = end ? soon(end, now) : null;
  const withSoon = (text: string) => (closing ? `${text} · ${closing}` : text);
  switch (sub.status) {
    case "TRIALING":
      return { main: end ? withSoon(`Trial until ${dateOnly(end)}`) : "Trial", note: null };
    case "PAST_DUE":
      return {
        main: end ? `Until ${dateOnly(end)}` : null,
        note: "A payment didn't go through. Your plan continues while it's retried.",
      };
    case "CANCELED":
      return { main: end ? withSoon(`Won't renew · ends ${dateOnly(end)}`) : null, note: null };
    default:
      return {
        main: end ? withSoon(`Until ${dateOnly(end)}`) : "Ongoing",
        note: sub.source === "ADMIN_GRANT" ? "Given to your family by Bookeyond Kid." : null,
      };
  }
}

function usageText(e: Entitlement) {
  if (e.key === "CHILDREN_MAX") {
    const used = e.used ?? 0;
    return e.limit === null ? `${used} added · no limit` : `${used} of ${e.limit}`;
  }
  return e.limit === null ? "No limit" : `Up to ${e.limit}`;
}

/** What having more than a plan includes means: nothing is taken away; only adding more is paused. */
function overText(e: Entitlement, used: number) {
  if (e.key === "CHILDREN_MAX") {
    return `Your family has ${used} children and this plan includes ${e.limit}. Every child keeps learning; you can't add another child on this plan.`;
  }
  return `One of your children follows ${used} journeys at once and this plan includes ${e.limit}. They all continue; that child can't start another one for now.`;
}

const over = (limit: number | null, used: number | null) => limit !== null && used !== null && used > limit;

function OfferLimits({ offer }: { offer: PlanOffer }) {
  return (
    <ul className="flex flex-col gap-0.5 text-sm text-ink">
      {offer.entitlements.map((e) => (
        <li key={e.key}>
          {ENTITLEMENT_LABEL[e.key]}: <span className="font-semibold">{e.limit === null ? "no limit" : e.limit}</span>
        </li>
      ))}
    </ul>
  );
}

/**
 * What the family's plan includes, how much of it is in use, what follows when it ends, and
 * which other plans exist. Nothing here pretends a checkout exists (PLATFORM-EXPANSION.md §3,
 * PD-09): with `onChanged`, a family may *ask* for another plan, which Bookeyond Kid answers (Y11).
 */
export function PlanDetails({
  plan,
  now = new Date(),
  onChanged,
}: {
  plan: OwnFamilyPlan;
  now?: Date;
  onChanged?: (next: OwnFamilyPlan) => void;
}) {
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function act(call: () => Promise<OwnFamilyPlan>) {
    setWorking(true);
    setError(null);
    try {
      onChanged?.(await call());
    } catch {
      setError("That didn't work just now. Please try again.");
    } finally {
      setWorking(false);
    }
  }
  const period = periodText(plan, now);
  const overLimits = plan.entitlements.filter((e) => over(e.limit, e.used));
  const end = plan.subscription?.grantsAccess ? plan.subscription.currentPeriodEnd : null;
  const usedOf = (key: Entitlement["key"]) => plan.entitlements.find((e) => e.key === key)?.used ?? 0;
  const tighterAfter = plan.afterwards?.entitlements.some((e) => over(e.limit, usedOf(e.key))) ?? false;

  return (
    <div className="flex flex-col gap-5">
      <div>
        <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span className="font-display text-lg font-semibold text-tonle-950">{plan.plan.name}</span>
          {period.main && <span className="text-sm text-ink-soft">{period.main}</span>}
        </p>
        {plan.plan.description && <p className="mt-1 text-sm leading-relaxed text-ink-soft">{plan.plan.description}</p>}
        {period.note && <p className="mt-1 text-sm text-ink-soft">{period.note}</p>}
      </div>

      <dl className="grid gap-3 sm:grid-cols-2">
        {plan.entitlements.map((entitlement) => (
          <div key={entitlement.key} className="rounded-[12px] bg-paper px-4 py-3 ring-1 ring-line">
            <dt className="text-sm text-ink-faint">{ENTITLEMENT_LABEL[entitlement.key]}</dt>
            <dd className="tabular font-semibold text-ink">{usageText(entitlement)}</dd>
          </div>
        ))}
      </dl>

      {overLimits.map((e) => (
        <p
          key={e.key}
          className="flex gap-2 rounded-[12px] bg-light-soft/60 px-4 py-3 text-sm leading-relaxed text-ink ring-1 ring-light/40"
        >
          <Info size={16} aria-hidden className="mt-0.5 shrink-0 text-light-deep" />
          <span>{overText(e, e.used ?? 0)}</span>
        </p>
      ))}

      {plan.afterwards && end && (
        <section aria-labelledby="plan-afterwards" className="flex flex-col gap-2 border-t border-line pt-4">
          <h3 id="plan-afterwards" className="text-sm font-semibold text-ink">
            After {dateOnly(end)}
          </h3>
          <p className="text-sm leading-relaxed text-ink-soft">
            Your family moves to <span className="font-semibold text-ink">{plan.afterwards.plan.name}</span>. Nothing your
            children have done is removed.
            {tighterAfter && " It includes less than your family has now: you keep everything, but can't add more beyond it."}
          </p>
          <OfferLimits offer={plan.afterwards} />
        </section>
      )}

      {plan.choices.length > 0 && (
        <section aria-labelledby="plan-choices" className="flex flex-col gap-3 border-t border-line pt-4">
          <div>
            <h3 id="plan-choices" className="text-sm font-semibold text-ink">
              Other plans
            </h3>
            <p className="text-sm text-ink-soft">
              Plans can&apos;t be bought online yet. Ask for one, and Bookeyond Kid replies by email; nothing changes until then.
            </p>
          </div>
          <ul className="grid gap-3 sm:grid-cols-2">
            {plan.choices.map((offer) => (
              <li key={offer.plan.id} className="flex flex-col gap-2 rounded-[12px] px-4 py-3 ring-1 ring-line">
                <p className="flex flex-wrap items-baseline justify-between gap-x-3">
                  <span className="font-semibold text-tonle-950">{offer.plan.name}</span>
                  {offer.plan.price && <span className="tabular text-sm text-ink">{priceText(offer.plan.price)}</span>}
                </p>
                {offer.plan.description && <p className="text-sm text-ink-soft">{offer.plan.description}</p>}
                <OfferLimits offer={offer} />
                {onChanged && plan.request?.planId === offer.plan.id && (
                  <div className="flex flex-col items-start gap-2">
                    <p role="status" className="text-sm font-semibold text-palm-deep">
                      You asked for this plan on {dateOnly(plan.request.requestedAt)}.
                    </p>
                    <Button size="sm" variant="ghost" onClick={() => act(familyPlanApi.withdraw)} disabled={working}>
                      <Undo2 size={15} aria-hidden /> Withdraw the request
                    </Button>
                  </div>
                )}
                {onChanged && plan.request?.planId !== offer.plan.id && (
                  <Button
                    size="sm"
                    variant="outline"
                    className="w-fit"
                    onClick={() => act(() => familyPlanApi.request(offer.plan.id))}
                    disabled={working}
                  >
                    <Send size={15} aria-hidden /> Ask for {offer.plan.name}
                  </Button>
                )}
              </li>
            ))}
          </ul>
          {plan.request && !plan.choices.some((offer) => offer.plan.id === plan.request?.planId) && (
            <p className="text-sm text-ink-soft">You asked for the {plan.request.planName} plan on {dateOnly(plan.request.requestedAt)}.</p>
          )}
          {error && (
            <p role="alert" className="text-sm font-medium text-brick-deep">
              {error}
            </p>
          )}
        </section>
      )}
    </div>
  );
}
