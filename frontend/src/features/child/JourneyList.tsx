import { useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { BookOpen, Check, Play } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import type { Chapter, Journey, JourneyStep } from "@/lib/api/types";
import { stepTag } from "@/features/learning/explain";
import { focusChapter } from "@/features/learning/chapters";
import { khmerNumber } from "@/features/world/PathOfBooks";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";
import { Trouble } from "./ChildStates";

export function JourneyList({
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
    <ol
      className={cn(
        // Y39: the chapters as stops on a village trail - a dashed sand path runs behind the stops.
        "relative flex flex-col gap-1.5 before:absolute before:bottom-5 before:left-[25px] before:top-5 before:w-[3px] before:rounded-full before:bg-[repeating-linear-gradient(to_bottom,#E6CFA0_0_8px,transparent_8px_14px)]",
        scroll && "max-h-[380px] overflow-y-auto pr-1",
      )}
      aria-label={m.home.chapters}
    >
      {journey.chapters.map((chapter) =>
        chapter.id === focus.chapter!.id ? (
          <li key={chapter.id} className="relative rounded-[20px] bg-light-soft/60 p-2 ring-1 ring-light/40">
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
          "relative flex h-9 w-9 shrink-0 items-center justify-center rounded-full ring-4 ring-white",
          done ? "bg-light text-tonle-950" : later ? "bg-paper-sunk text-ink-faint" : "bg-tonle-100 text-tonle-700",
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
        "relative flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-bold ring-4 ring-white",
        done && "bg-light text-tonle-950",
        isCurrent && "bg-tonle-900 text-light shadow-[0_0_0_6px_rgba(242,169,59,0.35)]",
        !done && !isCurrent && "bg-paper-sunk text-ink-soft",
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
      className="flex items-center gap-3 rounded-[16px] bg-white p-2 shadow-tight ring-1 ring-light/60 transition-[transform,box-shadow] duration-150 ease-out hover:-translate-y-0.5 hover:shadow-soft"
    >
      {body}
    </Link>
  ) : (
    <div className="flex items-center gap-3 rounded-[14px] p-2" role="img" aria-label={m.stepState(step.title, status)}>
      {body}
    </div>
  );
}
