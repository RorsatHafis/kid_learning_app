import { useEffect, useMemo, useRef } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { Check, Play, Star } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import type { EnrollmentOverview, Journey, JourneyStep } from "@/lib/api/types";
import { BuddySays, LearningBuddy } from "@/features/buddy/LearningBuddy";
import { childReason, stepTag } from "@/features/learning/explain";
import { cn } from "@/lib/utils/cn";
import { ChildSpaceLayout } from "./ChildSpaceLayout";

/**
 * The child's home: who they are, where they are on their journey, and the one
 * thing to do next (directive §12.1–12.2). The journey shows completed, current
 * and upcoming steps exactly as the server's learning path has them; the child
 * can only start the current step — the platform decides what comes next.
 */
export function ChildHome() {
  const { childId = "" } = useParams<{ childId: string }>();
  const [params, setParams] = useSearchParams();
  const child = useAsync(() => childrenApi.get(childId), [childId]);
  const enrollments = useAsync(() => learningApi.enrollments(childId), [childId]);

  const selected: EnrollmentOverview | null = useMemo(() => {
    const list = enrollments.data ?? [];
    return (
      list.find((e) => e.enrollmentId === params.get("path")) ??
      list.find((e) => e.nextStep !== null) ??
      list[0] ??
      null
    );
  }, [enrollments.data, params]);

  const name = child.data?.displayName;

  if (child.isLoading || enrollments.isLoading) {
    return (
      <ChildSpaceLayout childId={childId} childName={name}>
        <Waiting line="Getting your world ready…" />
      </ChildSpaceLayout>
    );
  }

  if (child.error || enrollments.error) {
    return (
      <ChildSpaceLayout childId={childId} childName={name}>
        <Trouble
          onRetry={() => {
            child.refetch();
            enrollments.refetch();
          }}
        />
      </ChildSpaceLayout>
    );
  }

  if (!selected) {
    return (
      <ChildSpaceLayout childId={childId} childName={name}>
        <div className="flex flex-col items-center gap-6 pt-10 text-center">
          <BuddySays mood="happy" size={120}>
            Hi {name}! A grown-up needs to choose what you'll learn first.
          </BuddySays>
        </div>
      </ChildSpaceLayout>
    );
  }

  return (
    <ChildSpaceLayout childId={childId} childName={name}>
      {(enrollments.data?.length ?? 0) > 1 && (
        <nav aria-label="What to learn" className="mb-6 flex flex-wrap gap-2">
          {enrollments.data!.map((enrollment) => (
            <button
              key={enrollment.enrollmentId}
              type="button"
              aria-current={enrollment.enrollmentId === selected.enrollmentId ? "true" : undefined}
              onClick={() => setParams({ path: enrollment.enrollmentId }, { replace: true })}
              className={cn(
                "min-h-11 rounded-pill px-5 py-2 text-sm font-bold shadow-tight transition-colors",
                enrollment.enrollmentId === selected.enrollmentId
                  ? "bg-kid-skyDeep text-white"
                  : "bg-white/80 text-ink hover:bg-white",
              )}
            >
              {enrollment.curriculum.subjectName ?? enrollment.curriculum.name}
            </button>
          ))}
        </nav>
      )}
      <JourneyView key={selected.enrollmentId} childId={childId} name={name} overview={selected} />
    </ChildSpaceLayout>
  );
}

function JourneyView({ childId, name, overview }: { childId: string; name?: string; overview: EnrollmentOverview }) {
  const journey = useAsync(() => learningApi.journey(overview.enrollmentId), [overview.enrollmentId]);
  const playHref = `/learn/${childId}/play/${overview.enrollmentId}`;
  const next = overview.nextStep;

  return (
    <div className="flex flex-col gap-8">
      <section
        aria-labelledby="greeting"
        className="relative flex flex-col gap-6 overflow-hidden rounded-[32px] bg-white/90 p-6 shadow-soft sm:flex-row sm:items-center sm:p-8"
      >
        <LearningBuddy mood={next ? "happy" : "cheer"} size={132} className="self-center" />
        <div className="flex flex-1 flex-col gap-3">
          <h1 id="greeting" className="text-3xl font-extrabold sm:text-4xl">
            Hi, {name}!
          </h1>
          <p className="text-lg font-semibold text-muted">
            {next ? childReason(next) : "You finished everything here for now. Amazing work!"}
          </p>
          <Progress done={overview.completedSteps} total={overview.totalSteps} />
        </div>
        {next && (
          <Link
            to={playHref}
            className="flex min-h-16 items-center justify-center gap-3 rounded-pill bg-kid-skyDeep px-10 py-5 text-xl font-extrabold text-white shadow-soft transition-transform hover:-translate-y-0.5 focus-visible:-translate-y-0.5 motion-safe:animate-pulse-ring"
          >
            <Play size={26} fill="currentColor" aria-hidden /> Play
          </Link>
        )}
      </section>

      <section aria-labelledby="journey-title" className="rounded-[32px] bg-white/60 p-5 shadow-tight sm:p-8">
        <h2 id="journey-title" className="mb-1 text-xl font-extrabold">
          My journey
        </h2>
        <p className="mb-6 text-sm font-medium text-muted">{overview.curriculum.name}</p>
        {journey.isLoading && <p className="py-8 text-center font-semibold text-muted">Drawing your map…</p>}
        {journey.error && <Trouble onRetry={journey.refetch} compact />}
        {journey.data && <JourneyMap journey={journey.data} playHref={playHref} />}
      </section>
    </div>
  );
}

function Progress({ done, total }: { done: number; total: number }) {
  const ratio = total > 0 ? done / total : 0;
  return (
    <div className="flex items-center gap-3">
      <div
        className="h-4 flex-1 overflow-hidden rounded-pill bg-kid-mint"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={total}
        aria-valuenow={done}
        aria-label="Steps finished"
      >
        <div
          className="h-full origin-left animate-fill-bar rounded-pill bg-kid-mintDeep"
          style={{ width: `${Math.max(ratio * 100, done > 0 ? 6 : 0)}%` }}
        />
      </div>
      <span className="whitespace-nowrap text-sm font-bold text-kid-mintDeep">
        {done} of {total} done
      </span>
    </div>
  );
}

const OFFSETS = ["sm:translate-x-0", "sm:translate-x-24", "sm:translate-x-12", "sm:-translate-x-12", "sm:-translate-x-24"];

function JourneyMap({ journey, playHref }: { journey: Journey; playHref: string }) {
  const currentRef = useRef<HTMLLIElement | null>(null);
  useEffect(() => {
    const reduce = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    // "nearest": only scrolls when the current step is out of view, so a short
    // journey never pushes the greeting and Play button off screen.
    currentRef.current?.scrollIntoView({ block: "nearest", behavior: reduce ? "auto" : "smooth" });
  }, []);

  if (journey.steps.length === 0) {
    return <p className="py-6 text-center font-semibold text-muted">Your journey is being prepared.</p>;
  }

  return (
    <ol className="relative mx-auto flex max-w-md flex-col items-center gap-3">
      <span aria-hidden className="absolute bottom-6 top-6 w-1 rounded-full border-l-4 border-dashed border-kid-lavender" />
      {journey.steps.map((step, index) => {
        const isCurrent = step.learningPathItemId === journey.nextStepId;
        return (
          <li
            key={step.learningPathItemId}
            ref={isCurrent ? currentRef : undefined}
            className={cn("relative flex w-full flex-col items-center transition-transform", OFFSETS[index % OFFSETS.length])}
            aria-current={isCurrent ? "step" : undefined}
          >
            <JourneyNode step={step} index={index} isCurrent={isCurrent} playHref={playHref} />
          </li>
        );
      })}
      <li className="relative flex flex-col items-center gap-1 pt-2" aria-hidden>
        <span className="flex h-14 w-14 items-center justify-center rounded-full bg-kid-sun shadow-tight">
          <Star className="text-kid-sunDeep" size={26} fill="currentColor" />
        </span>
      </li>
    </ol>
  );
}

function JourneyNode({
  step,
  index,
  isCurrent,
  playHref,
}: {
  step: JourneyStep;
  index: number;
  isCurrent: boolean;
  playHref: string;
}) {
  const tag = stepTag(step);
  const done = step.status === "COMPLETED";
  const tagClass = tag
    ? { sun: "bg-kid-sun text-kid-sunDeep", lavender: "bg-kid-lavender text-kid-lavenderDeep", peach: "bg-kid-peach text-kid-peachDeep" }[tag.tone]
    : "";
  const status = done ? "finished" : isCurrent ? "up next" : "coming later";

  const circle = (
    <span
      className={cn(
        "relative flex items-center justify-center rounded-full border-4 border-white font-extrabold shadow-soft",
        done && "h-16 w-16 bg-kid-mintDeep text-white",
        isCurrent && "h-24 w-24 bg-kid-skyDeep text-white ring-8 ring-kid-sky",
        !done && !isCurrent && "h-14 w-14 bg-white text-muted",
      )}
    >
      {done ? <Check size={30} strokeWidth={3} aria-hidden /> : isCurrent ? <Play size={34} fill="currentColor" aria-hidden /> : index + 1}
    </span>
  );

  return (
    <div className="flex flex-col items-center gap-2 text-center">
      {isCurrent ? (
        <Link to={playHref} aria-label={`Play ${step.title}, ${status}`} className="rounded-full">
          {circle}
        </Link>
      ) : (
        <span role="img" aria-label={`${step.title}, ${status}`}>
          {circle}
        </span>
      )}
      <span
        aria-hidden
        className={cn(
          "max-w-[14rem] rounded-2xl px-3 py-1 text-sm font-bold",
          isCurrent ? "bg-white text-ink shadow-tight" : done ? "bg-kid-paper/90 text-kid-mintDeep" : "bg-kid-paper/90 text-muted",
        )}
      >
        {step.title}
      </span>
      {tag && <span className={cn("rounded-pill px-3 py-0.5 text-xs font-bold", tagClass)}>{tag.label}</span>}
    </div>
  );
}

export function Waiting({ line }: { line: string }) {
  return (
    <div role="status" className="flex flex-col items-center gap-4 pt-16 text-center">
      <LearningBuddy mood="thinking" size={128} />
      <p className="text-lg font-bold text-muted">{line}</p>
    </div>
  );
}

export function Trouble({ onRetry, compact, line }: { onRetry: () => void; compact?: boolean; line?: string }) {
  return (
    <div role="alert" className={cn("flex flex-col items-center gap-4 text-center", compact ? "py-6" : "pt-16")}>
      <LearningBuddy mood="encourage" size={compact ? 88 : 120} />
      <p className="max-w-sm text-lg font-bold text-ink">{line ?? "Oops — we couldn't load that. Let's try again!"}</p>
      <button
        type="button"
        onClick={onRetry}
        className="min-h-12 rounded-pill bg-kid-skyDeep px-8 py-3 text-base font-extrabold text-white shadow-soft"
      >
        Try again
      </button>
    </div>
  );
}
