/**
 * The plans, before registering (owner requirement 2026-11-11): a visitor chooses Family or School, sees each plan the
 * records offer - price, what it includes, and for families the subjects - and chooses one. The choice is only a plan's
 * code in the link to registration; the server checks it against the records and keeps it (never a price from here).
 */
import { useEffect } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ArrowRight, Check, Home, School } from "lucide-react";
import { buttonClasses } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { SkipLink } from "@/components/ui/SkipLink";
import { Nav } from "@/features/marketing/sections/Nav";
import { Footer } from "@/features/marketing/sections/Footer";
import { siteApi, type PublicPlan, type PublicSubject } from "@/lib/api/site";
import { entitlementLabel, priceText } from "@/lib/api/subscriptions";
import { useAsync } from "@/lib/hooks/useAsync";
import { useLocale, useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { plansMessages } from "./plans.messages";

type Audience = "FAMILY" | "SCHOOL";

/** Limits in the order a person weighs them: who can learn, then how much each learns. */
const LIMIT_ORDER = ["CHILDREN_MAX", "JOURNEYS_PER_CHILD_MAX", "ACTIVE_JOURNEYS_PER_CHILD_MAX"];
const limitRank = (key: string) => (LIMIT_ORDER.indexOf(key) + LIMIT_ORDER.length + 1) % (LIMIT_ORDER.length + 1);

/** Where choosing a plan leads: registration, for the plan's kind of account. */
function registerPath(audience: Audience, code?: string) {
  const as = audience === "SCHOOL" ? "principal" : "parent";
  return code ? `/register?plan=${encodeURIComponent(code)}&as=${as}` : `/register?as=${as}`;
}

export function PlansPage() {
  const m = useMessages(plansMessages);
  const locale = useLocale();
  const [params, setParams] = useSearchParams();
  const audience: Audience = params.get("for") === "school" ? "SCHOOL" : "FAMILY";
  const plans = useAsync(() => siteApi.plans(), [locale]);
  const subjects = useAsync(() => siteApi.subjects(), [locale]);

  useEffect(() => {
    document.title = m.documentTitle;
  }, [m.documentTitle]);

  const shown = plans.data?.filter((p) => p.audience === audience) ?? [];
  const how = audience === "SCHOOL" ? m.howSchool : m.howFamily;
  const steps = [how.a, how.b, how.c, how.d];

  return (
    <div className="min-h-screen overflow-x-clip bg-paper">
      <SkipLink />
      <Nav />
      <main id="main" tabIndex={-1} className="mx-auto flex max-w-[1240px] flex-col gap-10 px-4 pb-24 pt-12 sm:px-8 sm:pt-16">
        <header className="flex max-w-3xl flex-col gap-4">
          <p className="text-sm font-bold uppercase tracking-[0.14em] text-palm-deep">{m.eyebrow}</p>
          <h1 className="font-display text-[2.4rem] font-bold leading-[1.05] tracking-[-0.015em] text-tonle-950 sm:text-[3.25rem]">
            {m.title}
          </h1>
          <p className="max-w-2xl text-lg leading-relaxed text-ink-soft">{m.lead}</p>
        </header>

        <div role="group" aria-label={m.audience} className="grid w-full max-w-md grid-cols-2 gap-1 rounded-[16px] bg-white p-1.5 ring-1 ring-line">
          {(["FAMILY", "SCHOOL"] as const).map((value) => {
            const Icon = value === "SCHOOL" ? School : Home;
            const on = audience === value;
            return (
              <button
                key={value}
                type="button"
                aria-pressed={on}
                onClick={() => setParams(value === "SCHOOL" ? { for: "school" } : {}, { replace: true })}
                className={cn(
                  "flex min-h-12 items-center justify-center gap-2 rounded-[12px] px-3 text-[0.9375rem] font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tonle-600",
                  on ? "bg-tonle-800 text-white shadow-card" : "text-tonle-900 hover:bg-tonle-50",
                )}
              >
                <Icon size={18} aria-hidden /> {value === "SCHOOL" ? m.forSchool : m.forFamily}
              </button>
            );
          })}
        </div>

        {plans.isLoading && !plans.data && <LoadingState label={m.loading} />}
        {plans.error && <ErrorState error={plans.error} onRetry={plans.refetch} />}
        {plans.data && shown.length === 0 && (
          <section className="flex max-w-2xl flex-col items-start gap-5 rounded-[24px] bg-white p-7 shadow-card sm:p-9">
            <p className="text-lg leading-relaxed text-ink">{audience === "SCHOOL" ? m.noPlansSchool : m.noPlansFamily}</p>
            <Link to={registerPath(audience)} className={buttonClasses("solid", "lg")}>
              {audience === "SCHOOL" ? m.registerSchool : m.registerFamily} <ArrowRight size={18} aria-hidden />
            </Link>
          </section>
        )}
        {shown.length > 0 && (
          <ul className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3" aria-live="polite">
            {shown.map((plan) => (
              <li key={plan.code} className="flex">
                <PlanCard plan={plan} subjects={subjects.data ?? null} />
              </li>
            ))}
          </ul>
        )}

        <section aria-labelledby="plans-how" className="flex max-w-3xl flex-col gap-5 border-t border-line pt-10">
          <h2 id="plans-how" className="font-display text-2xl font-bold text-tonle-950">
            {m.howTitle}
          </h2>
          <ol className="flex flex-col gap-4">
            {steps.map((step, i) => (
              <li key={step} className="flex gap-4">
                <span aria-hidden className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-tonle-50 font-display font-bold text-tonle-800 ring-1 ring-tonle-100">
                  {i + 1}
                </span>
                <span className="pt-1 leading-relaxed text-ink">{step}</span>
              </li>
            ))}
          </ol>
        </section>
      </main>
      <Footer />
    </div>
  );
}

function PlanCard({ plan, subjects }: { plan: PublicPlan; subjects: PublicSubject[] | null }) {
  const m = useMessages(plansMessages);
  const audience = plan.audience;
  const price =
    plan.amountMinor !== null && plan.currency && plan.billingInterval
      ? priceText({ amountMinor: plan.amountMinor, currency: plan.currency, interval: plan.billingInterval })
      : null;
  const id = `plan-${plan.code}`;
  const learnable = subjects?.filter((s) => s.available) ?? null;
  const included = plan.subjects ?? null;
  const subjectLine =
    audience !== "FAMILY" || included === null || learnable === null
      ? null
      : learnable.length === 0
        ? m.noSubjects
        : included.length >= learnable.length
          ? m.allSubjects
          : learnable.filter((s) => included.includes(s.code)).map((s) => s.name).join(", ") || m.noSubjects;

  return (
    <article
      aria-labelledby={id}
      className={cn(
        "flex w-full flex-col gap-6 rounded-[24px] p-7 sm:p-8",
        plan.isDefault ? "bg-white shadow-card ring-1 ring-line" : "bg-tonle-50/60 ring-1 ring-tonle-100",
      )}
    >
      <div className="flex flex-col gap-2">
        {plan.isDefault && (
          <p className="w-fit rounded-pill bg-palm-soft px-3 py-1 text-xs font-bold text-palm-deep">{m.defaultBadge}</p>
        )}
        <h2 id={id} className="font-display text-[1.75rem] font-bold leading-tight text-tonle-950 [overflow-wrap:anywhere]">
          {plan.name}
        </h2>
        <p className="tabular text-xl font-semibold text-ink">{price ?? m.nothingToPay}</p>
        {plan.description && <p className="leading-relaxed text-ink-soft [overflow-wrap:anywhere]">{plan.description}</p>}
      </div>

      {(plan.limits.length > 0 || subjectLine) && (
        <div className="flex flex-col gap-3 border-t border-line pt-5">
          <h3 className="text-sm font-semibold text-ink">{m.includes}</h3>
          <ul className="flex flex-col gap-2.5">
            {subjectLine && (
              <li className="flex items-start gap-3 text-ink">
                <Check size={18} strokeWidth={2.5} className="mt-0.5 shrink-0 text-palm" aria-hidden />
                <span>
                  <span className="font-semibold">{m.subjects}: </span>
                  {subjectLine}
                </span>
              </li>
            )}
            {[...plan.limits].sort((a, b) => limitRank(a.key) - limitRank(b.key)).map((limit) => (
              <li key={limit.key} className="flex items-start gap-3 text-ink">
                <Check size={18} strokeWidth={2.5} className="mt-0.5 shrink-0 text-palm" aria-hidden />
                <span>
                  {entitlementLabel(limit.key)}: <span className="font-semibold">{limit.limit === null ? m.noLimit : limit.limit}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="mt-auto flex flex-col gap-2 pt-1">
        {plan.chooseable !== false ? (
          <Link to={registerPath(audience, plan.code)} className={buttonClasses(plan.isDefault ? "solid" : "accent", "lg", "w-full sm:w-fit")}>
            {m.choose(plan.name)} <ArrowRight size={18} aria-hidden />
          </Link>
        ) : (
          <>
            <p className="text-sm text-ink-soft">{m.askLater}</p>
            <Link to={registerPath(audience)} className={buttonClasses("outline", "lg", "w-full sm:w-fit")}>
              {m.registerFirst}
            </Link>
          </>
        )}
      </div>
    </article>
  );
}
