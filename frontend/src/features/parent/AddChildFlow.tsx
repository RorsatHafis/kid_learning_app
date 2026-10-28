import { useMemo, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { ArrowLeft, Check, ChevronDown } from "lucide-react";
import { FormField } from "@/features/auth/FormField";
import { Button, buttonClasses } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { ApiError } from "@/lib/api/ApiError";
import { childrenApi, enrollmentApi } from "@/lib/api/endpoints";
import type { ApiProblem, Child, CurriculumChoice } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { SubjectEmblem } from "@/features/learning/SubjectEmblem";
import { subjectTheme } from "@/features/learning/subjectTheme";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { PlanLimitNote } from "./PlanLimitNote";
import { setupMessages } from "./setup.messages";

type Step = "about" | "subjects" | "review";

/**
 * Adding a child is three short steps: who they are, what they learn, a look at the plan. The child
 * is created on the server at the end of step one (with nothing chosen for them), and its id is kept
 * in the address, so a reload or an interrupted setup resumes at the subjects. The subjects are the
 * server's catalog for this child's age - only published platform curricula - and each one chosen is
 * enrolled through the same rules as "Add a subject" later on.
 */
export function AddChildFlow() {
  const [params, setParams] = useSearchParams();
  const childId = params.get("child");
  const m = useMessages(setupMessages);
  useDocumentTitle(m.title);
  const child = useAsync(() => (childId ? childrenApi.get(childId) : Promise.resolve(null)), [childId]);
  const [step, setStep] = useState<Step>(childId ? "subjects" : "about");
  const [chosen, setChosen] = useState<string[]>([]);

  const steps: { key: Step; label: string }[] = [
    { key: "about", label: m.steps.about },
    { key: "subjects", label: m.steps.subjects },
    { key: "review", label: m.steps.review },
  ];
  const index = steps.findIndex((s) => s.key === step);

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-8">
      <nav aria-label={m.title}>
        <Link to="/parent" className="inline-flex items-center gap-1.5 text-sm font-semibold text-tonle-700 underline-offset-4 hover:underline">
          <ArrowLeft size={15} aria-hidden /> {m.about.cancel}
        </Link>
      </nav>
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-[240px_minmax(0,1fr)] lg:gap-12">
        <aside className="flex flex-col gap-6">
          <h1 className="font-display text-headline font-bold text-tonle-950">{m.title}</h1>
          <ol className="flex gap-2 lg:flex-col lg:gap-3" aria-label={m.stepOf(index + 1, steps.length)}>
            {steps.map((s, i) => {
              const done = i < index;
              const current = i === index;
              return (
                <li key={s.key} aria-current={current ? "step" : undefined} className="flex flex-1 items-center gap-3 lg:flex-none">
                  <span
                    className={cn(
                      "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-bold",
                      done ? "bg-palm text-white" : current ? "bg-tonle-800 text-white" : "bg-white text-ink-faint ring-1 ring-line-strong",
                    )}
                  >
                    {done ? <Check size={16} strokeWidth={3} aria-hidden /> : i + 1}
                  </span>
                  <span className={cn("hidden text-sm font-semibold sm:inline", current ? "text-tonle-950" : "text-ink-soft")}>{s.label}</span>
                </li>
              );
            })}
          </ol>
          <div aria-hidden className="hidden items-end gap-1 lg:flex">
            <LearningBuddy mood={step === "review" ? "cheer" : "happy"} size={120} className="!animate-none" />
          </div>
        </aside>

        <section className="min-w-0 rounded-[28px] bg-white p-6 shadow-card sm:p-9" aria-labelledby="setup-step">
          <p className="mb-1 text-sm font-semibold text-ink-soft">{m.stepOf(index + 1, steps.length)}</p>
          {step === "about" && (
            <AboutStep
              onCreated={(created) => {
                setParams({ child: created.id }, { replace: true });
                setStep("subjects");
              }}
            />
          )}
          {step !== "about" && child.isLoading && <LoadingState />}
          {step !== "about" && child.error && <ErrorState error={child.error} onRetry={child.refetch} />}
          {step === "subjects" && child.data && (
            <SubjectsStep child={child.data} chosen={chosen} onChange={setChosen} onNext={() => setStep("review")} />
          )}
          {step === "review" && child.data && (
            <ReviewStep child={child.data} chosen={chosen} onBack={() => setStep("subjects")} />
          )}
        </section>
      </div>
    </div>
  );
}

function AboutStep({ onCreated }: { onCreated: (child: Child) => void }) {
  const m = useMessages(setupMessages).about;
  const [displayName, setDisplayName] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [planLimit, setPlanLimit] = useState<ApiProblem | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    setPlanLimit(null);
    setFieldErrors({});
    setBusy(true);
    try {
      onCreated(await childrenApi.create({ displayName, dateOfBirth, familyChoosesSubjects: true }));
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors) setFieldErrors(err.validationErrors);
      else if (err instanceof ApiError) {
        setFormError(err.problem?.detail ?? m.failed);
        setPlanLimit(err.isPlanLimit ? err.problem : null);
      } else setFormError(m.somethingWrong);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-6">
      <div>
        <h2 id="setup-step" className="font-display text-title font-bold text-tonle-950">
          {m.heading}
        </h2>
        <p className="mt-1.5 max-w-xl text-ink-soft">{m.lead}</p>
      </div>
      <div className="grid gap-5 sm:grid-cols-2">
        <FormField
          label={m.name}
          required
          autoFocus
          maxLength={80}
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          error={fieldErrors.displayName}
          hint={m.nameHint}
        />
        <FormField
          label={m.dateOfBirth}
          type="date"
          required
          value={dateOfBirth}
          onChange={(e) => setDateOfBirth(e.target.value)}
          error={fieldErrors.dateOfBirth}
          max={new Date().toISOString().slice(0, 10)}
        />
      </div>
      {formError && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {formError}
        </p>
      )}
      {planLimit && <PlanLimitNote problem={planLimit} />}
      <div className="flex flex-wrap gap-3">
        <Button type="submit" size="lg" disabled={busy}>
          {busy ? m.creating : m.next}
        </Button>
        <Link to="/parent" className={buttonClasses("ghost", "lg")}>
          {m.cancel}
        </Link>
      </div>
    </form>
  );
}

/** Curricula grouped by subject, in the server's order: the family chooses subjects, not rows. */
function bySubject(list: CurriculumChoice[]) {
  const groups = new Map<string, CurriculumChoice[]>();
  for (const c of list) groups.set(c.subjectCode, [...(groups.get(c.subjectCode) ?? []), c]);
  return [...groups.values()];
}

/** A subject: one card. With one path for this age the card is the choice; with several, each path is. */
function SubjectGroup({ choices, chosen, onToggle }: { choices: CurriculumChoice[]; chosen: string[]; onToggle: (id: string) => void }) {
  const m = useMessages(setupMessages).subjects;
  if (choices.length === 1) {
    return <SubjectCard choice={choices[0]} chosen={chosen.includes(choices[0].id)} onToggle={() => onToggle(choices[0].id)} />;
  }
  const first = choices[0];
  const t = subjectTheme(first.subjectCode, first.subjectName);
  const any = choices.some((c) => chosen.includes(c.id));
  return (
    <div
      role="group"
      aria-label={first.subjectName}
      className={cn("flex flex-col gap-3 rounded-[22px] p-4 ring-1", any ? cn(t.soft, "ring-2", t.ring) : "bg-paper ring-line")}
    >
      <div className="flex items-center gap-4">
        <SubjectEmblem code={first.subjectCode} name={first.subjectName} size={64} />
        <span className="font-display text-xl font-bold text-tonle-950">{first.subjectName}</span>
      </div>
      <div className="flex flex-col gap-2">
        {choices.map((c) => {
          const on = chosen.includes(c.id);
          return (
            <button
              key={c.id}
              type="button"
              aria-pressed={on}
              onClick={() => onToggle(c.id)}
              className={cn(
                "flex items-center justify-between gap-3 rounded-[14px] px-4 py-2.5 text-left text-sm ring-1 transition-colors duration-150",
                on ? cn("bg-white font-semibold ring-2", t.ring, t.deep) : "bg-white/70 text-ink ring-line hover:bg-white",
              )}
            >
              <span>
                {c.name} · {m.ages(c.minAge, c.maxAge)}
              </span>
              {on && <Check size={16} strokeWidth={3} aria-hidden />}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/** One subject as the family sees it: its emblem, name and path, chosen or not. */
function SubjectCard({ choice, chosen, onToggle }: { choice: CurriculumChoice; chosen: boolean; onToggle: () => void }) {
  const m = useMessages(setupMessages).subjects;
  const t = subjectTheme(choice.subjectCode, choice.subjectName);
  return (
    <button
      type="button"
      aria-pressed={chosen}
      onClick={onToggle}
      className={cn(
        "group relative flex min-h-[132px] items-center gap-4 rounded-[22px] p-4 text-left ring-1 transition-[box-shadow,background-color,transform] duration-200 ease-out hover:-translate-y-0.5 hover:shadow-soft",
        chosen ? cn(t.soft, "ring-2", t.ring) : "bg-paper ring-line hover:bg-white",
      )}
    >
      <SubjectEmblem code={choice.subjectCode} name={choice.subjectName} size={84} />
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="font-display text-xl font-bold text-tonle-950">{choice.subjectName}</span>
        <span className="text-sm text-ink-soft">
          {choice.name} · {m.ages(choice.minAge, choice.maxAge)}
        </span>
        <span className={cn("mt-1 inline-flex items-center gap-1.5 text-sm font-semibold", chosen ? t.deep : "text-ink-faint")}>
          {chosen ? (
            <>
              <Check size={15} strokeWidth={3} aria-hidden /> {m.chosen}
            </>
          ) : (
            m.tapToChoose
          )}
        </span>
      </span>
    </button>
  );
}

function SubjectsStep({
  child,
  chosen,
  onChange,
  onNext,
}: {
  child: Child;
  chosen: string[];
  onChange: (ids: string[]) => void;
  onNext: () => void;
}) {
  const m = useMessages(setupMessages).subjects;
  const navigate = useNavigate();
  const catalog = useAsync(() => childrenApi.curricula(child.id), [child.id]);
  const [nudge, setNudge] = useState(false);
  const forAge = catalog.data?.filter((c) => c.forChildAge) ?? [];
  const otherAges = catalog.data?.filter((c) => !c.forChildAge) ?? [];
  const toggle = (id: string) => {
    setNudge(false);
    onChange(chosen.includes(id) ? chosen.filter((x) => x !== id) : [...chosen, id]);
  };

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 id="setup-step" className="font-display text-title font-bold text-tonle-950">
          {m.heading(child.displayName)}
        </h2>
        <p className="mt-1.5 max-w-xl text-ink-soft">{m.lead}</p>
      </div>
      {catalog.isLoading && <LoadingState label={m.loading} />}
      {catalog.error && <ErrorState error={catalog.error} onRetry={catalog.refetch} />}
      {catalog.data && catalog.data.length === 0 && <p className="rounded-[18px] bg-cream p-5 text-ink-soft">{m.none(child.displayName)}</p>}
      {/* The audience is 3-12 (D-09) but not every age has a path yet: say so before offering other ages' paths. */}
      {catalog.data && catalog.data.length > 0 && forAge.length === 0 && (
        <p className="rounded-[18px] bg-cream p-5 text-ink-soft">{m.noneForAge(child.displayName)}</p>
      )}
      {forAge.length > 0 && (
        <div className="flex flex-col gap-3">
          <h3 className="text-sm font-semibold text-ink-soft">{m.forAge(child.displayName)}</h3>
          <div className="grid gap-3 md:grid-cols-2">
            {bySubject(forAge).map((group) => (
              <SubjectGroup key={group[0].subjectCode} choices={group} chosen={chosen} onToggle={toggle} />
            ))}
          </div>
        </div>
      )}
      {otherAges.length > 0 && (
        <details className="group rounded-[18px] bg-paper/70 ring-1 ring-line" open={forAge.length === 0}>
          <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-5 py-4 text-sm font-semibold text-tonle-800">
            {m.otherAges(otherAges.length)}
            <ChevronDown size={17} className="transition-transform group-open:rotate-180" aria-hidden />
          </summary>
          <div className="grid gap-3 px-4 pb-4 md:grid-cols-2">
            {bySubject(otherAges).map((group) => (
              <SubjectGroup key={group[0].subjectCode} choices={group} chosen={chosen} onToggle={toggle} />
            ))}
          </div>
        </details>
      )}
      {nudge && (
        <p role="alert" className="text-sm font-medium text-light-deep">
          {m.needOne}
        </p>
      )}
      <div className="flex flex-wrap items-center gap-3 border-t border-line pt-5">
        <Button
          size="lg"
          onClick={() => {
            if (chosen.length === 0 && (catalog.data?.length ?? 0) > 0) setNudge(true);
            else onNext();
          }}
        >
          {m.next}
        </Button>
        <button
          type="button"
          onClick={() => navigate(`/parent/children/${child.id}`)}
          className={buttonClasses("ghost", "lg")}
        >
          {m.later}
        </button>
        <span className="text-sm font-medium text-ink-soft" aria-live="polite">
          {chosen.length > 0 && m.count(chosen.length)}
        </span>
      </div>
    </div>
  );
}

function ReviewStep({ child, chosen, onBack }: { child: Child; chosen: string[]; onBack: () => void }) {
  const m = useMessages(setupMessages).review;
  const navigate = useNavigate();
  const catalog = useAsync(() => childrenApi.curricula(child.id), [child.id]);
  const picked = useMemo(() => (catalog.data ?? []).filter((c) => chosen.includes(c.id)), [catalog.data, chosen]);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState<{ choice: CurriculumChoice; reason: string; planLimit: ApiProblem | null }[]>([]);
  const [started, setStarted] = useState<string[]>([]);

  async function start(only: CurriculumChoice[]) {
    setBusy(true);
    const problems: typeof failed = [];
    const ok: string[] = [];
    // One at a time, in the order shown: each is the same request as "Add a subject".
    for (const choice of only) {
      try {
        await enrollmentApi.enroll(child.id, choice.id);
        ok.push(choice.id);
      } catch (err) {
        problems.push({
          choice,
          reason: err instanceof ApiError ? (err.problem?.detail ?? choice.name) : choice.name,
          planLimit: err instanceof ApiError && err.isPlanLimit ? err.problem : null,
        });
      }
    }
    setBusy(false);
    setStarted((s) => [...s, ...ok]);
    setFailed(problems);
    if (problems.length === 0) navigate(`/parent/children/${child.id}`);
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 id="setup-step" className="font-display text-title font-bold text-tonle-950">
          {m.heading(child.displayName)}
        </h2>
        <p className="mt-1.5 max-w-xl text-ink-soft">{m.lead(child.displayName)}</p>
      </div>
      {catalog.isLoading && <LoadingState />}
      {catalog.data && picked.length === 0 && <p className="rounded-[18px] bg-cream p-5 text-ink-soft">{m.none(child.displayName)}</p>}
      {picked.length > 0 && (
        <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[22px] ring-1 ring-line">
          {picked.map((c) => {
            const t = subjectTheme(c.subjectCode, c.subjectName);
            return (
              <li key={c.id} className="flex items-center gap-4 bg-white p-4">
                <span aria-hidden className={cn("h-12 w-1.5 shrink-0 rounded-full", t.solid)} />
                <SubjectEmblem code={c.subjectCode} name={c.subjectName} size={56} />
                <span className="flex min-w-0 flex-col">
                  <span className="font-display text-lg font-bold text-tonle-950">{c.subjectName}</span>
                  <span className="text-sm text-ink-soft">{c.name}</span>
                </span>
                {started.includes(c.id) && <Check size={18} className="ml-auto text-palm" aria-hidden />}
              </li>
            );
          })}
        </ul>
      )}
      {failed.length > 0 && (
        <div role="alert" className="flex flex-col gap-2 rounded-[18px] bg-brick-soft p-4 text-sm text-brick-deep">
          <p className="font-semibold">{m.failedSome}</p>
          <ul className="list-disc pl-5">
            {failed.map((f) => (
              <li key={f.choice.id}>
                {f.choice.subjectName}: {f.reason}
              </li>
            ))}
          </ul>
          {failed.some((f) => f.planLimit) && <PlanLimitNote problem={failed.find((f) => f.planLimit)?.planLimit} />}
        </div>
      )}
      <div className="flex flex-wrap gap-3 border-t border-line pt-5">
        {failed.length > 0 ? (
          <>
            <Button size="lg" disabled={busy} onClick={() => void start(failed.map((f) => f.choice))}>
              {busy ? m.starting : m.retry}
            </Button>
            <Link to={`/parent/children/${child.id}`} className={buttonClasses("ghost", "lg")}>
              {m.continue}
            </Link>
          </>
        ) : picked.length > 0 ? (
          <>
            <Button
              size="lg"
              className="max-w-full [overflow-wrap:anywhere]"
              disabled={busy || catalog.isLoading}
              onClick={() => void start(picked.filter((c) => !started.includes(c.id)))}
            >
              {busy ? m.starting : m.start(child.displayName)}
            </Button>
            <Button variant="ghost" size="lg" disabled={busy} onClick={onBack}>
              {m.back}
            </Button>
          </>
        ) : (
          <>
            <Link to={`/parent/children/${child.id}`} className={buttonClasses("solid", "lg")}>
              {m.finish}
            </Link>
            <Button variant="ghost" size="lg" onClick={onBack}>
              {m.back}
            </Button>
          </>
        )}
      </div>
    </div>
  );
}
