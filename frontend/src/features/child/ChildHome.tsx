import { useEffect, useMemo, useRef } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { BookOpen, Check, Library, Play } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import type { Chapter, EnrollmentOverview, Journey, JourneyStep } from "@/lib/api/types";
import { BuddySays, LearningBuddy } from "@/features/buddy/LearningBuddy";
import { childReason, stepTag } from "@/features/learning/explain";
import { chapterOf, focusChapter, journeyLight } from "@/features/learning/chapters";
import { BlueHour } from "@/features/world/BlueHour";
import { PathOfBooks, khmerNumber } from "@/features/world/PathOfBooks";
import { cn } from "@/lib/utils/cn";
import { ChildSpaceLayout } from "./ChildSpaceLayout";
import { SHELF_SPINES } from "./shelf";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";


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
  // X25: a review that fell due after the last step is placed first, so it is there to play. Never blocks the home.
  const enrollments = useAsync(async () => {
    await learningApi.placeDueReviews(childId).catch(() => null);
    return learningApi.enrollments(childId);
  }, [childId]);

  const selected: EnrollmentOverview | null = useMemo(() => {
    const list = enrollments.data ?? [];
    return list.find((e) => e.enrollmentId === params.get("path")) ?? list.find((e) => e.nextStep !== null) ?? list[0] ?? null;
  }, [enrollments.data, params]);

  const name = child.data?.displayName;
  useDocumentTitle(name ? `${name}'s path` : null);

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
            <nav aria-label="My subjects" className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1">
              {paths.map((enrollment, i) => {
                const active = enrollment.enrollmentId === selected.enrollmentId;
                const where = chapterOf(enrollment.currentChapter, enrollment.chapterCount);
                return (
                  <button
                    key={enrollment.enrollmentId}
                    type="button"
                    aria-current={active ? "true" : undefined}
                    onClick={() => setParams({ path: enrollment.enrollmentId }, { replace: true })}
                    className={cn(
                      "group flex min-h-14 shrink-0 items-center gap-3 rounded-[16px] py-2 pl-2 pr-4 text-left ring-1 transition-colors duration-150 ease-out",
                      active ? "bg-white text-tonle-950 ring-white" : "bg-tonle-950/55 text-white ring-white/20 backdrop-blur-md hover:bg-tonle-950/75",
                    )}
                  >
                    <span
                      aria-hidden
                      className={cn(
                        "h-10 w-7 shrink-0 rounded-[5px] shadow-[inset_5px_0_0_rgba(0,0,0,0.18)]",
                        SHELF_SPINES[i % SHELF_SPINES.length],
                      )}
                    />
                    <span className="flex flex-col">
                      <span className="text-[0.95rem] font-bold leading-tight">
                        {enrollment.curriculum.subjectName ?? enrollment.curriculum.name}
                      </span>
                      <span className={cn("text-xs font-medium", active ? "text-ink-soft" : "text-tonle-200")}>
                        {enrollment.nextStep === null ? "All lit" : (where ?? `${enrollment.completedSteps} of ${enrollment.totalSteps} lit`)}
                      </span>
                    </span>
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
  const focus = journey.data ? focusChapter(journey.data) : null;
  const playHref = `/learn/${childId}/play/${overview.enrollmentId}`;
  const next = overview.nextStep;

  return (
    <>
      <section aria-labelledby="greeting" className="relative -mt-[76px] sm:-mt-[88px] lg:flex lg:h-[100dvh] lg:max-h-[980px] lg:min-h-[680px] lg:flex-col">
        {/* The world. On a phone it is a tall window with the whole path in it; on a large
            screen it fills the stage and the words sit on it. */}
        <div aria-hidden className="relative h-[64svh] min-h-[430px] overflow-hidden lg:absolute lg:inset-0 lg:h-auto lg:min-h-0">
          <BlueHour crop="bottom" firstLight={journeyLight(overview)}>
            {focus && <PathOfBooks steps={focus.steps.map(({ step }) => step)} currentId={journey.data!.nextStepId} />}
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
            {overview.currentChapter && (
              <p className="-mb-2 flex items-center gap-2 text-sm font-semibold text-light-glow">
                <BookOpen size={16} aria-hidden />
                <span>
                  {[chapterOf(overview.currentChapter, overview.chapterCount), overview.currentChapter.title].filter(Boolean).join(" · ")}
                </span>
              </p>
            )}
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
            {!next && (
              <Link
                to={`/learn/${childId}/shelf`}
                className="inline-flex min-h-[3.75rem] w-fit items-center gap-3 rounded-pill bg-light pl-2.5 pr-7 text-xl font-bold text-tonle-950 shadow-glow transition-transform duration-200 ease-out hover:-translate-y-0.5 active:scale-[0.97]"
              >
                <span className="flex h-11 w-11 items-center justify-center rounded-full bg-tonle-950 text-light">
                  <Library size={20} aria-hidden />
                </span>
                See my shelf
              </Link>
            )}
            {focus?.chapter ? (
              <BooksLit
                done={focus.steps.filter(({ step }) => step.status === "COMPLETED").length}
                total={focus.steps.length}
                label={`Books lit in chapter ${focus.chapter.position}`}
                suffix="in this chapter"
              />
            ) : (
              <BooksLit done={overview.completedSteps} total={overview.totalSteps} label="Books lit on this path" />
            )}
            {next && (
              <Link
                to={`/learn/${childId}/shelf`}
                className="-mt-1 inline-flex min-h-11 w-fit items-center gap-2 rounded-pill bg-tonle-950/55 px-4 text-sm font-semibold text-white ring-1 ring-white/15 backdrop-blur-md transition-colors duration-150 ease-out hover:bg-tonle-950/75"
              >
                <Library size={16} aria-hidden /> My shelf
              </Link>
            )}
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
function BooksLit({ done, total, label, suffix }: { done: number; total: number; label: string; suffix?: string }) {
  return (
    <div className="flex items-center gap-3" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={done} aria-label={label}>
      <div className="flex flex-wrap gap-1.5" aria-hidden>
        {Array.from({ length: total }, (_, i) => (
          <span
            key={i}
            className={cn("h-2.5 w-6 rounded-[3px]", i < done ? "bg-light shadow-[0_0_12px_rgba(255,210,122,0.6)]" : "bg-white/15")}
          />
        ))}
      </div>
      <span className="text-sm font-semibold text-tonle-100">
        {done} of {total} lit{suffix ? ` ${suffix}` : ""}
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
        <MyPath journey={journey.data} playHref={playHref} currentRef={currentRef} scroll={!mobile} />
      )}
    </div>
  );
}

/**
 * The journey as chapters: finished ones as a single lit row, the one the child is in opened
 * with its books, the ones ahead as rows that say how many books wait there.
 */
function MyPath({
  journey,
  playHref,
  currentRef,
  scroll,
}: {
  journey: Journey;
  playHref: string;
  currentRef: React.MutableRefObject<HTMLLIElement | null>;
  scroll: boolean;
}) {
  const focus = focusChapter(journey);
  const steps = (
    <ol className="flex flex-col gap-1">
      {focus.steps.map(({ step }, i) => {
        const isCurrent = step.learningPathItemId === journey.nextStepId;
        return (
          <li key={step.learningPathItemId} ref={isCurrent ? currentRef : undefined} aria-current={isCurrent ? "step" : undefined}>
            <StepRow step={step} index={i} isCurrent={isCurrent} playHref={playHref} />
          </li>
        );
      })}
    </ol>
  );
  if (!focus.chapter) {
    return <div className={cn(scroll && "max-h-[340px] overflow-y-auto pr-1")}>{steps}</div>;
  }
  return (
    <ol className={cn("flex flex-col gap-1.5", scroll && "max-h-[380px] overflow-y-auto pr-1")} aria-label="Chapters">
      {journey.chapters.map((chapter) =>
        chapter.id === focus.chapter!.id ? (
          <li key={chapter.id} className="rounded-[18px] bg-white/[0.06] p-2 ring-1 ring-white/10">
            <p className="flex items-baseline justify-between gap-3 px-2 pb-2 pt-1">
              <span className="text-sm font-bold text-white">
                Chapter {chapter.position} · {chapter.title}
              </span>
              <span className="shrink-0 text-xs font-semibold text-light-glow">You are here</span>
            </p>
            {steps}
          </li>
        ) : (
          <li key={chapter.id}>
            <ChapterRow chapter={chapter} />
          </li>
        ),
      )}
    </ol>
  );
}

const CHAPTER_NOTE: Record<Chapter["state"], (c: Chapter) => string> = {
  COMPLETE: (c) => (c.totalSteps === 1 ? "Lit" : `All ${c.totalSteps} lit`),
  CURRENT: (c) => `${c.completedSteps} of ${c.totalSteps} lit`,
  STARTED: (c) => `${c.completedSteps} of ${c.totalSteps} lit`,
  UPCOMING: (c) => (c.totalSteps === 1 ? "1 book waiting" : `${c.totalSteps} books waiting`),
  NOT_READY: () => "Being made",
};

function ChapterRow({ chapter }: { chapter: Chapter }) {
  const done = chapter.state === "COMPLETE";
  const later = chapter.state === "UPCOMING" || chapter.state === "NOT_READY";
  return (
    <div className="flex items-center gap-3 rounded-[14px] px-2 py-1.5">
      <span
        aria-hidden
        className={cn(
          "flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px]",
          done ? "bg-light-soft text-light-deep" : later ? "bg-white/[0.06] text-tonle-300" : "bg-white/10 text-light-glow",
        )}
      >
        {done ? <Check size={17} strokeWidth={3} /> : <BookOpen size={16} />}
      </span>
      <span className="flex min-w-0 flex-col">
        <span className={cn("truncate text-sm font-semibold", later ? "text-tonle-200" : "text-white")}>
          Chapter {chapter.position} · {chapter.title}
        </span>
        <span className="text-xs text-tonle-300">{CHAPTER_NOTE[chapter.state](chapter)}</span>
      </span>
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
