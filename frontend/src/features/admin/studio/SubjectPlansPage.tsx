/**
 * V89 (owner 2026-11-10): which family plans include this subject. Administrators only. The plans are the plan records
 * themselves (Admin > Plans) - their names in the reader's language, never copied here; the rule is the server's.
 * "Every plan" is how every subject starts, so nothing changes until an administrator chooses.
 */
import { useId, useState, type FormEvent } from "react";
import { Check } from "lucide-react";
import { studioApi, type SubjectPlanAssignment } from "@/lib/api/studio";
import { Button } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { useAsync } from "@/lib/hooks/useAsync";
import { useLocale, useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { Panel } from "./shared";
import { errorText, useStudio } from "./studioLogic";
import { subjectPlansMessages } from "./subjectPlans.messages";

export function SubjectPlansPage() {
  const { catalog } = useStudio();
  const subjectId = catalog.subject.id;
  const loaded = useAsync(() => studioApi.subjectPlans(subjectId), [subjectId]);
  const m = useMessages(subjectPlansMessages);

  if (loaded.isLoading && !loaded.data) return <LoadingState label={m.loading} />;
  if (loaded.error) return <ErrorState error={loaded.error} onRetry={loaded.refetch} />;
  if (!loaded.data) return null;
  return <PlansForm key={subjectId} subjectName={catalog.subject.name} initial={loaded.data} />;
}

function PlansForm({ subjectName, initial }: { subjectName: string; initial: SubjectPlanAssignment }) {
  const m = useMessages(subjectPlansMessages);
  const locale = useLocale();
  const [saved, setSaved] = useState(initial);
  const [everyPlan, setEveryPlan] = useState(initial.everyPlan);
  const [chosen, setChosen] = useState<string[]>(initial.planIds);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const groupName = useId();
  const hintId = useId();

  const changed = everyPlan !== saved.everyPlan || (!everyPlan && [...chosen].sort().join() !== [...saved.planIds].sort().join());
  function toggle(id: string) {
    setDone(false);
    setChosen((list) => (list.includes(id) ? list.filter((x) => x !== id) : [...list, id]));
  }

  async function save(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!everyPlan && chosen.length === 0) {
      setError(m.chooseOne);
      return;
    }
    setBusy(true);
    try {
      const next = await studioApi.assignSubjectPlans(saved.subjectId, everyPlan ? { everyPlan: true } : { everyPlan: false, planIds: chosen });
      setSaved(next);
      setEveryPlan(next.everyPlan);
      setChosen(next.planIds);
      setDone(true);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  const name = (plan: SubjectPlanAssignment["plans"][number]) => (locale === "km" && plan.nameKm ? plan.nameKm : plan.name);

  return (
    <Panel title={m.title}>
      <form onSubmit={(e) => void save(e)} className="flex flex-col gap-5" aria-describedby={hintId}>
        <p id={hintId} className="max-w-2xl text-sm leading-relaxed text-ink-soft">
          {m.lead(subjectName)}
        </p>

        <fieldset className="flex flex-col gap-3">
          <legend className="mb-2 text-sm font-semibold text-ink">{m.whoGetsIt}</legend>
          <Choice
            name={groupName}
            checked={everyPlan}
            onChange={() => (setEveryPlan(true), setDone(false))}
            label={m.everyPlan}
            hint={m.everyPlanHint}
          />
          <Choice
            name={groupName}
            checked={!everyPlan}
            onChange={() => (setEveryPlan(false), setDone(false))}
            label={m.onlyThese}
            hint={m.onlyTheseHint}
          />
        </fieldset>

        {!everyPlan && (
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-sm font-semibold text-ink">
              {m.plans} <span className="font-normal text-ink-soft">({m.required})</span>
            </legend>
            {saved.plans.length === 0 && <p className="text-sm text-ink-soft">{m.noPlans}</p>}
            <ul className="flex flex-col gap-2">
              {saved.plans.map((plan) => {
                const on = chosen.includes(plan.id);
                return (
                  <li key={plan.id}>
                    <label
                      className={cn(
                        "flex min-h-12 cursor-pointer items-start gap-3 rounded-[12px] px-3.5 py-3 ring-1 ring-inset transition-colors",
                        on ? "bg-tonle-50 ring-tonle-300" : "bg-white ring-line hover:bg-paper",
                      )}
                    >
                      <input type="checkbox" checked={on} onChange={() => toggle(plan.id)} className="mt-0.5 h-5 w-5 shrink-0 accent-tonle-700" />
                      <span className="flex min-w-0 flex-col">
                        <span className="font-semibold text-ink [overflow-wrap:anywhere]">{name(plan)}</span>
                        <span className="text-xs text-ink-soft">
                          {[
                            plan.defaultPlan ? m.defaultPlan : null,
                            plan.status === "DRAFT" ? m.draft : plan.status === "RETIRED" ? m.retired : null,
                            plan.code,
                          ]
                            .filter(Boolean)
                            .join(" · ")}
                        </span>
                      </span>
                    </label>
                  </li>
                );
              })}
            </ul>
          </fieldset>
        )}

        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <div className="flex flex-wrap items-center gap-3 border-t border-line pt-4">
          <Button type="submit" size="md" disabled={busy || !changed}>
            {busy ? m.saving : m.save}
          </Button>
          {done && !changed && (
            <span role="status" className="flex items-center gap-1.5 text-sm font-semibold text-palm-deep">
              <Check size={16} aria-hidden /> {m.saved}
            </span>
          )}
        </div>
      </form>
    </Panel>
  );
}

function Choice({ name, checked, onChange, label, hint }: { name: string; checked: boolean; onChange: () => void; label: string; hint: string }) {
  return (
    <label
      className={cn(
        "flex min-h-12 cursor-pointer items-start gap-3 rounded-[12px] px-3.5 py-3 ring-1 ring-inset transition-colors",
        checked ? "bg-tonle-50 ring-tonle-300" : "bg-white ring-line hover:bg-paper",
      )}
    >
      <input type="radio" name={name} checked={checked} onChange={onChange} className="mt-0.5 h-5 w-5 shrink-0 accent-tonle-700" />
      <span className="flex min-w-0 flex-col">
        <span className="font-semibold text-ink">{label}</span>
        <span className="text-sm text-ink-soft">{hint}</span>
      </span>
    </label>
  );
}
