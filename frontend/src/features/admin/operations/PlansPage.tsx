import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { Search } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { ApiError } from "@/lib/api/ApiError";
import {
  ENTITLEMENT_KEYS,
  entitlementLabel,
  currencyDigits,
  planRequestsApi,
  plansApi,
  priceText,
  type BillingInterval,
  type EntitlementKey,
  type FamilyAccountSummary,
  type PlanAudience,
  type PlanCounts,
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
import { CustomerPayments } from "./CustomerPayments";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { formatDate } from "@/i18n/format";
import { plansMessages } from "./plans.messages";
import { CatalogNameFields } from "../studio/CatalogNameFields";
import { khmerNameBody } from "../studio/bilingual";
import { useUnsavedGuard } from "../studio/unsavedGuard";

const limitText = (limit: number | null, key?: EntitlementKey) =>
  limit === null ? messagesNow(plansMessages).noLimit : key === "TEACHER_STORAGE_MB" ? `${limit} MB` : String(limit);
const dateOnly = (iso: string) => formatDate(iso, { day: "numeric", month: "short", year: "numeric" });

/**
 * Plans and what families and schools have (PLATFORM-EXPANSION.md §3). Names, limits and prices
 * are the owner's to decide; this page only records them. No payment happens here or anywhere yet.
 */
export function PlansPage() {
  const m = useMessages(plansMessages);
  useDocumentTitle(m.title);
  const plans = useAsync(() => plansApi.list(), []);
  const composer = useComposer(false);
  const [params, setParams] = useSearchParams();
  // ?for=schools shows school plans; a link to a family (?parentEmail=) is always about family plans.
  const audience: PlanAudience = params.get("for") === "schools" && !params.get("parentEmail") ? "SCHOOL" : "FAMILY";
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [requestsVersion, setRequestsVersion] = useState(0);
  const shown = plans.data?.filter((p) => p.plan.audience === audience) ?? null;
  const selected = shown?.find((p) => p.plan.id === selectedId) ?? null;

  function choose(next: PlanAudience) {
    setSelectedId(null);
    setParams(next === "SCHOOL" ? { for: "schools" } : {}, { replace: true });
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={m.title}
        description={audience === "SCHOOL" ? m.leadSchools : m.lead}
        actions={!composer.open && <NewButton label={m.newPlan} onClick={composer.show} />}
      />
      <div role="group" aria-label={m.audienceLabel} className="flex w-fit gap-1 rounded-[12px] bg-paper p-1 ring-1 ring-line">
        {(["FAMILY", "SCHOOL"] as const).map((a) => (
          <button
            key={a}
            type="button"
            aria-pressed={audience === a}
            onClick={() => choose(a)}
            className={cn(
              "min-h-11 rounded-[9px] px-4 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tonle-600",
              audience === a ? "bg-white text-tonle-900 shadow-sm ring-1 ring-line" : "text-ink-soft hover:text-ink",
            )}
          >
            {m.audience[a]}
          </button>
        ))}
      </div>
      {composer.open && (
        <NewPlan
          audience={audience}
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
        <Panel title={m.all} flush>
          {plans.isLoading && !plans.data && (
            <div className="px-5 pb-5">
              <LoadingState label={m.loading} />
            </div>
          )}
          {plans.error && (
            <div className="px-5 pb-5">
              <ErrorState error={plans.error} onRetry={plans.refetch} />
            </div>
          )}
          {shown && shown.length === 0 && (
            <div className="px-5 pb-5">
              <EmptyState title={m.none} />
            </div>
          )}
          {shown && shown.length > 0 && (
            <>
              <div
                aria-hidden
                className={cn(
                  "hidden grid-cols-[minmax(0,1fr)_7rem_8rem_9rem_5rem] gap-x-4 border-y border-line bg-paper px-6 py-2 text-[0.8125rem] text-ink-faint sm:grid",
                  selected && "xl:grid-cols-[minmax(0,1fr)_7rem_5rem]",
                )}
              >
                <span>{m.plan}</span>
                <span>{m.status}</span>
                <span className={cn(selected && "xl:hidden")}>{m.limits}</span>
                <span className={cn(selected && "xl:hidden")}>{m.price}</span>
                <span className="text-right">{m.onIt}</span>
              </div>
              <ul aria-label={m.list} className="divide-y divide-line border-t border-line sm:border-t-0">
                {shown.map((p) => (
                  <li key={p.plan.id}>
                    <button
                      type="button"
                      onClick={() => setSelectedId(p.plan.id === selectedId ? null : p.plan.id)}
                      aria-expanded={p.plan.id === selectedId}
                      className={cn(
                        "grid w-full grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1 px-5 py-3.5 text-left transition-colors hover:bg-paper sm:grid-cols-[minmax(0,1fr)_7rem_8rem_9rem_5rem] sm:px-6",
                        selected && "xl:grid-cols-[minmax(0,1fr)_7rem_5rem]",
                        p.plan.id === selectedId && "bg-tonle-50/60",
                      )}
                    >
                      <span className="min-w-0">
                        <span className="block font-medium text-tonle-950">{p.plan.name}</span>
                        <span className="block font-mono text-xs text-ink-faint [overflow-wrap:anywhere]">
                          {p.plan.code}
                          {p.basedOn && <span className="font-sans"> · {m.versions.from(p.basedOn.code)}</span>}
                        </span>
                      </span>
                      <span className="flex flex-wrap items-center gap-1.5">
                        <PlanBadge status={p.plan.status} />
                        {p.plan.isDefault && <span className="text-xs font-semibold text-tonle-700">{m.default}</span>}
                        {p.plan.status === "ACTIVE" && p.offeredNow === false && (
                          <span className="text-xs font-semibold text-light-deep">{offerState(p)}</span>
                        )}
                      </span>
                      <span className={cn("hidden text-sm text-ink-soft sm:block", selected && "xl:hidden")}>
                        {p.entitlements.map((e) => (
                          <span key={e.key} className="block">
                            {entitlementLabel(e.key, true)}: {limitText(e.limit, e.key)}
                          </span>
                        ))}
                      </span>
                      <span className={cn("hidden text-sm text-ink-soft sm:block", selected && "xl:hidden")}>
                        {priceText(p.plan.price) ?? m.notSet}
                      </span>
                      <span className="hidden text-right text-sm text-ink-soft tabular sm:block">
                        {audience === "SCHOOL" ? m.schools(p.subscribers) : m.families(p.subscribers)}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </Panel>
        {selected && (
          <PlanDetail
            key={selected.plan.id + selected.plan.status}
            plan={selected}
            onChanged={plans.refetch}
            onDuplicated={(copy) => {
              plans.refetch();
              setSelectedId(copy.plan.id);
            }}
          />
        )}
      </div>

      <SubscriptionsNow audience={audience} version={plans.data} />

      {audience === "FAMILY" ? (
        <>
          <PlanRequests version={requestsVersion} />
          <FamilyAccess
            plans={(plans.data ?? []).filter((p) => p.plan.audience === "FAMILY")}
            onFamilyChanged={() => setRequestsVersion((n) => n + 1)}
          />
        </>
      ) : (
        <Panel title={m.schoolGrants.title}>
          <p className="text-sm text-ink-soft">
            {m.schoolGrants.lead}{" "}
            <Link to="/admin/schools" className="font-semibold text-tonle-700 underline-offset-2 hover:underline">
              {m.schoolGrants.open}
            </Link>
          </p>
        </Panel>
      )}
    </div>
  );
}

/**
 * Families asking for a plan (Y11). Nothing is bought online (PD-09): an administrator opens the
 * family below and gives the plan - which answers the request and tells the family - or declines
 * it here with a message the family receives by email.
 */
function PlanRequests({ version }: { version: number }) {
  const requests = useAsync(() => planRequestsApi.open(0, 100), [version]);
  const [declining, setDeclining] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ ok: boolean; text: string } | null>(null);
  const { hash } = useLocation();
  const m = useMessages(plansMessages).requests;
  useEffect(() => {
    if (hash === "#requests" && requests.data) document.getElementById("requests")?.scrollIntoView({ block: "start" });
  }, [hash, requests.data]);

  async function decline(id: string, familyName: string) {
    setBusy(true);
    setFeedback(null);
    try {
      await planRequestsApi.decline(id, message.trim());
      setFeedback({ ok: true, text: m.declined(familyName) });
      setDeclining(null);
      setMessage("");
    } catch (err) {
      setFeedback({ ok: false, text: errorText(err, m.didntWork) });
    } finally {
      setBusy(false);
      requests.refetch();
    }
  }

  const rows = requests.data?.items ?? [];
  return (
    <div id="requests">
      <Panel title={m.title}>
        <div className="flex flex-col gap-4">
          <p className="text-sm text-ink-soft">{m.lead}</p>
          {requests.isLoading && !requests.data && <LoadingState label={m.loading} />}
          {requests.error && <ErrorState error={requests.error} onRetry={requests.refetch} />}
          {feedback && <Feedback ok={feedback.ok} text={feedback.text} />}
          {requests.data && rows.length === 0 && <p className="text-sm text-ink-soft">{m.none}</p>}
          {rows.length > 0 && (
            <ul aria-label={m.title} className="flex flex-col divide-y divide-line border-y border-line">
              {rows.map((row) => (
                <li key={row.id} className="flex flex-col gap-3 py-3">
                  <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
                    <span className="min-w-0">
                      <span className="block font-medium text-ink">
                        {m.asked(row.familyName, row.planName)}
                      </span>
                      <span className="block text-sm text-ink-soft [overflow-wrap:anywhere]">
                        {row.parentEmail} · {dateOnly(row.requestedAt)}
                      </span>
                    </span>
                    <span className="flex flex-wrap gap-2">
                      <Link
                        to={`/admin/plans?parentEmail=${encodeURIComponent(row.parentEmail)}`}
                        className="inline-flex h-9 items-center rounded-[10px] px-3.5 text-[0.8125rem] font-semibold text-tonle-700 ring-1 ring-inset ring-line-strong hover:bg-paper"
                      >
                        {m.open}
                      </Link>
                      {declining !== row.id && (
                        <Button size="sm" variant="ghost" onClick={() => setDeclining(row.id)}>
                          {m.decline}
                        </Button>
                      )}
                    </span>
                  </div>
                  {declining === row.id && (
                    <div className="flex flex-col gap-3" role="group" aria-label={m.declineLabel(row.familyName)}>
                      <TextAreaField
                        label={m.message}
                        value={message}
                        maxLength={500}
                        onChange={(e) => setMessage(e.target.value)}
                      />
                      <div className="flex flex-wrap gap-2">
                        <Button size="sm" onClick={() => decline(row.id, row.familyName)} disabled={busy}>
                          {busy ? m.declining : m.declineConfirm}
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setDeclining(null)} disabled={busy}>
                          {m.cancel}
                        </Button>
                      </div>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
          {requests.data && requests.data.total > rows.length && (
            <p className="text-sm text-ink-soft">{m.oldest(rows.length, requests.data.total)}</p>
          )}
        </div>
      </Panel>
    </div>
  );
}

function PlanBadge({ status }: { status: PlanResponse["plan"]["status"] }) {
  const m = useMessages(plansMessages);
  const tone = {
    DRAFT: "bg-light-soft text-light-deep ring-light/40",
    ACTIVE: "bg-palm-soft text-palm-deep ring-palm/25",
    RETIRED: "bg-paper text-ink-soft ring-line",
  }[status];
  return (
    <span className={cn("inline-flex items-center rounded-[6px] px-2 py-0.5 text-xs font-semibold ring-1 ring-inset", tone)}>
      {m.badge[status]}
    </span>
  );
}

function NewPlan({
  audience,
  onCreated,
  onClose,
  focusOnOpen,
}: {
  audience: PlanAudience;
  onCreated: (plan: PlanResponse) => void;
  onClose: () => void;
  focusOnOpen: boolean;
}) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const m = useMessages(plansMessages).create;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onCreated(await plansApi.create({ code: code.trim().toUpperCase(), name: name.trim(), audience }));
    } catch (err) {
      setError(errorText(err, m.failed));
      setBusy(false);
    }
  }

  return (
    <ComposerPanel title={m.titleFor[audience]} onClose={onClose} focusOnOpen={focusOnOpen} className="max-w-2xl">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <p className="text-sm text-ink-soft">{m.leadFor[audience]}</p>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField
            label={m.nameFor[audience]}
            value={name}
            maxLength={120}
            required
            onChange={(e) => setName(e.target.value)}
          />
          <FormField
            label={m.code}
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
          {busy ? m.creating : m.create}
        </Button>
      </form>
    </ComposerPanel>
  );
}

/**
 * Who is on which plan now (flexible plan management, slice 1). Families are counted, never listed; schools whose
 * paid plan ends soon are listed, because renewing them is an administrator's next action.
 */
function SubscriptionsNow({ audience, version }: { audience: PlanAudience; version: unknown }) {
  const summary = useAsync(() => plansApi.summary(), [version]);
  const m = useMessages(plansMessages).summary;
  const data = summary.data;
  const days = data?.endingWithinDays ?? 30;
  const inForce = (p: PlanCounts) => p.active + p.trialing + p.pastDue + p.cancelling;
  const rows =
    data?.plans.filter((p) => p.audience === audience && (p.status === "ACTIVE" || (p.status === "RETIRED" && inForce(p) > 0))) ?? [];
  const anyone = rows.some((p) => inForce(p) + p.ended > 0);
  const defaultPlan = rows.find((p) => p.isDefault);
  const heading = (key: SummaryNumber) => (key === "endingSoon" ? m.endingSoon(days) : m[key]);
  return (
    <Panel title={m.title}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-ink-soft">{audience === "SCHOOL" ? m.leadSchool : m.leadFamily}</p>
        {summary.isLoading && !data && <LoadingState label={m.loading} />}
        {summary.error && <ErrorState error={summary.error} onRetry={summary.refetch} />}
        {data && !anyone && <p className="text-sm text-ink-soft">{m.none}</p>}
        {data && anyone && (
          <div role="region" aria-label={m.title} tabIndex={0} className="-mx-1 overflow-x-auto px-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tonle-600">
            <table className="w-full min-w-[36rem] border-collapse text-sm">
              <caption className="sr-only">{m.caption(days)}</caption>
              <thead>
                <tr className="border-b border-line text-left text-[0.8125rem] text-ink-faint">
                  <th scope="col" className="py-2 pr-4 font-medium">
                    {m.plan}
                  </th>
                  {SUMMARY_NUMBERS.map((key) => (
                    <th key={key} scope="col" className="px-2 py-2 text-right font-medium">
                      {heading(key)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {rows.map((p) => (
                  <tr key={p.planId}>
                    <th scope="row" className="py-2.5 pr-4 text-left font-medium text-ink [overflow-wrap:anywhere]">
                      {p.name}
                      {p.status === "RETIRED" && (
                        <span className="ml-2 align-middle">
                          <PlanBadge status="RETIRED" />
                        </span>
                      )}
                    </th>
                    {SUMMARY_NUMBERS.map((key) => (
                      <td
                        key={key}
                        className={cn(
                          "px-2 py-2.5 text-right tabular",
                          p[key] === 0
                            ? "text-ink-faint"
                            : key === "pastDue" || key === "endingSoon"
                              ? "font-semibold text-ink"
                              : "text-ink",
                        )}
                      >
                        {p[key]}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && audience === "FAMILY" && defaultPlan && (
          <p className="text-sm text-ink">{m.familiesOnDefault(data.familiesOnDefault, defaultPlan.name)}</p>
        )}
        {data && audience === "SCHOOL" && (
          <>
            <p className="text-sm text-ink">{m.schoolsUnpaid(data.schoolsWithoutPaidPlan)}</p>
            <div className="flex flex-col gap-2">
              <h3 className="text-sm font-semibold text-ink">{m.endingTitle(days)}</h3>
              {data.schoolsEndingSoon.length === 0 ? (
                <p className="text-sm text-ink-soft">{m.endingNone(days)}</p>
              ) : (
                <ul aria-label={m.endingTitle(days)} className="flex flex-col divide-y divide-line border-y border-line">
                  {data.schoolsEndingSoon.map((s) => (
                    <li key={s.schoolId} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-2.5">
                      <Link
                        to={`/admin/schools/${s.schoolId}`}
                        className="min-w-0 font-medium text-tonle-700 underline-offset-2 [overflow-wrap:anywhere] hover:underline"
                      >
                        {s.schoolName}
                      </Link>
                      <span className="text-sm text-ink-soft">{m.endingRow(s.planName, dateOnly(s.paidUntil))}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </>
        )}
      </div>
    </Panel>
  );
}

const SUMMARY_NUMBERS = ["active", "endingSoon", "trialing", "pastDue", "cancelling", "ended"] as const;
type SummaryNumber = (typeof SUMMARY_NUMBERS)[number];

/** V79: copies a plan into a new draft version; the original, its subscribers and their price stay as they are. */
function DuplicatePlan({ plan, onDuplicated }: { plan: PlanResponse; onDuplicated: (copy: PlanResponse) => void }) {
  const m = useMessages(plansMessages).versions;
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(plan.plan.name);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onDuplicated(await plansApi.duplicate(plan.plan.id, { code: code.trim().toUpperCase(), name: name.trim() }));
    } catch (err) {
      setError(errorText(err, m.failed));
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <Button variant="ghost" size="sm" className="w-fit" onClick={() => setOpen(true)}>
        {m.duplicate}
      </Button>
    );
  }
  return (
    <form
      onSubmit={onSubmit}
      aria-label={m.title(plan.plan.name)}
      className="flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line"
    >
      <h3 className="text-sm font-semibold text-ink [overflow-wrap:anywhere]">{m.title(plan.plan.name)}</h3>
      <p className="text-sm text-ink-soft">{m.lead}</p>
      <FormField label={m.name} value={name} maxLength={120} required onChange={(e) => setName(e.target.value)} />
      <FormField
        label={m.code}
        value={code}
        maxLength={60}
        required
        autoFocus
        onChange={(e) => setCode(e.target.value.toUpperCase())}
      />
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" size="sm" disabled={busy || !name.trim() || !code.trim()}>
          {busy ? m.creating : m.create}
        </Button>
        <Button type="button" size="sm" variant="ghost" onClick={() => setOpen(false)} disabled={busy}>
          {m.cancel}
        </Button>
      </div>
    </form>
  );
}

function PlanDetail({
  plan,
  onChanged,
  onDuplicated,
}: {
  plan: PlanResponse;
  onChanged: () => void;
  onDuplicated: (copy: PlanResponse) => void;
}) {
  const draft = plan.plan.status === "DRAFT";
  const [name, setName] = useState(plan.plan.name);
  const [description, setDescription] = useState(plan.plan.description ?? "");
  // Its words in Khmer (V82), written on a draft like the English; the English is never replaced by them.
  const [nameKm, setNameKm] = useState(plan.plan.khmer?.name ?? "");
  const [descriptionKm, setDescriptionKm] = useState(plan.plan.khmer?.description ?? "");
  const [selfService, setSelfService] = useState(plan.plan.selfService);
  const KEYS = ENTITLEMENT_KEYS[plan.plan.audience];
  const [limits, setLimits] = useState<Partial<Record<EntitlementKey, number | null>>>(
    Object.fromEntries(KEYS.map((k) => [k, plan.entitlements.find((e) => e.key === k)?.limit ?? null])),
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
  // Reloads the plan's history after a change made here (created, revised, dated, activated...).
  const [historyVersion, setHistoryVersion] = useState(0);
  const changed = () => {
    setHistoryVersion((n) => n + 1);
    onChanged();
  };
  const all = useMessages(plansMessages);
  const m = all.detail;
  // A draft's words, limits and price not saved yet: leaving or reloading asks first (as in the Studio).
  const onScreen = JSON.stringify([name.trim(), description.trim(), nameKm.trim(), descriptionKm.trim(), selfService, limits,
    amount.trim(), currency.trim(), interval]);
  // What was saved: the plan as opened (this form is shown anew for each plan and status), then each save.
  const [savedScreen, setSavedScreen] = useState(onScreen);
  useUnsavedGuard(draft && onScreen !== savedScreen);

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
        khmer: khmerNameBody(plan.plan.khmer ?? undefined, nameKm, descriptionKm),
      });
      setSaved(true);
      setSavedScreen(onScreen);
      changed();
    } catch (err) {
      setError(errorText(err, m.saveFailed));
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
      changed();
    } catch (err) {
      setError(errorText(err, m.didntWork));
    } finally {
      setBusy(false);
    }
  }

  const CONFIRM: Record<"activate" | "default" | "retire", { question: string; label: string }> = {
    activate: { question: m.activateQuestion, label: m.activateYes },
    default: { question: m.defaultQuestion, label: m.defaultYes },
    retire: { question: m.retireQuestion, label: m.retireYes },
  };

  return (
    <Panel title={plan.plan.name} className="self-start">
      <div className="flex flex-col gap-5">
        <p className="flex flex-wrap items-center gap-2 text-sm text-ink-soft">
          <PlanBadge status={plan.plan.status} />
          {plan.plan.isDefault && <span className="font-semibold text-tonle-700">{m.defaultPlan}</span>}
          <span>{plan.plan.audience === "SCHOOL" ? m.subscribedSchools(plan.subscribers) : m.subscribed(plan.subscribers)}</span>
        </p>
        {plan.basedOn && (
          <p className="-mt-3 text-sm text-ink-soft [overflow-wrap:anywhere]">{all.versions.basedOn(plan.basedOn.name, plan.basedOn.code)}</p>
        )}

        {draft ? (
          <form onSubmit={save} className="flex flex-col gap-4">
            <CatalogNameFields
              nameLabel={all.create.nameFor[plan.plan.audience]}
              nameMax={120}
              name={name}
              onName={setName}
              description={description}
              onDescription={setDescription}
              descriptionLabel={m.descriptionFor[plan.plan.audience]}
              descriptionMax={1000}
              nameKm={nameKm}
              onNameKm={setNameKm}
              descriptionKm={descriptionKm}
              onDescriptionKm={setDescriptionKm}
            />
            <fieldset className="flex flex-col gap-3">
              <legend className="mb-1 text-sm font-semibold text-ink">{all.limits}</legend>
              {KEYS.map((key) => (
                <div key={key} className="flex flex-col gap-2 rounded-[12px] bg-paper px-4 py-3 ring-1 ring-line">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <span className="text-sm font-semibold text-ink">{entitlementLabel(key)}</span>
                    <label className="flex items-center gap-2 text-sm text-ink">
                      <input
                        type="checkbox"
                        className="h-4 w-4 accent-tonle-700"
                        checked={limits[key] == null}
                        onChange={(e) => setLimits({ ...limits, [key]: e.target.checked ? null : 1 })}
                      />
                      {m.noLimit}
                    </label>
                  </div>
                  {limits[key] != null && (
                    <div className="w-32">
                      <FormField
                        label={key === "TEACHER_STORAGE_MB" ? m.upToMb : m.upTo(entitlementLabel(key).toLowerCase())}
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
                {m.priceOptional}
              </summary>
              <div className="mt-3 grid gap-3 sm:grid-cols-3">
                <FormField
                  label={m.amount}
                  type="number"
                  min={0}
                  step={1 / 10 ** (currencyDigits(currency.trim()) ?? 2)}
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                />
                <FormField
                  label={m.currency}
                  value={currency}
                  maxLength={3}
                  placeholder="USD"
                  onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                />
                <SelectField
                  label={m.every}
                  value={interval}
                  onChange={(e) => setBillingInterval(e.target.value as BillingInterval | "")}
                >
                  <option value="">—</option>
                  <option value="MONTH">{m.month}</option>
                  <option value="YEAR">{m.year}</option>
                </SelectField>
              </div>
            </details>
            {/* Families ask for self-service plans; schools choose and pay for a priced one themselves (M4). */}
            <label className="flex items-start gap-2 text-sm text-ink">
              <input
                type="checkbox"
                className="mt-0.5 h-4 w-4 accent-tonle-700"
                checked={selfService}
                onChange={(e) => setSelfService(e.target.checked)}
              />
              {plan.plan.audience === "FAMILY" ? m.selfService : m.schoolSelfService}
            </label>
            {error && (
              <p role="alert" className="text-sm font-medium text-brick-deep">
                {error}
              </p>
            )}
            {saved && (
              <p role="status" className="text-sm font-medium text-palm-deep">
                {m.saved}
              </p>
            )}
            <div className="flex flex-wrap gap-2">
              <Button type="submit" variant="outline" disabled={busy}>
                {busy ? m.saving : m.saveDraft}
              </Button>
              <Button type="button" onClick={() => setConfirming("activate")} disabled={busy}>
                {m.activate}
              </Button>
            </div>
          </form>
        ) : (
          <>
            {plan.plan.description && <p className="text-sm leading-relaxed text-ink-soft">{plan.plan.description}</p>}
            <PlanKhmerWords plan={plan} />
            <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {plan.entitlements.map((e) => (
                <div key={e.key} className="rounded-[12px] bg-paper px-4 py-3 ring-1 ring-line">
                  <dt className="text-sm text-ink-faint">{entitlementLabel(e.key)}</dt>
                  <dd className="font-semibold text-ink">{limitText(e.limit, e.key)}</dd>
                </div>
              ))}
              <div className="rounded-[12px] bg-paper px-4 py-3 ring-1 ring-line sm:col-span-2">
                <dt className="text-sm text-ink-faint">{all.price}</dt>
                <dd className="font-semibold text-ink">{priceText(plan.plan.price) ?? all.notSet}</dd>
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
                  {m.makeDefault}
                </Button>
                <Button variant="ghost" size="sm" onClick={() => setConfirming("retire")}>
                  {m.retire}
                </Button>
              </div>
            )}
          </>
        )}

        {confirming && (
          <div
            role="group"
            aria-label={m.confirm}
            className="flex flex-col gap-3 rounded-[12px] bg-light-soft/60 p-4 ring-1 ring-light/40"
          >
            <p className="text-sm font-medium text-ink">{CONFIRM[confirming].question}</p>
            <div className="flex flex-wrap gap-2">
              <Button size="sm" onClick={() => act(confirming)} disabled={busy}>
                {busy ? m.working : CONFIRM[confirming].label}
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setConfirming(null)} disabled={busy}>
                {all.requests.cancel}
              </Button>
            </div>
          </div>
        )}
        <OfferDates plan={plan} onChanged={changed} />
        {!confirming && <DuplicatePlan plan={plan} onDuplicated={onDuplicated} />}
        {/* Every change to a plan is audited (PLAN_*): who did what, when, with the values before and after. */}
        <section aria-labelledby={`plan-history-${plan.plan.id}`} className="flex flex-col gap-2">
          <h3 id={`plan-history-${plan.plan.id}`} className="text-sm font-semibold text-ink">
            {m.history}
          </h3>
          <AuditLog refreshKey={historyVersion} filter={{ resourceId: plan.plan.id }} emptyTitle={m.historyEmpty} />
        </section>
      </div>
    </Panel>
  );
}

/** "2026-11-01" in the browser's day, as the instant that day starts. */
const dayStart = (day: string) => new Date(`${day}T00:00:00`).toISOString();
/** An instant as the browser's calendar day ("2026-11-01"). */
/**
 * An offered plan's Khmer, read-only: its words are fixed once it is offered (V82), so a plan offered without Khmer says
 * how to give it some (a copy, which is a draft) rather than offering an edit the server would refuse.
 */
function PlanKhmerWords({ plan }: { plan: PlanResponse }) {
  const m = useMessages(plansMessages).detail;
  const khmer = plan.plan.khmer;
  if (!khmer?.name && !khmer?.description) {
    return <p className="text-sm text-ink-soft">{m.khmerNoneOffered}</p>;
  }
  return (
    <div className="flex flex-col gap-1 rounded-[12px] bg-paper px-4 py-3 ring-1 ring-line">
      <p className="text-sm font-semibold text-ink">{m.khmerWords}</p>
      {khmer.name && (
        <p lang="km" className="text-sm text-ink [overflow-wrap:anywhere]">
          {khmer.name}
        </p>
      )}
      {khmer.description && (
        <p lang="km" className="text-sm leading-relaxed text-ink-soft [overflow-wrap:anywhere]">
          {khmer.description}
        </p>
      )}
      {(!khmer.name || (plan.plan.description && !khmer.description)) && <p className="text-xs text-ink-soft">{m.khmerPartlyOffered}</p>}
    </div>
  );
}

function dayOf(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
/** The last day offered is shown and chosen; the server keeps the moment the offer ends (the next day's start). */
function lastDayOf(untilIso: string): string {
  const d = new Date(untilIso);
  d.setDate(d.getDate() - 1);
  return dayOf(d.toISOString());
}
function dayAfter(day: string): string {
  const d = new Date(`${day}T00:00:00`);
  d.setDate(d.getDate() + 1);
  return d.toISOString();
}

/** Why an active plan isn't offered now: its offer starts later, or has ended (the server decides offeredNow). */
function offerState(p: PlanResponse): string {
  const m = messagesNow(plansMessages).offer;
  return p.availableFrom && new Date(p.availableFrom).getTime() > Date.now() ? m.notYet : m.over;
}

/**
 * V80: when new customers can take the plan up. A family or school already on it keeps it whatever the dates say;
 * the default plan is always offered; a retired plan is offered to nobody.
 */
function OfferDates({ plan, onChanged }: { plan: PlanResponse; onChanged: () => void }) {
  const m = useMessages(plansMessages).offer;
  const [open, setOpen] = useState(false);
  const [from, setFrom] = useState(plan.availableFrom ? dayOf(plan.availableFrom) : "");
  const [until, setUntil] = useState(plan.availableUntil ? lastDayOf(plan.availableUntil) : "");
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ ok: boolean; text: string } | null>(null);
  const status = plan.plan.status;
  const showDay = (day: string) => dateOnly(`${day}T12:00:00`);
  const fromDay = plan.availableFrom ? dayOf(plan.availableFrom) : null;
  const lastDay = plan.availableUntil ? lastDayOf(plan.availableUntil) : null;
  const wrongOrder = Boolean(from && until && until < from);

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setFeedback(null);
    try {
      await plansApi.setAvailability(plan.plan.id, from ? dayStart(from) : null, until ? dayAfter(until) : null);
      setOpen(false);
      setFeedback({ ok: true, text: m.saved });
      onChanged();
    } catch (err) {
      setFeedback({ ok: false, text: errorText(err, m.failed) });
    } finally {
      setBusy(false);
    }
  }

  let line: string;
  if (plan.plan.isDefault) line = m.defaultAlways;
  else if (status === "RETIRED") line = m.retired;
  else if (!fromDay && !lastDay) line = m.always;
  else line = m.window(fromDay && showDay(fromDay), lastDay && showDay(lastDay));
  const editable = !plan.plan.isDefault && status !== "RETIRED";

  return (
    <section aria-label={m.title} className="flex flex-col gap-2 rounded-[12px] bg-paper px-4 py-3 ring-1 ring-line">
      <h3 className="text-sm font-semibold text-ink">{m.title}</h3>
      <p className="text-sm text-ink">
        {line}
        {status === "ACTIVE" && plan.offeredNow === false && !plan.plan.isDefault && (
          <span className="ml-1 font-semibold text-light-deep">({offerState(plan)})</span>
        )}
      </p>
      {status === "DRAFT" && (fromDay || lastDay) && <p className="text-xs text-ink-soft">{m.draftNote}</p>}
      {editable && !open && (
        <Button size="sm" variant="ghost" className="w-fit" onClick={() => setOpen(true)}>
          {m.change}
        </Button>
      )}
      {editable && open && (
        <form onSubmit={save} className="flex flex-col gap-3">
          <p className="text-xs text-ink-soft">{m.lead}</p>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <FormField label={m.from} type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            <FormField
              label={m.until}
              type="date"
              value={until}
              min={from || undefined}
              error={wrongOrder ? m.endBeforeStart : undefined}
              onChange={(e) => setUntil(e.target.value)}
            />
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" size="sm" disabled={busy || wrongOrder}>
              {busy ? m.saving : m.save}
            </Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => setOpen(false)} disabled={busy}>
              {m.cancel}
            </Button>
          </div>
        </form>
      )}
      {feedback && <Feedback ok={feedback.ok} text={feedback.text} />}
    </section>
  );
}

/** Find a family by a parent's exact address and give it a plan, or end the one it has. */
function FamilyAccess({ plans, onFamilyChanged }: { plans: PlanResponse[]; onFamilyChanged: () => void }) {
  // ?parentEmail= (a link from the overview) opens that family straight away.
  const [params] = useSearchParams();
  const linked = params.get("parentEmail") ?? "";
  const [email, setEmail] = useState(linked);
  const [finding, setFinding] = useState(false);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [family, setFamily] = useState<FamilyAccountSummary | null>(null);
  const m = useMessages(plansMessages).family;

  // The address last opened from a link: a new link (e.g. from a plan request) opens its family too.
  const openedLink = useRef<string | null>(null);
  useEffect(() => {
    if (!linked || openedLink.current === linked) return;
    openedLink.current = linked;
    setEmail(linked);
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
          ? m.notFound
          : errorText(err, m.lookupFailed),
      );
    } finally {
      setFinding(false);
    }
  }

  return (
    <Panel title={m.title}>
      <div className="flex flex-col gap-5">
        <form onSubmit={onFind} className="flex flex-col gap-3 sm:flex-row sm:items-end" noValidate>
          <div className="min-w-0 flex-1">
            <FormField
              label={m.email}
              type="email"
              autoComplete="off"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={lookupError ?? undefined}
            />
          </div>
          <Button type="submit" disabled={finding || !email.trim()} className="w-fit">
            <Search size={16} aria-hidden /> {finding ? m.finding : m.find}
          </Button>
        </form>
        {family && (
          <FamilyPlanCard
            key={family.familyId}
            family={family}
            plans={plans}
            onChanged={(next) => {
              setFamily(next);
              onFamilyChanged();
            }}
          />
        )}
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
  const m = useMessages(plansMessages).family;

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
        text: m.given(updated.plan.plan.name, until ? dateOnly(new Date(`${until}T23:59:59`).toISOString()) : null),
      });
      setUntil("");
      setNote("");
      setHistoryVersion((v) => v + 1);
      onChanged(updated);
    } catch (err) {
      setFeedback({ where: "grant", ok: false, text: errorText(err, m.grantFailed) });
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
        text: periodEnd ? m.runsUntil(dateOnly(periodEnd)) : m.openEnded,
      });
      setMoving(false);
      setNewEnd("");
      setMoveReason("");
      setHistoryVersion((v) => v + 1);
      onChanged(updated);
    } catch (err) {
      setFeedback({ where: "plan", ok: false, text: errorText(err, m.moveFailed) });
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
      setFeedback({ where: "plan", ok: true, text: m.ended });
      setEnding(false);
      setHistoryVersion((v) => v + 1);
      onChanged(updated);
    } catch (err) {
      setFeedback({ where: "plan", ok: false, text: errorText(err, m.didntWork) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid grid-cols-1 gap-6 border-t border-line pt-5 lg:grid-cols-2">
      <div className="flex flex-col gap-3">
        <p className="font-semibold text-tonle-950">{family.familyName}</p>
        <p className="text-sm text-ink-soft">
          {family.parents.map((p) => p.email).join(", ")} · {m.children(family.childCount)}
        </p>
        <div className="rounded-[12px] bg-paper px-4 py-3 ring-1 ring-line">
          <p className="font-semibold text-ink">
            {family.plan.plan.name}
            {family.plan.onDefaultPlan && <span className="font-normal text-ink-soft"> {m.defaultSuffix}</span>}
          </p>
          <p className="text-sm text-ink-soft">
            {sub && sub.grantsAccess
              ? m.givenOn(dateOnly(sub.startedAt), sub.currentPeriodEnd ? dateOnly(sub.currentPeriodEnd) : null)
              : sub
                ? m.earlierEnded(sub.currentPeriodEnd ? dateOnly(sub.currentPeriodEnd) : null)
                : m.noSubscription}
          </p>
          {sub?.note && <p className="mt-1 text-sm text-ink-faint">{m.note(sub.note)}</p>}
          <ul className="mt-2 flex flex-col gap-0.5 text-sm text-ink">
            {family.plan.entitlements.map((e) => (
              <li key={e.key}>
                {entitlementLabel(e.key)}:{" "}
                {e.key === "CHILDREN_MAX" ? m.childrenUsage(e.used ?? 0, e.limit) : m.journeysUsage(e.used ?? 0, e.limit)}
              </li>
            ))}
          </ul>
        </div>
        {sub?.grantsAccess && sub.source === "PROVIDER" && (
          <p className="text-sm text-ink-soft">{m.provider}</p>
        )}
        {sub?.grantsAccess && sub.source === "ADMIN_GRANT" && !ending && !moving && (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" className="w-fit" onClick={() => setMoving(true)}>
              {m.changeEnd}
            </Button>
            <Button variant="ghost" size="sm" className="w-fit" onClick={() => setEnding(true)}>
              {m.endPlan}
            </Button>
          </div>
        )}
        {moving && (
          <form onSubmit={move} className="flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line">
            <p className="text-sm text-ink-soft">
              {m.sameStart} {sub?.currentPeriodEnd ? m.nowUntil(dateOnly(sub.currentPeriodEnd)) : m.nowOpen}
            </p>
            <FormField
              label={m.newEnd}
              type="date"
              value={newEnd}
              onChange={(e) => setNewEnd(e.target.value)}
            />
            <FormField
              label={m.whyChange}
              value={moveReason}
              maxLength={500}
              required
              onChange={(e) => setMoveReason(e.target.value)}
            />
            <div className="flex gap-2">
              <Button type="submit" size="sm" disabled={busy || !moveReason.trim()}>
                {busy ? m.saving : m.saveEnd}
              </Button>
              <Button type="button" size="sm" variant="ghost" onClick={() => setMoving(false)}>
                {m.cancel}
              </Button>
            </div>
          </form>
        )}
        {ending && (
          <form onSubmit={end} className="flex flex-col gap-3 rounded-[12px] bg-light-soft/60 p-4 ring-1 ring-light/40">
            <FormField
              label={m.whyEnd}
              value={reason}
              maxLength={500}
              required
              onChange={(e) => setReason(e.target.value)}
            />
            <div className="flex gap-2">
              <Button type="submit" size="sm" disabled={busy || !reason.trim()}>
                {busy ? m.ending : m.endNow}
              </Button>
              <Button type="button" size="sm" variant="ghost" onClick={() => setEnding(false)}>
                {m.cancel}
              </Button>
            </div>
          </form>
        )}
        {feedback?.where === "plan" && <Feedback {...feedback} />}
      </div>

      {givable.length === 0 ? (
        <div className="flex flex-col gap-2">
          <p className="text-sm font-semibold text-ink">{m.give}</p>
          <p className="text-sm text-ink-soft">{m.nothingToGive}</p>
        </div>
      ) : (
        <form onSubmit={grant} className="flex flex-col gap-4">
          <div>
            <p className="text-sm font-semibold text-ink">{sub?.grantsAccess ? m.replace : m.give}</p>
            {sub?.grantsAccess && <p className="text-sm text-ink-soft">{m.replaceNote(family.plan.plan.name)}</p>}
          </div>
          <SelectField label={m.plan} value={planId} onChange={(e) => setPlanId(e.target.value)}>
            {givable.map((p) => (
              <option key={p.plan.id} value={p.plan.id}>
                {p.plan.name}
              </option>
            ))}
          </SelectField>
          <FormField
            label={m.until}
            type="date"
            value={until}
            onChange={(e) => setUntil(e.target.value)}
          />
          <FormField
            label={m.adminNote}
            value={note}
            maxLength={500}
            onChange={(e) => setNote(e.target.value)}
          />
          {feedback?.where === "grant" && <Feedback {...feedback} />}
          <Button type="submit" disabled={busy || !planId} className="w-fit">
            {busy ? m.giving : sub?.grantsAccess ? m.replaceButton : m.giveButton}
          </Button>
        </form>
      )}

      {sub && (
        <section aria-labelledby="subscription-history" className="flex flex-col gap-2 lg:col-span-2">
          <h3 id="subscription-history" className="text-sm font-semibold text-ink">
            {m.history}
          </h3>
          <AuditLog refreshKey={historyVersion} filter={{ resourceId: sub.id }} emptyTitle={m.noHistory} />
        </section>
      )}
      <div className="lg:col-span-2">
        <CustomerPayments
          customer={{ familyId: family.familyId }}
          words={{ title: m.paymentsTitle, none: m.paymentsNone, loading: m.paymentsLoading, all: m.paymentsAll }}
        />
      </div>
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
