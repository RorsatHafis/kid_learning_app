import { useState, type FormEvent } from "react";
import { useAsync } from "@/lib/hooks/useAsync";
import { plansApi, schoolPlansApi, type SchoolPlan } from "@/lib/api/subscriptions";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { useMessages } from "@/i18n/catalog";
import { formatDate } from "@/i18n/format";
import { SchoolPlanUsage } from "@/features/school/SchoolPlanCard";
import { planSourceText } from "@/features/school/planUsage";
import { schoolPlanMessages } from "@/features/school/plan.messages";
import { Panel, SelectField } from "../studio/shared";
import { errorText } from "../studio/studioLogic";
import { AuditLog } from "./AuditLog";
import { adminSchoolPlanMessages } from "./schoolPlan.messages";

const dateOnly = (iso: string) => formatDate(iso, { day: "numeric", month: "short", year: "numeric" });
const endOfDay = (date: string) => new Date(`${date}T23:59:59`).toISOString();

/**
 * A school's plan, for an administrator: what it includes and holds, and giving, moving or ending
 * it (V59). Every change is audited and ending one removes nothing the school holds.
 */
export function SchoolPlanPanel({ schoolId }: { schoolId: string }) {
  const current = useAsync(() => schoolPlansApi.get(schoolId), [schoolId]);
  const plans = useAsync(() => plansApi.list(), []);
  const m = useMessages(adminSchoolPlanMessages);
  const shared = useMessages(schoolPlanMessages);
  const [override, setOverride] = useState<SchoolPlan | null>(null);
  const plan = override ?? current.data;

  return (
    <Panel title={m.title}>
      {current.isLoading && !plan && <LoadingState label={m.loading} />}
      {current.error && <ErrorState error={current.error} onRetry={current.refetch} />}
      {plan && (
        <SchoolPlanControls
          plan={plan}
          givable={(plans.data ?? []).filter((p) => p.plan.audience === "SCHOOL" && p.plan.status === "ACTIVE" && !p.plan.isDefault)}
          sourceText={planSourceText(plan, shared)}
          defaultSuffix={shared.defaultSuffix}
          onChanged={setOverride}
          m={m}
        />
      )}
    </Panel>
  );
}

function SchoolPlanControls({
  plan,
  givable,
  sourceText,
  defaultSuffix,
  onChanged,
  m,
}: {
  plan: SchoolPlan;
  givable: { plan: { id: string; name: string } }[];
  sourceText: string;
  defaultSuffix: string;
  onChanged: (plan: SchoolPlan) => void;
  m: typeof adminSchoolPlanMessages.en;
}) {
  const sub = plan.subscription;
  const [planId, setPlanId] = useState("");
  const [until, setUntil] = useState("");
  const [note, setNote] = useState("");
  const [mode, setMode] = useState<"idle" | "move" | "end">("idle");
  const [newEnd, setNewEnd] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [historyVersion, setHistoryVersion] = useState(0);
  const [feedback, setFeedback] = useState<{ where: "plan" | "grant"; ok: boolean; text: string } | null>(null);
  const chosen = planId || givable[0]?.plan.id || "";

  async function run(where: "plan" | "grant", action: () => Promise<SchoolPlan>, ok: (next: SchoolPlan) => string, failed: string) {
    setBusy(true);
    setFeedback(null);
    try {
      const next = await action();
      setFeedback({ where, ok: true, text: ok(next) });
      setMode("idle");
      setReason("");
      setNewEnd("");
      setHistoryVersion((v) => v + 1);
      onChanged(next);
    } catch (err) {
      setFeedback({ where, ok: false, text: errorText(err, failed) });
    } finally {
      setBusy(false);
    }
  }

  function grant(e: FormEvent) {
    e.preventDefault();
    const periodEnd = until ? endOfDay(until) : null;
    void run(
      "grant",
      () => schoolPlansApi.grant(plan.schoolId, { planId: chosen, periodEnd, note: note.trim() || null }),
      (next) => m.given(next.plan.name, periodEnd ? dateOnly(periodEnd) : null),
      m.grantFailed,
    );
  }

  function move(e: FormEvent) {
    e.preventDefault();
    const periodEnd = newEnd ? endOfDay(newEnd) : null;
    void run(
      "plan",
      () => schoolPlansApi.changePeriod(plan.schoolId, periodEnd, reason.trim()),
      () => (periodEnd ? m.runsUntil(dateOnly(periodEnd)) : m.openEnded),
      m.moveFailed,
    );
  }

  function end(e: FormEvent) {
    e.preventDefault();
    void run("plan", () => schoolPlansApi.end(plan.schoolId, reason.trim()), () => m.ended, m.didntWork);
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3">
        <p className="font-semibold text-ink">
          {plan.plan.name}
          {plan.onDefaultPlan && <span className="font-normal text-ink-soft"> {defaultSuffix}</span>}
        </p>
        <p className="text-sm text-ink-soft">{sourceText}</p>
        {sub?.note && <p className="text-sm text-ink-faint">{m.note(sub.note)}</p>}
        <SchoolPlanUsage entitlements={plan.entitlements} />
        {sub?.grantsAccess && sub.source === "PROVIDER" && <p className="text-sm text-ink-soft">{m.provider}</p>}
        {sub?.grantsAccess && sub.source === "ADMIN_GRANT" && mode === "idle" && (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={() => setMode("move")}>
              {m.changeEnd}
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setMode("end")}>
              {m.endPlan}
            </Button>
          </div>
        )}
        {mode === "move" && (
          <form onSubmit={move} className="flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line">
            <FormField label={m.newEnd} type="date" value={newEnd} onChange={(e) => setNewEnd(e.target.value)} />
            <FormField label={m.whyChange} value={reason} maxLength={500} required onChange={(e) => setReason(e.target.value)} />
            <div className="flex gap-2">
              <Button type="submit" size="sm" disabled={busy || !reason.trim()}>
                {busy ? m.saving : m.saveEnd}
              </Button>
              <Button type="button" size="sm" variant="ghost" onClick={() => setMode("idle")}>
                {m.cancel}
              </Button>
            </div>
          </form>
        )}
        {mode === "end" && (
          <form
            onSubmit={end}
            aria-label={m.endPlan}
            className="flex flex-col gap-3 rounded-[12px] bg-light-soft/60 p-4 ring-1 ring-light/40"
          >
            <p className="text-sm font-medium text-ink">{m.endQuestion}</p>
            <FormField label={m.whyEnd} value={reason} maxLength={500} required onChange={(e) => setReason(e.target.value)} />
            <div className="flex gap-2">
              <Button type="submit" size="sm" disabled={busy || !reason.trim()}>
                {busy ? m.ending : m.endNow}
              </Button>
              <Button type="button" size="sm" variant="ghost" onClick={() => setMode("idle")}>
                {m.cancel}
              </Button>
            </div>
          </form>
        )}
        {feedback?.where === "plan" && <Feedback {...feedback} />}
      </div>

      <div className="border-t border-line pt-5">
        {givable.length === 0 ? (
          <div className="flex flex-col gap-1">
            <p className="text-sm font-semibold text-ink">{m.give}</p>
            <p className="text-sm text-ink-soft">{m.nothingToGive}</p>
          </div>
        ) : (
          <form onSubmit={grant} className="flex flex-col gap-4">
            <div>
              <p className="text-sm font-semibold text-ink">{sub?.grantsAccess ? m.replace : m.give}</p>
              {sub?.grantsAccess && <p className="text-sm text-ink-soft">{m.replaceNote(plan.plan.name)}</p>}
            </div>
            <SelectField label={m.plan} value={chosen} onChange={(e) => setPlanId(e.target.value)}>
              {givable.map((p) => (
                <option key={p.plan.id} value={p.plan.id}>
                  {p.plan.name}
                </option>
              ))}
            </SelectField>
            <FormField label={m.until} type="date" value={until} onChange={(e) => setUntil(e.target.value)} />
            <FormField label={m.adminNote} value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} />
            {feedback?.where === "grant" && <Feedback {...feedback} />}
            <Button type="submit" disabled={busy || !chosen} className="w-fit">
              {busy ? m.giving : sub?.grantsAccess ? m.replaceButton : m.giveButton}
            </Button>
          </form>
        )}
      </div>

      {sub && (
        <section aria-labelledby="school-subscription-history" className="flex flex-col gap-2">
          <h3 id="school-subscription-history" className="text-sm font-semibold text-ink">
            {m.history}
          </h3>
          <AuditLog refreshKey={historyVersion} filter={{ resourceId: sub.id }} emptyTitle={m.noHistory} />
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
