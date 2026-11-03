import { Link } from "react-router-dom";
import { CheckCircle2, Circle } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { studioApi } from "@/lib/api/studio";
import type { ClassOverview } from "@/lib/api/schoolAccess";
import { useMessages } from "@/i18n/catalog";
import { gettingStartedMessages } from "./gettingStarted.messages";

interface Step {
  key: string;
  done: boolean;
  title: string;
  lead: string;
  to?: string;
  action?: string;
}

/**
 * A teacher's first steps, read from what is true now - never a stored checklist: a school, a class, a curriculum for
 * it, children in it, and an activity of their own. Shown until every step is done, each with where to do it; a step
 * someone else does (the principal assigning a class) says so instead of linking. Nothing here changes anything.
 */
export function GettingStarted({ classes }: { classes: ClassOverview[] | undefined }) {
  const m = useMessages(gettingStartedMessages);
  const schools = useAsync(() => schoolApi.myMemberships(), []);
  const lessons = useAsync(() => studioApi.ownLessonsTotal(), []);
  if (!classes || !schools.data || lessons.data === null || lessons.data === undefined) return null;

  const active = classes.filter((c) => !c.archived);
  const first = active[0];
  const withCurriculum = active.find((c) => c.curricula.length > 0);
  const steps: Step[] = [
    { key: "school", done: schools.data.length > 0, title: m.school, lead: m.schoolLead },
    { key: "class", done: active.length > 0, title: m.klass, lead: m.classLead },
    {
      key: "curriculum",
      done: !!withCurriculum,
      title: m.curriculum,
      lead: m.curriculumLead,
      to: first ? `/teacher/classes/${first.classId}` : undefined,
      action: m.openClass,
    },
    {
      key: "children",
      done: active.some((c) => c.children > 0),
      title: m.children,
      lead: m.childrenLead,
      to: first ? `/teacher/classes/${first.classId}` : undefined,
      action: m.openClass,
    },
    { key: "content", done: lessons.data > 0, title: m.content, lead: m.contentLead, to: "/teacher/studio", action: m.openStudio },
  ];
  const done = steps.filter((s) => s.done).length;
  if (done === steps.length) return null;
  // The next step to take: the first not done.
  const next = steps.find((s) => !s.done)!;

  return (
    <section aria-labelledby="getting-started" className="flex flex-col gap-3 rounded-[18px] bg-white p-5 shadow-card sm:p-6">
      <div>
        <h2 id="getting-started" className="font-display text-xl font-semibold text-tonle-950">
          {m.title}
        </h2>
        <p className="mt-1 text-sm text-ink-soft">{m.progress(done, steps.length)}</p>
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
                <span className="sr-only"> - {step.done ? m.doneLabel : m.todoLabel}</span>
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
