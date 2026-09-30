import { useEffect, useRef, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import { Search } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { ApiError } from "@/lib/api/ApiError";
import {
  ENTITLEMENT_LABEL,
  currencyDigits,
  plansApi,
  priceText,
  type BillingInterval,
  type EntitlementKey,
  type FamilyAccountSummary,
  type PlanResponse,
} from "@/lib/api/subscriptions";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { FormField } from "@/features/auth/FormField";
import { cn } from "@/lib/utils/cn";
import { ComposerPanel, NewButton, Panel, SelectField, TextAreaField } from "../studio/shared";
import { errorText, useComposer } from "../studio/studioLogic";
import { AuditLog } from "./AuditLog";

const KEYS: EntitlementKey[] = ["CHILDREN_MAX", "JOURNEYS_PER_CHILD_MAX"];
const limitText = (limit: number | null) => (limit === null ? "No limit" : String(limit));
const dateOnly = (iso: string) =>
  new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });

/**
 * Plans and what families have (PLATFORM-EXPANSION.md §3). Names, limits and prices are the
 * owner's to decide; this page only records them. No payment happens here or anywhere yet.
 */
export function PlansPage() {
  useDocumentTitle("Plans");
  const plans = useAsync(() => plansApi.list(), []);
  const composer = useComposer(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = plans.data?.find((p) => p.plan.id === selectedId) ?? null;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Plans"
        description="What a family can have. A family without a subscription is on the default plan. Limits apply only when a family adds a child or a journey — nothing already done is ever taken away."
        actions={!composer.open && <NewButton label="New plan" onClick={composer.show} />}
      />
      {composer.open && (
        <NewPlan
          focusOnOpen={composer.requested}
          onClose={composer.hide}
          onCreated={(plan) => {
            composer.hide();
            plans.refetch();
            setSelectedId(plan.plan.id);
          }}
        />
      )}

      <div className={cn("grid grid-cols-1 gap-6", selected && "xl:grid-cols-[minmax(0,1fr)_400px]")}>
        <Panel title="All plans" flush>
          {plans.isLoading && !plans.data && (
            <div className="px-5 pb-5">
              <LoadingState label="Loading plans…" />
            </div>
          )}
          {plans.error && (
            <div className="px-5 pb-5">
              <ErrorState error={plans.error} onRetry={plans.refetch} />
            </div>
          )}
          {plans.data && plans.data.length === 0 && (
            <div className="px-5 pb-5">
              <EmptyState title="No plans yet" />
            </div>
          )}
          {plans.data && plans.data.length > 0 && (
            <>
              <div
                aria-hidden
                className="hidden grid-cols-[minmax(0,1fr)_7rem_8rem_9rem_5rem] gap-x-4 border-y border-line bg-paper px-6 py-2 text-[0.8125rem] text-ink-faint sm:grid"
              >
                <span>Plan</span>
                <span>Status</span>
                <span>Limits</span>
                <span>Price</span>
                <span className="text-right">On it</span>
              </div>
              <ul aria-label="Plans" className="divide-y divide-line border-t border-line sm:border-t-0">
                {plans.data.map((p) => (
                  <li key={p.plan.id}>
                    <button
                      type="button"
                      onClick={() => setSelectedId(p.plan.id === selectedId ? null : p.plan.id)}
                      aria-expanded={p.plan.id === selectedId}
                      className={cn(
                        "grid w-full grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1 px-5 py-3.5 text-left transition-colors hover:bg-paper sm:grid-cols-[minmax(0,1fr)_7rem_8rem_9rem_5rem] sm:px-6",
                        p.plan.id === selectedId && "bg-tonle-50/60",
                      )}
                    >
                      <span className="min-w-0">
                        <span className="block font-medium text-tonle-950">{p.plan.name}</span>
                        <span className="block font-mono text-xs text-ink-faint">{p.plan.code}</span>
                      </span>
                      <span className="flex flex-wrap items-center gap-1.5">
                        <PlanBadge status={p.plan.status} />
                        {p.plan.isDefault && <span className="text-xs font-semibold text-tonle-700">Default</span>}
                      </span>
                      <span className="hidden text-sm text-ink-soft sm:block">
                        {p.entitlements.map((e) => (
                          <span key={e.key} className="block">
                            {ENTITLEMENT_LABEL[e.key].split(" ")[0]}: {limitText(e.limit)}
                          </span>
                        ))}
                      </span>
                      <span className="hidden text-sm text-ink-soft sm:block">{priceText(p.plan.price) ?? "Not set"}</span>
                      <span className="hidden text-right text-sm text-ink-soft tabular sm:block">
                        {p.familiesOnPlan} {p.familiesOnPlan === 1 ? "family" : "families"}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </Panel>
        {selected && <PlanDetail key={selected.plan.id + selected.plan.status} plan={selected} onChanged={plans.refetch} />}
      </div>

      <FamilyAccess plans={plans.data ?? []} />
    </div>
  );
}

function PlanBadge({ status }: { status: PlanResponse["plan"]["status"] }) {
  const tone = {
    DRAFT: "bg-light-soft text-light-deep ring-light/40",
    ACTIVE: "bg-palm-soft text-palm-deep ring-palm/25",
    RETIRED: "bg-paper text-ink-soft ring-line",
  }[status];
  return (
    <span className={cn("inline-flex items-center rounded-[6px] px-2 py-0.5 text-xs font-semibold ring-1 ring-inset", tone)}>
      {status === "DRAFT" ? "Draft" : status === "ACTIVE" ? "Active" : "Retired"}
    </span>
  );
}

function NewPlan({
  onCreated,
  onClose,
  focusOnOpen,
}: {
  onCreated: (plan: PlanResponse) => void;
  onClose: () => void;
  focusOnOpen: boolean;
}) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onCreated(await plansApi.create({ code: code.trim().toUpperCase(), name: name.trim() }));
    } catch (err) {
      setError(errorText(err, "The plan couldn't be created."));
      setBusy(false);
    }
  }

  return (
    <ComposerPanel title="New plan" onClose={onClose} focusOnOpen={focusOnOpen} className="max-w-2xl">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <p className="text-sm text-ink-soft">
          It starts as a draft with the default plan&apos;s limits. Set its limits, then activate it.
        </p>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="Name families will see"
            value={name}
            maxLength={120}
            required
            onChange={(e) => setName(e.target.value)}
          />
          <FormField
            label="Code (capital letters, numbers and _)"
            value={code}
            maxLength={60}
            required
            onChange={(e) => setCode(e.target.value.toUpperCase())}
          />
        </div>
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <Button type="submit" disabled={busy || !name.trim() || !code.trim()} className="w-fit">
          {busy ? "Creating…" : "Create draft plan"}
        </Button>
      </form>
    </ComposerPanel>
  );
}

function PlanDetail({ plan, onChanged }: { plan: PlanResponse; onChanged: () => void }) {
  const draft = plan.plan.status === "DRAFT";
  const [name, setName] = useState(plan.plan.name);
  const [description, setDescription] = useState(plan.plan.description ?? "");
  const [selfService, setSelfService] = useState(plan.plan.selfService);
  const [limits, setLimits] = useState<Record<EntitlementKey, number | null>>(
    Object.fromEntries(KEYS.map((k) => [k, plan.entitlements.find((e) => e.key === k)?.limit ?? null])) as Record<
      EntitlementKey,
      number | null
    >,
  );
  const [amount, setAmount] = useState(
    plan.plan.price ? String(plan.plan.price.amountMinor / 10 ** (currencyDigits(plan.plan.price.currency) ?? 2)) : "",
  );
  const [currency, setCurrency] = useState(plan.plan.price?.currency ?? "");
  const [interval, setBillingInterval] = useState<BillingInterval | "">(plan.plan.price?.interval ?? "");
  const [confirming, setConfirming] = useState<"activate" | "default" | "retire" | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      await plansApi.revise(plan.plan.id, {
        name: name.trim(),
        description: description.trim() || null,
        selfService,
        // The server stores minor units; an unknown currency is refused there with its reason.
        priceAmountMinor: amount.trim() ? Math.round(Number(amount) * 10 ** (currencyDigits(currency.trim()) ?? 0)) : null,
        priceCurrency: currency.trim() ? currency.trim().toUpperCase() : null,
        billingInterval: interval || null,
        limits,
      });
      setSaved(true);
      onChanged();
    } catch (err) {
      setError(errorText(err, "The plan couldn't be saved."));
    } finally {
      setBusy(false);
    }
  }

  async function act(action: "activate" | "default" | "retire") {
    setBusy(true);
    setError(null);
    try {
      if (action === "activate") await plansApi.activate(plan.plan.id);
      if (action === "default") await plansApi.makeDefault(plan.plan.id);
      if (action === "retire") await plansApi.retire(plan.plan.id);
      setConfirming(null);
      onChanged();
    } catch (err) {
      setError(errorText(err, "That didn't work."));
    } finally {
      setBusy(false);
    }
  }

  const CONFIRM: Record<"activate" | "default" | "retire", { question: string; label: string }> = {
    activate: { question: "Activate this plan? Its limits and price can't change afterwards.", label: "Yes, activate" },
    default: {
      question: "Make this the default? Every family without a subscription gets it from now on.",
      label: "Yes, make default",
    },
    retire: {
      question: "Retire this plan? No family can be given it; families on it keep it until their subscription ends.",
      label: "Yes, retire",
    },
  };

  return (
    <Panel title={plan.plan.name} className="self-start">
      <div className="flex flex-col gap-5">
        <p className="flex flex-wrap items-center gap-2 text-sm text-ink-soft">
          <PlanBadge status={plan.plan.status} />
          {plan.plan.isDefault && <span className="font-semibold text-tonle-700">Default plan</span>}
          <span>
            · {plan.familiesOnPlan} {plan.familiesOnPlan === 1 ? "family" : "families"} subscribed
          </span>
        </p>

        {draft ? (
          <form onSubmit={save} className="flex flex-col gap-4">
            <FormField
              label="Name families will see"
              value={name}
              maxLength={120}
              required
              onChange={(e) => setName(e.target.value)}
            />
            <TextAreaField
              label="What it includes, in the family's words"
              value={description}
              maxLength={1000}
              onChange={(e) => setDescription(e.target.value)}
            />
            <fieldset className="flex flex-col gap-3">
              <legend className="mb-1 text-sm font-semibold text-ink">Limits</legend>
              {KEYS.map((key) => (
                <div key={key} className="flex flex-col gap-2 rounded-[12px] bg-paper px-4 py-3 ring-1 ring-line">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <span className="text-sm font-semibold text-ink">{ENTITLEMENT_LABEL[key]}</span>
                    <label className="flex items-center gap-2 text-sm text-ink">
                      <input
                        type="checkbox"
                        className="h-4 w-4 accent-tonle-700"
                        checked={limits[key] === null}
                        onChange={(e) => setLimits({ ...limits, [key]: e.target.checked ? null : 1 })}
                      />
                      No limit
                    </label>
                  </div>
                  {limits[key] !== null && (
                    <div className="w-32">
                      <FormField
                        label={`Up to (${ENTITLEMENT_LABEL[key].toLowerCase()})`}
                        type="number"
                        min={0}
                        value={String(limits[key])}
                        onChange={(e) =>
                          setLimits({ ...limits, [key]: e.target.value === "" ? 0 : Math.max(0, Number(e.target.value)) })
                        }
                      />
                    </div>
                  )}
                </div>
              ))}
            </fieldset>
            <details className="group rounded-[12px] bg-paper px-4 py-3 ring-1 ring-line">
              <summary className="cursor-pointer text-sm font-semibold text-tonle-700">
                Price (optional — none yet is fine)
              </summary>
              <div className="mt-3 grid gap-3 sm:grid-cols-3">
                <FormField
                  label="Amount"
                  type="number"
                  min={0}
                  step={1 / 10 ** (currencyDigits(currency.trim()) ?? 2)}
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                />
                <FormField
                  label="Currency"
                  value={currency}
                  maxLength={3}
                  placeholder="USD"
                  onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                />
                <SelectField
                  label="Every"
                  value={interval}
                  onChange={(e) => setBillingInterval(e.target.value as BillingInterval | "")}
                >
                  <option value="">—</option>
                  <option value="MONTH">Month</option>
                  <option value="YEAR">Year</option>
                </SelectField>
              </div>
              <label className="mt-3 flex items-start gap-2 text-sm text-ink">
                <input
                  type="checkbox"
                  className="mt-0.5 h-4 w-4 accent-tonle-700"
                  checked={selfService}
                  onChange={(e) => setSelfService(e.target.checked)}
                />
                Families may choose it themselves (once online payment exists; nothing charges anyone today)
              </label>
            </details>
            {error && (
              <p role="alert" className="text-sm font-medium text-brick-deep">
                {error}
              </p>
            )}
            {saved && (
              <p role="status" className="text-sm font-medium text-palm-deep">
                Saved.
              </p>
            )}
            <div className="flex flex-wrap gap-2">
              <Button type="submit" variant="outline" disabled={busy}>
                {busy ? "Saving…" : "Save draft"}
              </Button>
              <Button type="button" onClick={() => setConfirming("activate")} disabled={busy}>
                Activate…
              </Button>
            </div>
          </form>
        ) : (
          <>
            {plan.plan.description && <p className="text-sm leading-relaxed text-ink-soft">{plan.plan.description}</p>}
            <dl className="grid gap-3 sm:grid-cols-2">
              {plan.entitlements.map((e) => (
                <div key={e.key} className="rounded-[12px] bg-paper px-4 py-3 ring-1 ring-line">
                  <dt className="text-sm text-ink-faint">{ENTITLEMENT_LABEL[e.key]}</dt>
                  <dd className="font-semibold text-ink">{limitText(e.limit)}</dd>
                </div>
              ))}
              <div className="rounded-[12px] bg-paper px-4 py-3 ring-1 ring-line sm:col-span-2">
                <dt className="text-sm text-ink-faint">Price</dt>
                <dd className="font-semibold text-ink">{priceText(plan.plan.price) ?? "Not set"}</dd>
              </div>
            </dl>
            {error && (
              <p role="alert" className="text-sm font-medium text-brick-deep">
                {error}
              </p>
            )}
            {plan.plan.status === "ACTIVE" && !plan.plan.isDefault && !confirming && (
              <div className="flex flex-wrap gap-2">
                <Button variant="outline" size="sm" onClick={() => setConfirming("default")}>
                  Make default…
                </Button>
                <Button variant="ghost" size="sm" onClick={() => setConfirming("retire")}>
                  Retire…
                </Button>
              </div>
            )}
          </>
        )}

        {confirming && (
          <div
            role="group"
            aria-label="Confirm"
            className="flex flex-col gap-3 rounded-[12px] bg-light-soft/60 p-4 ring-1 ring-light/40"
          >
            <p className="text-sm font-medium text-ink">{CONFIRM[confirming].question}</p>
            <div className="flex flex-wrap gap-2">
              <Button size="sm" onClick={() => act(confirming)} disabled={busy}>
                {busy ? "Working…" : CONFIRM[confirming].label}
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setConfirming(null)} disabled={busy}>
                Cancel
              </Button>
            </div>
          </div>
        )}
      </div>
    </Panel>
  );
}

/** Find a family by a parent's exact address and give it a plan, or end the one it has. */
function FamilyAccess({ plans }: { plans: PlanResponse[] }) {
  // ?parentEmail= (a link from the overview) opens that family straight away.
  const [params] = useSearchParams();
  const linked = params.get("parentEmail") ?? "";
  const [email, setEmail] = useState(linked);
  const [finding, setFinding] = useState(false);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [family, setFamily] = useState<FamilyAccountSummary | null>(null);

  const openedLink = useRef(false);
  useEffect(() => {
    if (!linked || openedLink.current) return;
    openedLink.current = true;
    void find(linked);
    // Only the address it was opened with.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [linked]);

  async function onFind(e: FormEvent) {
    e.preventDefault();
    await find(email.trim());
  }

  async function find(address: string) {
    setFinding(true);
    setLookupError(null);
    setFamily(null);
    try {
      setFamily(await plansApi.findFamily(address));
    } catch (err) {
      setLookupError(
        err instanceof ApiError && err.status === 404
          ? "No family has a parent with that email address. It has to match exactly."
          : errorText(err, "The lookup didn't work. Please try again."),
      );
    } finally {
      setFinding(false);
    }
  }

  return (
    <Panel title="A family's plan">
      <div className="flex flex-col gap-5">
        <form onSubmit={onFind} className="flex flex-col gap-3 sm:flex-row sm:items-end" noValidate>
          <div className="min-w-0 flex-1">
            <FormField
              label="A parent's email address"
              type="email"
              autoComplete="off"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={lookupError ?? undefined}
            />
          </div>
          <Button type="submit" disabled={finding || !email.trim()} className="w-fit">
            <Search size={16} aria-hidden /> {finding ? "Finding…" : "Find family"}
          </Button>
        </form>
        {family && <FamilyPlanCard key={family.familyId} family={family} plans={plans} onChanged={setFamily} />}
      </div>
    </Panel>
  );
}

function FamilyPlanCard({
  family,
  plans,
  onChanged,
}: {
  family: FamilyAccountSummary;
  plans: PlanResponse[];
  onChanged: (family: FamilyAccountSummary) => void;
}) {
  // The default plan applies without a subscription; giving it would change nothing.
  const givable = plans.filter((p) => p.plan.status === "ACTIVE" && !p.plan.isDefault);
  const [planId, setPlanId] = useState(givable[0]?.plan.id ?? "");
  const [until, setUntil] = useState("");
  const [note, setNote] = useState("");
  const [ending, setEnding] = useState(false);
  const [reason, setReason] = useState("");
  const [moving, setMoving] = useState(false);
  const [newEnd, setNewEnd] = useState("");
  const [moveReason, setMoveReason] = useState("");
  const [historyVersion, setHistoryVersion] = useState(0);
  const [busy, setBusy] = useState(false);
  // Each message shows next to the action that caused it: the current plan's controls, or the grant form.
  const [feedback, setFeedback] = useState<{ where: "plan" | "grant"; ok: boolean; text: string } | null>(null);
  const sub = family.plan.subscription;

  async function grant(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setFeedback(null);
    try {
      const updated = await plansApi.grant(family.familyId, {
        planId,
        periodEnd: until ? new Date(`${until}T23:59:59`).toISOString() : null,
        note: note.trim() || null,
      });
      setFeedback({
        where: "grant",
        ok: true,
        text: `${updated.plan.plan.name} given${until ? ` until ${dateOnly(new Date(`${until}T23:59:59`).toISOString())}` : ""}.`,
      });
      setUntil("");
      setNote("");
      setHistoryVersion((v) => v + 1);
      onChanged(updated);
    } catch (err) {
      setFeedback({ where: "grant", ok: false, text: errorText(err, "The plan couldn't be given.") });
    } finally {
      setBusy(false);
    }
  }

  async function move(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setFeedback(null);
    try {
      const periodEnd = newEnd ? new Date(`${newEnd}T23:59:59`).toISOString() : null;
      const updated = await plansApi.changePeriod(family.familyId, periodEnd, moveReason.trim());
      setFeedback({
        where: "plan",
        ok: true,
        text: periodEnd ? `It now runs until ${dateOnly(periodEnd)}.` : "It is now open-ended.",
      });
      setMoving(false);
      setNewEnd("");
      setMoveReason("");
      setHistoryVersion((v) => v + 1);
      onChanged(updated);
    } catch (err) {
      setFeedback({ where: "plan", ok: false, text: errorText(err, "The end date couldn't be changed.") });
    } finally {
      setBusy(false);
    }
  }

  async function end(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setFeedback(null);
    try {
      const updated = await plansApi.end(family.familyId, reason.trim());
      setFeedback({ where: "plan", ok: true, text: "Ended. The family is on the default plan now." });
      setEnding(false);
      setHistoryVersion((v) => v + 1);
      onChanged(updated);
    } catch (err) {
      setFeedback({ where: "plan", ok: false, text: errorText(err, "That didn't work.") });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid gap-6 border-t border-line pt-5 lg:grid-cols-2">
      <div className="flex flex-col gap-3">
        <p className="font-semibold text-tonle-950">{family.familyName}</p>
        <p className="text-sm text-ink-soft">
          {family.parents.map((p) => p.email).join(", ")} · {family.childCount} {family.childCount === 1 ? "child" : "children"}
        </p>
        <div className="rounded-[12px] bg-paper px-4 py-3 ring-1 ring-line">
          <p className="font-semibold text-ink">
            {family.plan.plan.name}
            {family.plan.onDefaultPlan && <span className="font-normal text-ink-soft"> (default)</span>}
          </p>
          <p className="text-sm text-ink-soft">
            {sub && sub.grantsAccess
              ? `Given ${dateOnly(sub.startedAt)} · ${sub.currentPeriodEnd ? `until ${dateOnly(sub.currentPeriodEnd)}` : "open-ended"}`
              : sub
                ? `An earlier plan ended${sub.currentPeriodEnd ? ` on ${dateOnly(sub.currentPeriodEnd)}` : ""}.`
                : "No subscription: the default plan applies."}
          </p>
          {sub?.note && <p className="mt-1 text-sm text-ink-faint">Note: {sub.note}</p>}
          <ul className="mt-2 flex flex-col gap-0.5 text-sm text-ink">
            {family.plan.entitlements.map((e) => (
              <li key={e.key}>
                {ENTITLEMENT_LABEL[e.key]}:{" "}
                {e.key === "CHILDREN_MAX"
                  ? e.limit === null
                    ? `${e.used ?? 0} · no limit`
                    : `${e.used ?? 0} of ${e.limit}`
                  : `${e.limit === null ? "no limit" : `up to ${e.limit}`} · busiest child has ${e.used ?? 0}`}
              </li>
            ))}
          </ul>
        </div>
        {sub?.grantsAccess && sub.source === "PROVIDER" && (
          <p className="text-sm text-ink-soft">This plan is paid through the payment provider; change it there.</p>
        )}
        {sub?.grantsAccess && sub.source === "ADMIN_GRANT" && !ending && !moving && (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" className="w-fit" onClick={() => setMoving(true)}>
              Change end date…
            </Button>
            <Button variant="ghost" size="sm" className="w-fit" onClick={() => setEnding(true)}>
              End this plan…
            </Button>
          </div>
        )}
        {moving && (
          <form onSubmit={move} className="flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line">
            <p className="text-sm text-ink-soft">
              The plan and its start stay the same.{" "}
              {sub?.currentPeriodEnd ? `Now: until ${dateOnly(sub.currentPeriodEnd)}.` : "Now: open-ended."}
            </p>
            <FormField
              label="New end (leave empty for open-ended)"
              type="date"
              value={newEnd}
              onChange={(e) => setNewEnd(e.target.value)}
            />
            <FormField
              label="Why it changes (recorded in the activity log)"
              value={moveReason}
              maxLength={500}
              required
              onChange={(e) => setMoveReason(e.target.value)}
            />
            <div className="flex gap-2">
              <Button type="submit" size="sm" disabled={busy || !moveReason.trim()}>
                {busy ? "Saving…" : "Save end date"}
              </Button>
              <Button type="button" size="sm" variant="ghost" onClick={() => setMoving(false)}>
                Cancel
              </Button>
            </div>
          </form>
        )}
        {ending && (
          <form onSubmit={end} className="flex flex-col gap-3 rounded-[12px] bg-light-soft/60 p-4 ring-1 ring-light/40">
            <FormField
              label="Why it ends (recorded in the activity log)"
              value={reason}
              maxLength={500}
              required
              onChange={(e) => setReason(e.target.value)}
            />
            <div className="flex gap-2">
              <Button type="submit" size="sm" disabled={busy || !reason.trim()}>
                {busy ? "Ending…" : "End it now"}
              </Button>
              <Button type="button" size="sm" variant="ghost" onClick={() => setEnding(false)}>
                Cancel
              </Button>
            </div>
          </form>
        )}
        {feedback?.where === "plan" && <Feedback {...feedback} />}
      </div>

      {givable.length === 0 ? (
        <div className="flex flex-col gap-2">
          <p className="text-sm font-semibold text-ink">Give a plan</p>
          <p className="text-sm text-ink-soft">
            There&apos;s no plan to give yet: every family already has the default. Create and activate another plan first.
          </p>
        </div>
      ) : (
        <form onSubmit={grant} className="flex flex-col gap-4">
          <div>
            <p className="text-sm font-semibold text-ink">{sub?.grantsAccess ? "Replace with another plan" : "Give a plan"}</p>
            {sub?.grantsAccess && (
              <p className="text-sm text-ink-soft">
                The new plan starts now and replaces {family.plan.plan.name}. To keep it and change only when it ends, use
                &ldquo;Change end date&rdquo;.
              </p>
            )}
          </div>
          <SelectField label="Plan" value={planId} onChange={(e) => setPlanId(e.target.value)}>
            {givable.map((p) => (
              <option key={p.plan.id} value={p.plan.id}>
                {p.plan.name}
              </option>
            ))}
          </SelectField>
          <FormField
            label="Until (optional — leave empty for open-ended)"
            type="date"
            value={until}
            onChange={(e) => setUntil(e.target.value)}
          />
          <FormField
            label="Note (for administrators only)"
            value={note}
            maxLength={500}
            onChange={(e) => setNote(e.target.value)}
          />
          {feedback?.where === "grant" && <Feedback {...feedback} />}
          <Button type="submit" disabled={busy || !planId} className="w-fit">
            {busy ? "Giving…" : sub?.grantsAccess ? "Replace the plan" : "Give this plan"}
          </Button>
        </form>
      )}

      {sub && (
        <section aria-labelledby="subscription-history" className="flex flex-col gap-2 lg:col-span-2">
          <h3 id="subscription-history" className="text-sm font-semibold text-ink">
            History of this family&apos;s plan
          </h3>
          <AuditLog refreshKey={historyVersion} filter={{ resourceId: sub.id }} emptyTitle="Nothing recorded for this plan yet" />
        </section>
      )}
    </div>
  );
}

function Feedback({ ok, text }: { ok: boolean; text: string }) {
  return ok ? (
    <p role="status" className="text-sm font-medium text-palm-deep">
      {text}
    </p>
  ) : (
    <p role="alert" className="text-sm font-medium text-brick-deep">
      {text}
    </p>
  );
}
