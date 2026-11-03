import { Link } from "react-router-dom";
import { CheckCircle2, Circle } from "lucide-react";

export interface FirstStep {
  key: string;
  done: boolean;
  title: string;
  lead: string;
  /** Where to do it; a step someone else takes has none and says so in its lead. */
  to?: string;
  action?: string;
}

/**
 * Someone's first steps in a workspace (a teacher's, a principal's), read from what is true now - never a stored
 * checklist. Shown until every step is done; the first one not done is the current step, with where to take it.
 */
export function FirstSteps({
  headingId,
  title,
  progress,
  doneLabel,
  todoLabel,
  steps,
}: {
  headingId: string;
  title: string;
  progress: (done: number, total: number) => string;
  doneLabel: string;
  todoLabel: string;
  steps: FirstStep[];
}) {
  const done = steps.filter((s) => s.done).length;
  if (done === steps.length) return null;
  const next = steps.find((s) => !s.done)!;

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3 rounded-[18px] bg-white p-5 shadow-card sm:p-6">
      <div>
        <h2 id={headingId} className="font-display text-xl font-semibold text-tonle-950">
          {title}
        </h2>
        <p className="mt-1 text-sm text-ink-soft">{progress(done, steps.length)}</p>
      </div>
      <ol className="flex flex-col gap-2">
        {steps.map((step) => (
          <li
            key={step.key}
            aria-current={step === next ? "step" : undefined}
            className={
              step === next
                ? "flex gap-3 rounded-[14px] bg-tonle-50/70 px-4 py-3 ring-1 ring-tonle-100"
                : "flex gap-3 px-4 py-2"
            }
          >
            {step.done ? (
              <CheckCircle2 size={20} className="mt-0.5 shrink-0 text-palm-deep" aria-hidden />
            ) : (
              <Circle size={20} className="mt-0.5 shrink-0 text-ink-faint" aria-hidden />
            )}
            <span className="flex min-w-0 flex-1 flex-col gap-1">
              <span className={step.done ? "text-[0.9375rem] text-ink-soft" : "text-[0.9375rem] font-semibold text-ink"}>
                {step.title}
                <span className="sr-only"> - {step.done ? doneLabel : todoLabel}</span>
              </span>
              {!step.done && <span className="text-sm leading-relaxed text-ink-soft">{step.lead}</span>}
              {!step.done && step === next && step.to && (
                <Link
                  to={step.to}
                  className="mt-1 inline-flex min-h-11 w-fit items-center rounded-pill bg-tonle-800 px-4 text-sm font-semibold text-white hover:bg-tonle-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tonle-600 focus-visible:ring-offset-2"
                >
                  {step.action}
                </Link>
              )}
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}
