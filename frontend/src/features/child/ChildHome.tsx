import { useEffect, useMemo, useRef } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { BookOpen, Check, Library, Play } from "lucide-react";
import { ApiError } from "@/lib/api/ApiError";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import type { Chapter, EnrollmentOverview, Journey, JourneyStep } from "@/lib/api/types";
import { BuddySays, LearningBuddy } from "@/features/buddy/LearningBuddy";
import { childReason, stepTag } from "@/features/learning/explain";
import { chapterOf, focusChapter, journeyLight } from "@/features/learning/chapters";
import { RiverGarden } from "@/features/world/RiverGarden";
import { PathOfBooks, khmerNumber } from "@/features/world/PathOfBooks";
import { cn } from "@/lib/utils/cn";
import { ChildSpaceLayout } from "./ChildSpaceLayout";
import { goButton } from "./chips";
import { SHELF_SPINES } from "./shelf";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";


/**
 * The child's home: River Garden with their journey laid along the village path as books.
 * Finished books are lit, the current one glows with the child's buddy beside it, and one warm
 * "Play" button starts it (directive §12.1–12.2). Lotuses open in the pond as chapters finish. The journey is exactly the server's
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
  const language = child.data?.preferredLanguage;
  const buddy = child.data?.learningBuddy;
  const m = useMessages(childMessages);
  useDocumentTitle(name ? m.home.title(name) : null);

  if (child.isLoading || enrollments.isLoading) {
    return (
      <ChildSpaceLayout childId={childId} childName={name} childLanguage={language} buddy={buddy}>
        <Waiting line={m.home.gettingReady} />
      </ChildSpaceLayout>
    );
  }

  if (child.error instanceof ApiError && child.error.status === 404) {
    // Not this family's child, or one being deleted: trying again won't change that.
    return (
      <ChildSpaceLayout childId={childId} childName={name} childLanguage={language} buddy={buddy}>
        <Trouble line={m.closed} />
      </ChildSpaceLayout>
    );
  }

  if (child.error || enrollments.error) {
    return (
      <ChildSpaceLayout childId={childId} childName={name} childLanguage={language} buddy={buddy}>
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
      <ChildSpaceLayout childId={childId} childName={name} childLanguage={language} buddy={buddy}>
        <div className="flex flex-col items-center gap-6 px-6 pt-24 text-center">
          <BuddySays mood="happy" size={130}>
            {m.home.noPathYet(name ?? "")}
          </BuddySays>
        </div>
      </ChildSpaceLayout>
    );
  }

  const paths = enrollments.data ?? [];
  return (
    <ChildSpaceLayout childId={childId} childName={name} childLanguage={language} buddy={buddy} backdrop={false}>
      <JourneyStage
        key={selected.enrollmentId}
        childId={childId}
        name={name}
        overview={selected}
        switcher={
          paths.length > 1 ? (
            <nav aria-label={m.home.mySubjects} className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1">
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
                      active ? "bg-tonle-900 text-white ring-tonle-900" : "bg-paper text-tonle-950 ring-line hover:bg-tonle-50",
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
                      <span className={cn("text-xs font-medium", active ? "text-tonle-100" : "text-ink-soft")}>
                        {enrollment.nextStep === null ? m.home.allLit : (where ?? m.home.litOf(enrollment.completedSteps, enrollment.totalSteps))}
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
  const m = useMessages(childMessages);
  const focus = journey.data ? focusChapter(journey.data) : null;
  const playHref = `/learn/${childId}/play/${overview.enrollmentId}`;
  const next = overview.nextStep;

  return (
    <>
      <section aria-labelledby="greeting" className="relative -mt-[76px] sm:-mt-[88px] lg:flex lg:h-[100dvh] lg:max-h-[980px] lg:min-h-[700px] lg:flex-col">
        {/* The world. On a phone it is a tall window with the whole path in it; on a large
            screen it fills the stage and the page of words sits on it. */}
        <div aria-hidden className="relative h-[60svh] min-h-[420px] overflow-hidden lg:absolute lg:inset-0 lg:h-auto lg:min-h-0">
          <RiverGarden crop="bottom" bloom={journeyLight(overview)}>
            {focus && <PathOfBooks steps={focus.steps.map(({ step }) => step)} currentId={journey.data!.nextStepId} />}
          </RiverGarden>
          <div className="absolute inset-x-0 bottom-0 h-28 bg-gradient-to-t from-[#E7F1EC] via-[#E7F1EC]/60 to-transparent lg:hidden" />
        </div>

        <div className="relative mx-auto -mt-6 w-full max-w-6xl px-4 pb-8 sm:px-8 lg:mt-auto lg:pb-10">
          <div className="flex max-w-[30rem] flex-col gap-4 rounded-[28px] bg-white/95 p-6 shadow-lift ring-1 ring-tonle-950/5 backdrop-blur-sm sm:p-7">
            {switcher}
            <h1 id="greeting" className="font-display text-[clamp(2.5rem,1.9rem+2.2vw,3.6rem)] font-bold leading-[1.02] text-tonle-950">
              {m.home.hi(name ?? "")}
            </h1>
            {overview.currentChapter && (
              <p className="-mb-2 flex items-center gap-2 text-sm font-semibold text-clay-deep">
                <BookOpen size={16} aria-hidden />
                <span>
                  {[chapterOf(overview.currentChapter, overview.chapterCount), overview.currentChapter.title].filter(Boolean).join(" · ")}
                </span>
              </p>
            )}
            <p className="max-w-md text-lg font-medium leading-snug text-ink-soft">{next ? childReason(next) : m.home.allDone}</p>
            {next && (
              <div className="flex flex-wrap items-center gap-x-5 gap-y-3">
                <Link to={playHref} className={cn(goButton, "min-h-[4.25rem] pl-3 pr-9 text-2xl")}>
                  <span className="flex h-12 w-12 items-center justify-center rounded-full bg-tonle-900 text-light">
                    <Play size={20} fill="currentColor" aria-hidden className="translate-x-px" />
                  </span>
                  {m.home.play}
                </Link>
                <span className="text-sm font-semibold text-ink-soft">{next.title}</span>
              </div>
            )}
            {!next && (
              <Link to={`/learn/${childId}/shelf`} className={cn(goButton, "w-fit pl-2.5 pr-7")}>
                <span className="flex h-11 w-11 items-center justify-center rounded-full bg-tonle-900 text-light">
                  <Library size={20} aria-hidden />
                </span>
                {m.home.seeShelf}
              </Link>
            )}
            {focus?.chapter ? (
              <BooksLit
                done={focus.steps.filter(({ step }) => step.status === "COMPLETED").length}
                total={focus.steps.length}
                label={m.home.litInChapter(focus.chapter.position)}
                suffix={m.home.inThisChapter}
              />
            ) : (
              <BooksLit done={overview.completedSteps} total={overview.totalSteps} label={m.home.litOnPath} />
            )}
            {next && (
              <Link
                to={`/learn/${childId}/shelf`}
                className="-mt-1 inline-flex min-h-11 w-fit items-center gap-2 rounded-pill bg-tonle-50 px-4 text-sm font-semibold text-tonle-800 ring-1 ring-tonle-100 transition-colors duration-150 ease-out hover:bg-tonle-100"
              >
                <Library size={16} aria-hidden /> {m.myShelf}
              </Link>
            )}
          </div>
        </div>
      </section>

      <section aria-labelledby="journey-title-m" className="mx-auto max-w-3xl px-4 pb-16 pt-2 sm:px-8 lg:pt-10">
        <div className="rounded-[26px] bg-white p-5 shadow-paper ring-1 ring-tonle-950/5 sm:p-7">
          <JourneyList journey={journey} playHref={playHref} curriculumName={overview.curriculum.name} mobile />
        </div>
      </section>
    </>
  );
}

/** Progress as the books themselves: lit ones and the ones still waiting. */
function BooksLit({ done, total, label, suffix }: { done: number; total: number; label: string; suffix?: string }) {
  const m = useMessages(childMessages);
  return (
    <div className="flex items-center gap-3" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={done} aria-label={label}>
      <div className="flex flex-wrap gap-1.5" aria-hidden>
        {Array.from({ length: total }, (_, i) => (
          <span
            key={i}
            className={cn("h-2.5 w-6 rounded-[3px]", i < done ? "bg-light" : "bg-tonle-100")}
          />
        ))}
      </div>
      <span className="text-sm font-semibold text-ink-soft">
        {m.home.litOf(done, total)}
        {suffix ? ` ${suffix}` : ""}
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
  const m = useMessages(childMessages);
  useEffect(() => {
    if (!mobile) currentRef.current?.scrollIntoView({ block: "nearest" });
  }, [journey.data, mobile]);

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 id={mobile ? "journey-title-m" : "journey-title"} className="font-display text-xl font-bold text-tonle-950">
          {m.myPath}
        </h2>
        <p className="text-sm text-ink-soft">{curriculumName}</p>
      </div>
      {journey.isLoading && <p className="py-6 text-center font-medium text-ink-soft">{m.home.layingOut}</p>}
      {journey.error && <Trouble onRetry={journey.refetch} compact />}
      {journey.data && journey.data.steps.length === 0 && <p className="py-4 text-ink-soft">{m.home.preparing}</p>}
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
  const m = useMessages(childMessages);
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
    <ol className={cn("flex flex-col gap-1.5", scroll && "max-h-[380px] overflow-y-auto pr-1")} aria-label={m.home.chapters}>
      {journey.chapters.map((chapter) =>
        chapter.id === focus.chapter!.id ? (
          <li key={chapter.id} className="rounded-[18px] bg-paper p-2 ring-1 ring-line">
            <p className="flex items-baseline justify-between gap-3 px-2 pb-2 pt-1">
              <span className="text-sm font-bold text-tonle-950">{m.home.chapter(chapter.position, chapter.title)}</span>
              <span className="shrink-0 text-xs font-semibold text-clay-deep">{m.home.youAreHere}</span>
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

function chapterNote(c: Chapter, m: ChildWords): string {
  switch (c.state) {
    case "COMPLETE":
      return c.totalSteps === 1 ? m.home.lit : m.home.allNLit(c.totalSteps);
    case "CURRENT":
    case "STARTED":
      return m.home.litOf(c.completedSteps, c.totalSteps);
    case "UPCOMING":
      return m.home.booksWaiting(c.totalSteps);
    case "NOT_READY":
      return m.home.beingMade;
  }
}

type ChildWords = typeof childMessages.en;

function ChapterRow({ chapter }: { chapter: Chapter }) {
  const m = useMessages(childMessages);
  const done = chapter.state === "COMPLETE";
  const later = chapter.state === "UPCOMING" || chapter.state === "NOT_READY";
  return (
    <div className="flex items-center gap-3 rounded-[14px] px-2 py-1.5">
      <span
        aria-hidden
        className={cn(
          "flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px]",
          done ? "bg-light-soft text-light-deep" : later ? "bg-paper text-ink-faint" : "bg-tonle-50 text-tonle-700",
        )}
      >
        {done ? <Check size={17} strokeWidth={3} /> : <BookOpen size={16} />}
      </span>
      <span className="flex min-w-0 flex-col">
        <span className={cn("truncate text-sm font-semibold", later ? "text-ink-soft" : "text-tonle-950")}>
          {m.home.chapter(chapter.position, chapter.title)}
        </span>
        <span className="text-xs text-ink-faint">{chapterNote(chapter, m)}</span>
      </span>
    </div>
  );
}

function StepRow({ step, index, isCurrent, playHref }: { step: JourneyStep; index: number; isCurrent: boolean; playHref: string }) {
  const done = step.status === "COMPLETED";
  const tag = stepTag(step);
  const m = useMessages(childMessages).home;
  const status = done ? m.finished : isCurrent ? m.upNextState : m.later;
  const badge = (
    <span
      className={cn(
        "flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] text-sm font-bold",
        done && "bg-light-soft text-light-deep",
        isCurrent && "bg-light text-tonle-950",
        !done && !isCurrent && "bg-paper text-ink-soft ring-1 ring-line",
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
        <span className={cn("truncate text-[0.95rem] font-semibold", isCurrent ? "text-tonle-950" : "text-ink-soft")}>{step.title}</span>
        <span className="text-xs text-ink-faint">
          {isCurrent ? m.upNext : done ? m.lit : m.waiting}
          {tag ? ` · ${tag.label}` : ""}
        </span>
      </span>
    </>
  );
  return isCurrent ? (
    <Link
      to={playHref}
      aria-label={m.playStep(step.title, status)}
      className="flex items-center gap-3 rounded-[14px] bg-light-soft p-2 ring-1 ring-light/50 transition-colors duration-150 ease-out hover:bg-light-soft/70"
    >
      {body}
    </Link>
  ) : (
    <div className="flex items-center gap-3 rounded-[14px] p-2" role="img" aria-label={m.stepState(step.title, status)}>
      {body}
    </div>
  );
}

export function Waiting({ line }: { line: string }) {
  return (
    <div role="status" className="flex flex-col items-center gap-4 px-6 pt-28 text-center motion-safe:animate-fade-in-late">
      <LearningBuddy mood="thinking" size={132} />
      <p className="rounded-pill bg-white/90 px-5 py-2 text-lg font-semibold text-tonle-900 shadow-tight">{line}</p>
    </div>
  );
}

/** Something went wrong in the child space. Without `onRetry` there is nothing to try again (e.g. a closed space). */
export function Trouble({ onRetry, compact, line }: { onRetry?: () => void; compact?: boolean; line?: string }) {
  const m = useMessages(childMessages);
  return (
    <div role="alert" className={cn("flex flex-col items-center gap-4 px-6 text-center", compact ? "py-6" : "pt-28")}>
      <LearningBuddy mood="encourage" size={compact ? 88 : 124} />
      <p className="max-w-sm rounded-[20px] bg-white/95 px-5 py-3 text-lg font-semibold text-tonle-950 shadow-tight">{line ?? m.trouble}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className={cn(goButton, "min-h-14 px-9 text-lg")}>
          {m.tryAgain}
        </button>
      )}
    </div>
  );
}
