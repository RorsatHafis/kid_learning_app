import { useEffect, useMemo, useRef } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { Check, Play } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import type { EnrollmentOverview, Journey, JourneyStep } from "@/lib/api/types";
import { BuddySays, LearningBuddy } from "@/features/buddy/LearningBuddy";
import { childReason, stepTag } from "@/features/learning/explain";
import { BlueHour } from "@/features/world/BlueHour";
import { PathOfBooks, khmerNumber } from "@/features/world/PathOfBooks";
import { cn } from "@/lib/utils/cn";
import { ChildSpaceLayout } from "./ChildSpaceLayout";

/**
 * The child's home: the blue-hour world with their journey laid across the river as books.
 * Finished books are lit, the current one glows with the firefly above it, and one warm
 * "Play" lantern starts it (directive §12.1–12.2). The journey is exactly the server's
 * learning path; the child can only start the current step — the platform decides what
 * comes next. The scene is decorative; the list beside it is the readable journey.
 */
export function ChildHome() {
  const { childId = "" } = useParams<{ childId: string }>();
  const [params, setParams] = useSearchParams();
  const child = useAsync(() => childrenApi.get(childId), [childId]);
  const enrollments = useAsync(() => learningApi.enrollments(childId), [childId]);

  const selected: EnrollmentOverview | null = useMemo(() => {
    const list = enrollments.data ?? [];
    return list.find((e) => e.enrollmentId === params.get("path")) ?? list.find((e) => e.nextStep !== null) ?? list[0] ?? null;
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
        <div className="flex flex-col items-center gap-6 px-6 pt-24 text-center">
          <BuddySays mood="happy" size={130} tone="night">
            Hi {name}! A grown-up needs to choose what you&apos;ll learn first.
          </BuddySays>
        </div>
      </ChildSpaceLayout>
    );
  }

  const paths = enrollments.data ?? [];
  return (
    <ChildSpaceLayout childId={childId} childName={name} backdrop={false}>
      <JourneyStage
        key={selected.enrollmentId}
        childId={childId}
        name={name}
        overview={selected}
        switcher={
          paths.length > 1 ? (
            <nav aria-label="What to learn" className="flex flex-wrap gap-2">
              {paths.map((enrollment) => {
                const active = enrollment.enrollmentId === selected.enrollmentId;
                return (
                  <button
                    key={enrollment.enrollmentId}
                    type="button"
                    aria-current={active ? "true" : undefined}
                    onClick={() => setParams({ path: enrollment.enrollmentId }, { replace: true })}
                    className={cn(
                      "min-h-11 rounded-pill px-5 text-sm font-semibold ring-1 transition-colors duration-150 ease-out",
                      active ? "bg-white text-tonle-900 ring-white" : "bg-tonle-950/50 text-tonle-100 ring-white/20 backdrop-blur-md hover:bg-tonle-950/70",
                    )}
                  >
                    {enrollment.curriculum.subjectName ?? enrollment.curriculum.name}
                  </button>
                );
              })}
            </nav>
          ) : null
        }
      />
    </ChildSpaceLayout>
  );
}

function JourneyStage({
  childId,
  name,
  overview,
  switcher,
}: {
  childId: string;
  name?: string;
  overview: EnrollmentOverview;
  switcher: React.ReactNode;
}) {
  const journey = useAsync(() => learningApi.journey(overview.enrollmentId), [overview.enrollmentId]);
  const playHref = `/learn/${childId}/play/${overview.enrollmentId}`;
  const next = overview.nextStep;

  return (
    <>
      <section aria-labelledby="greeting" className="relative -mt-[76px] sm:-mt-[88px] lg:flex lg:h-[100dvh] lg:max-h-[980px] lg:min-h-[680px] lg:flex-col">
        {/* The world. On a phone it is a tall window with the whole path in it; on a large
            screen it fills the stage and the words sit on it. */}
        <div aria-hidden className="relative h-[64svh] min-h-[430px] overflow-hidden lg:absolute lg:inset-0 lg:h-auto lg:min-h-0">
          <BlueHour crop="bottom">
            {journey.data && <PathOfBooks steps={journey.data.steps} currentId={journey.data.nextStepId} />}
          </BlueHour>
          {/* scrims behind the words only, so the lit path keeps its light */}
          <div className="absolute inset-0 hidden bg-[radial-gradient(90%_70%_at_0%_100%,rgba(10,21,51,0.92)_0%,rgba(10,21,51,0.55)_45%,rgba(10,21,51,0)_75%)] lg:block" />
          <div className="absolute inset-x-0 bottom-0 h-14 bg-gradient-to-t from-tonle-950 to-transparent lg:hidden" />
          <div className="absolute inset-x-0 top-0 h-32 bg-gradient-to-b from-tonle-950/50 to-transparent" />
        </div>

        <div className="relative mx-auto -mt-2 grid w-full max-w-6xl gap-8 px-5 pb-8 sm:px-8 lg:mt-auto lg:grid-cols-[minmax(0,1fr)_380px] lg:items-end lg:pb-14">
          <div className="flex max-w-xl flex-col gap-5">
            {switcher}
            <h1 id="greeting" className="font-display text-display font-bold tracking-[-0.02em] text-white" style={{ fontVariationSettings: '"opsz" 72' }}>
              Hi, {name}!
            </h1>
            <p className="max-w-md text-xl font-medium leading-snug text-tonle-100">
              {next ? childReason(next) : "You lit every book on this path. Amazing work!"}
            </p>
            {next && (
              <div className="flex flex-wrap items-center gap-x-5 gap-y-3">
                <Link
                  to={playHref}
                  className="group relative inline-flex min-h-[4.25rem] items-center gap-3 rounded-pill bg-light pl-3 pr-9 text-2xl font-bold text-tonle-950 shadow-glow transition-transform duration-200 ease-out hover:-translate-y-0.5 active:scale-[0.97] motion-safe:animate-pulse-ring"
                >
                  <span className="flex h-12 w-12 items-center justify-center rounded-full bg-tonle-950 text-light">
                    <Play size={20} fill="currentColor" aria-hidden className="translate-x-px" />
                  </span>
                  Play
                </Link>
                <span className="text-sm font-medium text-tonle-200">{next.title}</span>
              </div>
            )}
            <BooksLit done={overview.completedSteps} total={overview.totalSteps} />
          </div>

          <section aria-labelledby="journey-title" className="hidden rounded-[26px] bg-tonle-950/60 p-5 ring-1 ring-white/10 backdrop-blur-md lg:block">
            <JourneyList journey={journey} playHref={playHref} curriculumName={overview.curriculum.name} />
          </section>
        </div>
      </section>

      <section aria-labelledby="journey-title-m" className="mx-auto max-w-2xl px-5 pb-16 lg:hidden">
        <JourneyList journey={journey} playHref={playHref} curriculumName={overview.curriculum.name} mobile />
      </section>
    </>
  );
}

/** Progress as the books themselves: lit ones and the ones still waiting. */
function BooksLit({ done, total }: { done: number; total: number }) {
  return (
    <div className="flex items-center gap-3" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={done} aria-label="Books lit on this path">
      <div className="flex flex-wrap gap-1.5" aria-hidden>
        {Array.from({ length: total }, (_, i) => (
          <span
            key={i}
            className={cn("h-2.5 w-6 rounded-[3px]", i < done ? "bg-light shadow-[0_0_12px_rgba(255,210,122,0.6)]" : "bg-white/15")}
          />
        ))}
      </div>
      <span className="text-sm font-semibold text-tonle-100">
        {done} of {total} lit
      </span>
    </div>
  );
}

function JourneyList({
  journey,
  playHref,
  curriculumName,
  mobile,
}: {
  journey: ReturnType<typeof useAsync<Journey>>;
  playHref: string;
  curriculumName: string;
  mobile?: boolean;
}) {
  const currentRef = useRef<HTMLLIElement | null>(null);
  useEffect(() => {
    if (!mobile) currentRef.current?.scrollIntoView({ block: "nearest" });
  }, [journey.data, mobile]);

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 id={mobile ? "journey-title-m" : "journey-title"} className="font-display text-xl font-bold text-white">
          My path
        </h2>
        <p className="text-sm text-tonle-200">{curriculumName}</p>
      </div>
      {journey.isLoading && <p className="py-6 text-center font-medium text-tonle-200">Laying out your books…</p>}
      {journey.error && <Trouble onRetry={journey.refetch} compact />}
      {journey.data && journey.data.steps.length === 0 && <p className="py-4 text-tonle-200">Your path is being prepared.</p>}
      {journey.data && journey.data.steps.length > 0 && (
        <ol className={cn("flex flex-col gap-1", !mobile && "max-h-[340px] overflow-y-auto pr-1")}>
          {journey.data.steps.map((step, index) => {
            const isCurrent = step.learningPathItemId === journey.data!.nextStepId;
            return (
              <li key={step.learningPathItemId} ref={isCurrent ? currentRef : undefined} aria-current={isCurrent ? "step" : undefined}>
                <StepRow step={step} index={index} isCurrent={isCurrent} playHref={playHref} />
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}

function StepRow({ step, index, isCurrent, playHref }: { step: JourneyStep; index: number; isCurrent: boolean; playHref: string }) {
  const done = step.status === "COMPLETED";
  const tag = stepTag(step);
  const status = done ? "finished" : isCurrent ? "up next" : "coming later";
  const badge = (
    <span
      className={cn(
        "flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] text-sm font-bold",
        done && "bg-light-soft text-light-deep",
        isCurrent && "bg-light text-tonle-950 shadow-glow",
        !done && !isCurrent && "bg-white/10 text-tonle-200",
      )}
      aria-hidden
    >
      {done ? <Check size={18} strokeWidth={3} /> : isCurrent ? <Play size={16} fill="currentColor" /> : <span lang="km">{khmerNumber(index + 1)}</span>}
    </span>
  );
  const body = (
    <>
      {badge}
      <span className="flex min-w-0 flex-col">
        <span className={cn("truncate text-[0.95rem] font-semibold", done ? "text-tonle-100" : isCurrent ? "text-white" : "text-tonle-200")}>{step.title}</span>
        <span className="text-xs text-tonle-300">
          {isCurrent ? "Up next" : done ? "Lit" : "Waiting"}
          {tag ? ` · ${tag.label}` : ""}
        </span>
      </span>
    </>
  );
  return isCurrent ? (
    <Link
      to={playHref}
      aria-label={`Play ${step.title}, ${status}`}
      className="flex items-center gap-3 rounded-[14px] bg-white/10 p-2 ring-1 ring-light/40 transition-colors duration-150 ease-out hover:bg-white/15"
    >
      {body}
    </Link>
  ) : (
    <div className="flex items-center gap-3 rounded-[14px] p-2" role="img" aria-label={`${step.title}, ${status}`}>
      {body}
    </div>
  );
}

export function Waiting({ line }: { line: string }) {
  return (
    <div role="status" className="flex flex-col items-center gap-4 px-6 pt-28 text-center motion-safe:animate-fade-in-late">
      <LearningBuddy mood="thinking" size={132} />
      <p className="text-lg font-semibold text-tonle-100">{line}</p>
    </div>
  );
}

export function Trouble({ onRetry, compact, line }: { onRetry: () => void; compact?: boolean; line?: string }) {
  return (
    <div role="alert" className={cn("flex flex-col items-center gap-4 px-6 text-center", compact ? "py-6" : "pt-28")}>
      <LearningBuddy mood="encourage" size={compact ? 88 : 124} />
      <p className="max-w-sm text-lg font-semibold text-white">{line ?? "Oops — we couldn't load that. Let's try again!"}</p>
      <button
        type="button"
        onClick={onRetry}
        className="min-h-14 rounded-pill bg-light px-9 text-lg font-bold text-tonle-950 shadow-[0_5px_0_#B86A1C,0_12px_40px_rgba(243,168,59,0.45)] transition-[transform,box-shadow,background-color] duration-150 ease-out hover:bg-light-glow active:translate-y-1 active:shadow-[0_1px_0_#B86A1C]"
      >
        Try again
      </button>
    </div>
  );
}
