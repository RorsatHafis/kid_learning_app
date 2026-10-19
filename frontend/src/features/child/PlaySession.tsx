import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowDownToLine, ArrowRight, BookOpen, Hash, Library, Link2, ListOrdered, Pencil, Pointer, ThumbsUp, X } from "lucide-react";
import { ProgressBuds, Bud } from "@/features/world/ProgressBuds";
import { activityAttemptApi, activityContentApi, childrenApi, enrollmentApi, learningApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import { useAsync } from "@/lib/hooks/useAsync";
import type { ActivityVersionDetail, AnswerRecord, Chapter, EnrollmentOverview, JourneyStep, QuestionType } from "@/lib/api/types";
import { BuddySays, LearningBuddy } from "@/features/buddy/LearningBuddy";
import { play as playCue } from "@/features/buddy/sound";
import { childReason } from "@/features/learning/explain";
import { chapterRuns, focusChapter, journeyLight } from "@/features/learning/chapters";
import { goButton, quietButton } from "./chips";
import { PathStrip } from "@/features/world/PathStrip";
import type { PathStep } from "@/features/world/PathOfBooks";
import { cn } from "@/lib/utils/cn";
import { ChildSpaceLayout } from "./ChildSpaceLayout";
import { Trouble, Waiting } from "./ChildHome";
import { QuestionView, type AnswerResult } from "./QuestionView";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";
import { breakDue, readSitting, sittingMinutes, useBreakReminder } from "./breaks";
import { afterActivity, type GardenNews } from "./garden";
import { Explanation } from "./Explanation";
import { gardenMessages } from "./garden.messages";
import { Plant } from "@/features/world/Plant";
import type { CurriculumProgress } from "@/lib/api/types";
import { childBreakMessages } from "@/features/parent/breaks.messages";

/** Why loading or saving stopped: a key into the child's words, so the line follows the language. */
type TroubleKey = "loadFailed" | "saveFailed";
/** A line computed from the path when it is shown (so it is in the language on screen then). */
type Line = () => string;

type Phase =
  | { kind: "loading" }
  /** Not an error: LearningPathService#getNextItem answers 404 when nothing is
   * PENDING/IN_PROGRESS — the child is simply caught up. */
  | { kind: "caught-up" }
  | { kind: "error"; message: TroubleKey }
  | { kind: "intro"; attemptId: string; itemId: string; activity: ActivityVersionDetail; reason: Line | null }
  | {
      kind: "playing";
      attemptId: string;
      itemId: string;
      activity: ActivityVersionDetail;
      index: number;
      result: AnswerResult | null;
      startedAt: number;
    }
  | { kind: "completing" }
  | {
      kind: "completed";
      /** Y41: what this activity did for the garden (server standings, before vs after); null when unknown. */
      garden?: GardenNews | null;
      title: string;
      correct: number;
      total: number;
      /** The path's next step as the server now has it; null when caught up;
       * undefined when it couldn't be read (then "Keep going" is still offered). */
      next: JourneyStep | null | undefined;
      /** The chapter this activity finished, when it was the chapter's last step. */
      finishedChapter: Chapter | null;
      /** The chapter the next step is in. */
      nextChapter: Chapter | null;
      /** The finished step, and its chapter's steps as the server now has them (for the river). */
      itemId: string;
      chapterSteps: PathStep[];
      nextId: string | null;
      /** How far the path has brought the dawn, after this activity (see journeyLight). */
      lightAfter: number | null;
    };

/** The server's recorded answer for this question in this attempt, if any. */
async function findRecordedAnswer(attemptId: string, questionVersionId: string): Promise<AnswerRecord | null> {
  try {
    const answers = await activityAttemptApi.listAnswers(attemptId);
    return answers.find((answer) => answer.questionVersionId === questionVersionId) ?? null;
  } catch {
    return null;
  }
}

/**
 * Plays the next step of one learning path. Everything that matters is server
 * state: the next step comes from the path, an attempt is started or resumed on
 * the server, answers are scored there, and completion runs the whole learning-
 * state transition in one transaction (Battle 5D). This screen resumes from what
 * the server has recorded, so a reload, a lost response or a second tab never
 * loses or duplicates work (directive §12.5).
 */
export function PlaySession() {
  const { childId = "", enrollmentId = "" } = useParams<{ childId: string; enrollmentId: string }>();
  const child = useAsync(() => childrenApi.get(childId), [childId]);
  const [phase, setPhase] = useState<Phase>({ kind: "loading" });
  const m = useMessages(childMessages);
  // Y36: the sitting grows while the child plays and finishes activities.
  const breaks = useBreakReminder(childId);
  const { touch } = breaks;
  // Y39: what the answers showed before this activity, to say afterwards what grew. Read once per
  // activity; a failed read only means no "your garden grew" (it never blocks playing).
  const skillsBefore = useRef<CurriculumProgress[] | null>(null);
  const snapshotFor = useRef<string | null>(null);
  useEffect(() => {
    if ((phase.kind !== "intro" && phase.kind !== "playing") || snapshotFor.current === phase.attemptId) return;
    snapshotFor.current = phase.attemptId;
    skillsBefore.current = null;
    learningApi
      .skills(childId)
      .then((s) => {
        skillsBefore.current = s;
      })
      .catch(() => undefined);
  }, [phase, childId]);
  useEffect(() => {
    if (phase.kind === "playing" || phase.kind === "completed") touch();
  }, [phase.kind, touch]);
  useDocumentTitle(
    phase.kind === "intro" || phase.kind === "playing" ? phase.activity.title : phase.kind === "completed" ? phase.title : m.play.documentTitle,
  );
  // React 18 StrictMode runs effects twice in development; without this guard the
  // start call could fire twice in a row for the same path item.
  const loadInFlight = useRef(false);

  const overviewOfPath = useCallback(async (): Promise<EnrollmentOverview | null | undefined> => {
    try {
      const overviews = await learningApi.enrollments(childId);
      return overviews.find((overview) => overview.enrollmentId === enrollmentId) ?? null;
    } catch {
      return undefined;
    }
  }, [childId, enrollmentId]);

  /** The path after a completion, read from the journey: what comes next, and whether a chapter just closed. */
  const journeyAfter = useCallback(
    async (itemId: string) => {
      try {
        const journey = await learningApi.journey(enrollmentId);
        const next = journey.steps.find((step) => step.learningPathItemId === journey.nextStepId) ?? null;
        const chapterOf = (step: JourneyStep | null | undefined) =>
          step?.chapterId ? (journey.chapters.find((chapter) => chapter.id === step.chapterId) ?? null) : null;
        const done = chapterOf(journey.steps.find((step) => step.learningPathItemId === itemId));
        const nextChapter = chapterOf(next);
        // The stretch of river the finished step lies on: its chapter's steps (practice met inside
        // it included), or the chapter in focus when the step belongs to none.
        const run = chapterRuns(journey).filter((r) => r.steps.some(({ step }) => step.learningPathItemId === itemId));
        const chapterSteps = done
          ? chapterRuns(journey).filter((r) => r.chapter?.id === done.id || run.includes(r)).flatMap((r) => r.steps.map(({ step }) => step))
          : focusChapter(journey).steps.map(({ step }) => step);
        return {
          next,
          finishedChapter: done && done.state === "COMPLETE" && done.id !== nextChapter?.id ? done : null,
          nextChapter,
          chapterSteps,
          nextId: journey.nextStepId,
          lightAfter: journeyLight(journey),
        };
      } catch {
        return { next: undefined, finishedChapter: null, nextChapter: null, chapterSteps: [], nextId: null, lightAfter: null };
      }
    },
    [enrollmentId],
  );

  const finishAttempt = useCallback(
    async (attemptId: string, title: string, itemId: string) => {
      setPhase({ kind: "completing" });
      try {
        await activityAttemptApi.complete(attemptId);
      } catch {
        // Retry goes through loadNext, which re-reads server state: it resumes at
        // any unanswered question, or moves on if the attempt did complete.
        setPhase({ kind: "error", message: "saveFailed" });
        return;
      }
      playCue("complete");
      const [answers, after, skillsAfter] = await Promise.all([
        activityAttemptApi.listAnswers(attemptId).catch(() => [] as AnswerRecord[]),
        journeyAfter(itemId),
        learningApi.skills(childId).catch(() => null),
      ]);
      setPhase({
        kind: "completed",
        title,
        correct: answers.filter((answer) => answer.correct).length,
        total: answers.length,
        itemId,
        garden: afterActivity(skillsBefore.current, skillsAfter),
        ...after,
      });
    },
    [journeyAfter, childId],
  );

  const loadNext = useCallback(async () => {
    if (!childId || !enrollmentId || loadInFlight.current) return;
    loadInFlight.current = true;
    setPhase({ kind: "loading" });
    try {
      let item;
      try {
        item = await enrollmentApi.nextActivity(enrollmentId);
      } catch (err) {
        setPhase(err instanceof ApiError && err.status === 404 ? { kind: "caught-up" } : { kind: "error", message: "loadFailed" });
        return;
      }

      try {
        // Returns the existing IN_PROGRESS attempt for this path item if there is
        // one (Battle 2A) — this is also the resume path.
        const attempt = await activityAttemptApi.start(childId, {
          activityVersionId: item.activityVersionId,
          learningPathItemId: item.id,
        });
        const [activity, answers, overview] = await Promise.all([
          activityContentApi.getVersion(item.activityVersionId, childId),
          activityAttemptApi.listAnswers(attempt.id),
          overviewOfPath(),
        ]);
        const next = overview?.nextStep ?? null;

        const items = [...activity.items].sort((a, b) => a.sequenceOrder - b.sequenceOrder);
        const ordered = { ...activity, items };
        const answered = new Set(answers.map((answer) => answer.questionVersionId));
        const resumeIndex = items.findIndex((question) => !answered.has(question.questionVersionId));

        if (resumeIndex === -1) {
          // Every question already answered (the child left on the last feedback
          // screen) — the attempt only needs completing.
          await finishAttempt(attempt.id, activity.title, item.id);
          return;
        }
        if (resumeIndex === 0) {
          const ofThis = next && next.learningPathItemId === item.id ? next : null;
          // The first book of a chapter opens the chapter: the child meets its name here.
          const chapter = ofThis?.chapterId ? overview?.chapters.find((c) => c.id === ofThis.chapterId) : undefined;
          const opensChapter = chapter && chapter.completedSteps === 0 && (overview?.chapterCount ?? 0) > 1;
          const reason: Line | null = opensChapter
            ? () => {
                const words = messagesNow(childMessages).play;
                return (chapter.position === 1 ? words.journeyBegins : words.chapterBegins)(chapter.position, chapter.title);
              }
            : ofThis
              ? () => childReason(ofThis)
              : null;
          setPhase({ kind: "intro", attemptId: attempt.id, itemId: item.id, activity: ordered, reason });
          return;
        }
        setPhase({
          kind: "playing",
          attemptId: attempt.id,
          itemId: item.id,
          activity: ordered,
          index: resumeIndex,
          result: null,
          startedAt: Date.now(),
        });
      } catch {
        setPhase({ kind: "error", message: "loadFailed" });
      }
    } finally {
      loadInFlight.current = false;
    }
  }, [childId, enrollmentId, finishAttempt, overviewOfPath]);

  useEffect(() => {
    void loadNext();
  }, [loadNext]);

  const journeyHref = `/learn/${childId}?path=${enrollmentId}`;
  const shell = (content: React.ReactNode, focus = false) => (
    <ChildSpaceLayout
      childId={childId}
      childName={child.data?.displayName}
      childLanguage={child.data?.preferredLanguage}
      buddy={child.data?.learningBuddy}
      home={journeyHref}
      focus={focus}
    >
      {content}
    </ChildSpaceLayout>
  );

  switch (phase.kind) {
    case "loading":
      return shell(<Waiting line={m.play.gettingNext} />);
    case "completing":
      return shell(<Waiting line={m.play.saving} />);
    case "error":
      return shell(<Trouble onRetry={loadNext} line={m.play[phase.message]} />);
    case "caught-up":
      return shell(
        <div className="flex flex-col items-center gap-8 px-6 pt-24 text-center">
          <BuddySays mood="cheer" size={150}>
            {m.play.caughtUp}
          </BuddySays>
          <div className="flex flex-wrap justify-center gap-3">
            <JourneyLink href={`/learn/${childId}/shelf`} solid toShelf />
            <JourneyLink href={journeyHref} />
          </div>
        </div>,
      );
    case "intro":
      return shell(
        <Intro
          activity={phase.activity}
          reason={phase.reason}
          journeyHref={journeyHref}
          onStart={() =>
            setPhase({
              kind: "playing",
              attemptId: phase.attemptId,
              itemId: phase.itemId,
              activity: phase.activity,
              index: 0,
              result: null,
              startedAt: Date.now(),
            })
          }
        />,
      );
    case "completed":
      return shell(
        <Completed
          phase={phase}
          journeyHref={journeyHref}
          onKeepGoing={loadNext}
          breakSuggestion={
            breakDue(breaks.minutes, readSitting(childId), Date.now())
              ? { minutes: sittingMinutes(readSitting(childId), Date.now()), rest: breaks.rest, oneMore: breaks.oneMore }
              : null
          }
        />,
      );
    case "playing":
      return shell(
        // Keyed per question: each question gets fresh interaction state
        // (submitting flag, error, idempotency keys).
        <Question
          key={`${phase.attemptId}:${phase.index}`}
          phase={phase}
          journeyHref={journeyHref}
          onAnswered={(result) => setPhase({ ...phase, result })}
          onResync={loadNext}
          onContinue={() => {
            const nextIndex = phase.index + 1;
            if (nextIndex < phase.activity.items.length) {
              setPhase({ ...phase, index: nextIndex, result: null, startedAt: Date.now() });
              return;
            }
            void finishAttempt(phase.attemptId, phase.activity.title, phase.itemId);
          }}
        />,
        true,
      );
  }
}

function JourneyLink({ href, solid, toShelf = false }: { href: string; solid?: boolean; toShelf?: boolean }) {
  const m = useMessages(childMessages);
  return (
    <Link to={href} className={cn(quietButton, solid && "bg-tonle-50 ring-tonle-200")}>
      {toShelf ? <Library size={19} aria-hidden /> : <BookOpen size={19} aria-hidden />} {toShelf ? m.myShelf : m.myPath}
    </Link>
  );
}

// ------------------------------------------------------------------ playing a book again (X24)

type PracticePhase =
  | { kind: "loading" }
  | { kind: "error"; message: TroubleKey | "startedOnPath" | "cantPlayAgain"; retry: boolean }
  | Extract<Phase, { kind: "intro" | "playing" }>
  | { kind: "completing" }
  | { kind: "done"; title: string; correct: number; total: number };

/**
 * A book the child has already lit, played again from their shelf. It is free practice - an
 * attempt outside the path - so its answers are scored and count as evidence like any other, and
 * it moves nothing on the path: no "up next" here, and the path stays where it is.
 */
export function PracticeSession() {
  const { childId = "", activityVersionId = "" } = useParams<{ childId: string; activityVersionId: string }>();
  const child = useAsync(() => childrenApi.get(childId), [childId]);
  const [phase, setPhase] = useState<PracticePhase>({ kind: "loading" });
  const m = useMessages(childMessages);
  useDocumentTitle(phase.kind === "intro" || phase.kind === "playing" ? phase.activity.title : m.play.againTitle);
  const loadInFlight = useRef(false);
  const shelfHref = `/learn/${childId}/shelf`;

  const finish = useCallback(async (attemptId: string, title: string) => {
    setPhase({ kind: "completing" });
    try {
      await activityAttemptApi.complete(attemptId);
    } catch {
      setPhase({ kind: "error", message: "saveFailed", retry: true });
      return;
    }
    playCue("complete");
    const answers = await activityAttemptApi.listAnswers(attemptId).catch(() => [] as AnswerRecord[]);
    setPhase({ kind: "done", title, correct: answers.filter((a) => a.correct).length, total: answers.length });
  }, []);

  const load = useCallback(async () => {
    if (!childId || !activityVersionId || loadInFlight.current) return;
    loadInFlight.current = true;
    setPhase({ kind: "loading" });
    try {
      let attempt;
      try {
        // No path item: free practice. An unfinished practice of this book is resumed.
        attempt = await activityAttemptApi.start(childId, { activityVersionId });
      } catch (err) {
        if (err instanceof ApiError && err.status === 409) {
          setPhase({ kind: "error", message: "startedOnPath", retry: false });
        } else if (err instanceof ApiError && err.status === 404) {
          setPhase({ kind: "error", message: "cantPlayAgain", retry: false });
        } else {
          setPhase({ kind: "error", message: "loadFailed", retry: true });
        }
        return;
      }
      const [activity, answers] = await Promise.all([
        activityContentApi.getVersion(activityVersionId, childId),
        activityAttemptApi.listAnswers(attempt.id),
      ]);
      const items = [...activity.items].sort((a, b) => a.sequenceOrder - b.sequenceOrder);
      const ordered = { ...activity, items };
      const answered = new Set(answers.map((answer) => answer.questionVersionId));
      const resumeIndex = items.findIndex((question) => !answered.has(question.questionVersionId));
      if (resumeIndex === -1) {
        await finish(attempt.id, activity.title);
      } else if (resumeIndex === 0) {
        setPhase({ kind: "intro", attemptId: attempt.id, itemId: "", activity: ordered, reason: () => messagesNow(childMessages).play.playingAgain });
      } else {
        setPhase({ kind: "playing", attemptId: attempt.id, itemId: "", activity: ordered, index: resumeIndex, result: null, startedAt: Date.now() });
      }
    } catch {
      setPhase({ kind: "error", message: "loadFailed", retry: true });
    } finally {
      loadInFlight.current = false;
    }
  }, [childId, activityVersionId, finish]);

  useEffect(() => {
    void load();
  }, [load]);

  const shell = (content: React.ReactNode) => (
    <ChildSpaceLayout
      childId={childId}
      childName={child.data?.displayName}
      childLanguage={child.data?.preferredLanguage}
      buddy={child.data?.learningBuddy}
      home={shelfHref}
      focus={phase.kind === "playing"}
    >
      {content}
    </ChildSpaceLayout>
  );

  switch (phase.kind) {
    case "loading":
      return shell(<Waiting line={m.play.gettingBook} />);
    case "completing":
      return shell(<Waiting line={m.play.saving} />);
    case "error":
      return shell(
        phase.retry ? (
          <Trouble onRetry={load} line={m.play[phase.message]} />
        ) : (
          <div className="flex flex-col items-center gap-8 px-6 pt-24 text-center">
            <BuddySays mood="encourage" size={140}>
              {m.play[phase.message]}
            </BuddySays>
            <JourneyLink href={shelfHref} solid toShelf />
          </div>
        ),
      );
    case "intro":
      return shell(
        <Intro
          activity={phase.activity}
          reason={phase.reason}
          journeyHref={shelfHref}
          onStart={() =>
            setPhase({ kind: "playing", attemptId: phase.attemptId, itemId: "", activity: phase.activity, index: 0, result: null, startedAt: Date.now() })
          }
        />,
      );
    case "playing":
      return shell(
        <Question
          key={`${phase.attemptId}:${phase.index}`}
          phase={phase}
          journeyHref={shelfHref}
          onAnswered={(result) => setPhase({ ...phase, result })}
          onResync={load}
          onContinue={() => {
            const nextIndex = phase.index + 1;
            if (nextIndex < phase.activity.items.length) {
              setPhase({ ...phase, index: nextIndex, result: null, startedAt: Date.now() });
              return;
            }
            void finish(phase.attemptId, phase.activity.title);
          }}
        />,
      );
    case "done":
      return shell(
        <div className="px-4 pb-16 pt-24 sm:pt-28">
          <Page labelledBy="practice-done" className="px-6 pb-10 pt-12 text-center motion-safe:animate-pop-in sm:px-14 sm:pb-12">
            <LearningBuddy mood="cheer" size={132} className="mx-auto -mt-24 sm:-mt-28" />
            <h1 id="practice-done" className="mt-2 font-display text-headline font-bold text-tonle-950">
              {phase.total > 0 && phase.correct === phase.total ? m.play.perfectAgain : m.play.practiceDone}
            </h1>
            <p className="mt-1 text-lg font-medium text-ink-soft">{m.play.playedAgain(phase.title)}</p>
            {phase.total > 0 && (
              <p className="mx-auto mt-5 w-fit rounded-pill bg-light-soft px-5 py-2 text-lg font-bold text-light-deep ring-1 ring-light/40">
                {m.play.gotRight(phase.correct, phase.total)}
              </p>
            )}
            <div className="mt-8 flex flex-col-reverse items-center justify-center gap-4 sm:flex-row">
              <JourneyLink href={`/learn/${childId}`} />
              <Link to={shelfHref} className={goButton}>
                <Library size={22} aria-hidden /> {m.play.backToShelf}
              </Link>
            </div>
          </Page>
        </div>,
      );
  }
}

/** The page the child reads from: one clean sheet over the quiet field. */
function Page({ children, className, labelledBy }: { children: React.ReactNode; className?: string; labelledBy: string }) {
  return (
    <section
      aria-labelledby={labelledBy}
      className={cn(
        "relative mx-auto w-full max-w-3xl rounded-[32px] bg-[#FFFDF8] text-ink shadow-lift ring-1 ring-tonle-950/5",
        // Y38: a storybook page - a printed frame line just inside the sheet.
        "before:pointer-events-none before:absolute before:inset-[10px] before:rounded-[24px] before:border before:border-[#EFE6D2]",
        className,
      )}
    >
      {children}
    </section>
  );
}

function Intro({
  activity,
  reason,
  journeyHref,
  onStart,
}: {
  activity: ActivityVersionDetail;
  reason: Line | null;
  journeyHref: string;
  onStart: () => void;
}) {
  const startRef = useRef<HTMLButtonElement | null>(null);
  const m = useMessages(childMessages).play;
  useEffect(() => startRef.current?.focus(), []);
  const n = activity.items.length;
  return (
    <div className="px-4 pb-16 pt-28 sm:pt-32">
      <Page labelledBy="activity-title" className="px-6 pb-10 pt-10 text-center motion-safe:animate-page-in sm:px-14 sm:pb-12">
        <LearningBuddy mood="happy" size={140} className="mx-auto -mt-32 sm:-mt-36" />
        {reason && <p className="mx-auto mt-2 w-fit rounded-pill bg-tonle-50 px-4 py-1.5 text-sm font-semibold text-tonle-800">{reason()}</p>}
        <h1 id="activity-title" className="mt-4 font-display text-headline font-bold text-tonle-950">
          {activity.title}
        </h1>
        {activity.instructions && <p className="mx-auto mt-3 max-w-lg text-lg font-medium leading-relaxed text-ink-soft">{activity.instructions}</p>}

        <div className="mx-auto mt-7 flex w-fit flex-col items-center gap-2">
          <div className="flex flex-wrap justify-center gap-1.5" aria-hidden>
            {Array.from({ length: n }, (_, i) => (
              <Bud key={i} state={i === 0 ? "current" : "waiting"} size={n > 10 ? 26 : 38} />
            ))}
          </div>
          <p className="text-base font-semibold text-ink-soft">{m.lanterns(n)}</p>
        </div>
        <Kinds activity={activity} />

        <div className="mt-9 flex flex-col-reverse items-center justify-center gap-4 sm:flex-row">
          <JourneyLink href={journeyHref} />
          <button ref={startRef} type="button" onClick={onStart} className={goButton}>
            {m.letsGo} <ArrowRight size={22} aria-hidden />
          </button>
        </div>
      </Page>
    </div>
  );
}

/** A picture of what each kind of question asks the child to do; the word beside it is the label. */
const KIND_PICTURE: Record<QuestionType, typeof Pointer> = {
  MULTIPLE_CHOICE: Pointer,
  TRUE_FALSE: ThumbsUp,
  NUMERIC: Hash,
  SHORT_ANSWER: Pencil,
  ORDERING: ListOrdered,
  MATCHING: Link2,
  SORTING: ArrowDownToLine,
};

/**
 * Y40: what this book will ask the child to do - choose, put in order, match, sort... - as pictures
 * with one word each, in the order they first come. Read from the activity's own questions.
 */
function Kinds({ activity }: { activity: ActivityVersionDetail }) {
  const m = useMessages(childMessages).play;
  const kinds = [...new Set(activity.items.map((item) => item.questionType))].filter((k): k is QuestionType => k in KIND_PICTURE);
  if (kinds.length === 0) return null;
  return (
    <div className="mx-auto mt-6 flex max-w-lg flex-col items-center gap-2.5">
      <p id="in-this-book" className="text-sm font-bold text-clay-deep">
        {m.inThisBook}
      </p>
      <ul aria-labelledby="in-this-book" className="flex flex-wrap justify-center gap-2">
        {kinds.map((kind) => {
          const Icon = KIND_PICTURE[kind];
          return (
            <li key={kind} className="flex items-center gap-2 rounded-pill bg-tonle-50 py-1.5 pl-1.5 pr-4 font-bold text-tonle-800 ring-1 ring-tonle-100">
              <span aria-hidden className="flex h-8 w-8 items-center justify-center rounded-full bg-white text-tonle-600 shadow-tight">
                <Icon size={17} strokeWidth={2.4} />
              </span>
              {m.kinds[kind]}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

// Varied, authored words for a right answer (every one still says so plainly).
const RIGHT_HEADLINES = ["right1", "right2", "right3"] as const;

/**
 * One question, and nothing else competing with it: a way back to the path, a quiet row of buds,
 * the question on a clean page, the buddy small at its corner, and - once answered - one clear
 * next step. Everything about the answer (scoring, the recorded result, resuming) is the server's.
 */
function Question({
  phase,
  journeyHref,
  onAnswered,
  onResync,
  onContinue,
}: {
  phase: Extract<Phase, { kind: "playing" }>;
  journeyHref: string;
  onAnswered: (result: AnswerResult) => void;
  onResync: () => void;
  onContinue: () => void;
}) {
  const { attemptId, activity, index, result, startedAt } = phase;
  const item = activity.items[index];
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<"answerDoesntFit" | "answerNotSent" | null>(null);
  const m = useMessages(childMessages).play;
  const q = useMessages(childMessages).question;
  // One idempotency key per distinct answer to this question: retrying the same
  // answer after a lost response replays the original result server-side; a
  // different answer gets a fresh key.
  const idempotencyKeys = useRef(new Map<string, string>());
  const continueRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (result) continueRef.current?.focus();
  }, [result]);

  if (!item) return null;

  function reveal(record: AnswerRecord, submitted: string) {
    playCue(record.correct ? "correct" : "incorrect");
    onAnswered({ submitted, correct: record.correct, correctAnswer: record.correctAnswer, explanation: record.explanation ?? null });
  }

  async function handleAnswer(submittedAnswer: string) {
    if (isSubmitting) return; // a rapid double-tap must not fire two submits
    setIsSubmitting(true);
    setSubmitError(null);
    let idempotencyKey = idempotencyKeys.current.get(submittedAnswer);
    if (!idempotencyKey) {
      idempotencyKey = crypto.randomUUID();
      idempotencyKeys.current.set(submittedAnswer, idempotencyKey);
    }
    try {
      const record = await activityAttemptApi.submitAnswer(attemptId, {
        questionVersionId: item.questionVersionId,
        submittedAnswer,
        timeSpentSeconds: Math.max(1, Math.round((Date.now() - startedAt) / 1000)),
        idempotencyKey,
      });
      reveal(record, submittedAnswer);
    } catch (err) {
      // The server is the source of truth: the answer may already be recorded
      // (lost response, answered in another tab) — if so, carry on with it.
      const recorded = await findRecordedAnswer(attemptId, item.questionVersionId);
      if (recorded) {
        reveal(recorded, submittedAnswer);
        return;
      }
      if (err instanceof ApiError && err.status === 409) {
        // The attempt no longer accepts answers (completed or abandoned elsewhere):
        // reload the real state rather than guess.
        onResync();
        return;
      }
      setSubmitError(err instanceof ApiError && err.status === 422 ? "answerDoesntFit" : "answerNotSent");
      setIsSubmitting(false);
    }
  }

  const total = activity.items.length;
  const answeredHere = result ? 1 : 0;
  const mood = result ? (result.correct ? "cheer" : "encourage") : isSubmitting ? "thinking" : "idle";
  return (
    <div className="flex flex-col gap-5 px-4 pb-16 pt-1 sm:gap-7">
      <div className="mx-auto grid w-full max-w-3xl grid-cols-[2.75rem_minmax(0,1fr)_2.75rem] items-center gap-3">
        <Link
          to={journeyHref}
          aria-label={m.backToJourney}
          className="flex h-11 w-11 items-center justify-center rounded-full bg-white/90 text-tonle-900 shadow-tight ring-1 ring-tonle-950/10 transition-colors duration-150 ease-out hover:bg-white"
        >
          <X size={20} aria-hidden />
        </Link>
        <ProgressBuds total={total} done={index + answeredHere} current={result ? null : index} label={m.questionOf(index + 1, total)} />
        <span aria-hidden />
      </div>

      <Page
        labelledBy="question-prompt"
        className={cn(
          "mt-6 transition-shadow duration-500 motion-safe:animate-page-in sm:mt-8",
          result?.correct && "shadow-[0_0_0_2px_#F2A93B,0_30px_70px_-20px_rgba(199,122,30,0.45)]",
        )}
      >
        {/* The buddy keeps the child company at the page's corner and reacts to the answer. */}
        <div aria-hidden className="pointer-events-none absolute -top-14 right-3 sm:-top-16 sm:right-8">
          <LearningBuddy mood={mood} size={78} className="sm:hidden" />
          <LearningBuddy mood={mood} size={96} className="hidden sm:block" />
        </div>

        <div className="flex flex-col gap-8 px-5 pb-9 pt-10 sm:px-12 sm:pb-11 sm:pt-12">
          <QuestionView item={item} onAnswer={handleAnswer} disabled={isSubmitting} result={result} />
          {submitError && (
            <p role="alert" className="text-center text-base font-semibold text-brick-deep">
              {m[submitError]}
            </p>
          )}
        </div>

        {result && (
          <div
            role="status"
            className={cn(
              "flex flex-col gap-5 rounded-b-[32px] px-6 pb-7 pt-6 motion-safe:animate-rise-in sm:px-12",
              result.correct ? "bg-light-soft" : "bg-tonle-50",
            )}
          >
          <div className="flex flex-col items-center gap-5 sm:flex-row">
            {/* Y40: the verdict as a picture, for a child who can't read the words yet. */}
            <span aria-hidden className={cn("flex h-16 w-16 shrink-0 items-center justify-center rounded-full ring-4 ring-white", result.correct ? "bg-white motion-safe:animate-pop-in" : "bg-white/80")}>
              <Bud state={result.correct ? "open" : "current"} size={44} />
            </span>
            <div className="flex-1 text-center sm:text-left">
              <p className={cn("font-display text-[1.875rem] font-bold leading-tight", result.correct ? "text-light-deep" : "text-tonle-800")}>
                {result.correct ? m[RIGHT_HEADLINES[index % RIGHT_HEADLINES.length]] : m.notQuite}
              </p>
              <p className="mt-1 text-lg font-medium text-ink">
                {result.correct
                  ? m.lanternLit
                  : result.correctAnswer?.matches?.length
                    ? m.matchesMixed
                    : result.correctAnswer?.text
                      ? m.theAnswerIs(
                          item.questionType === "TRUE_FALSE" ? (result.correctAnswer.text === "true" ? q.true : q.false) : result.correctAnswer.text,
                        )
                      : m.keepGoingLine}
              </p>
            </div>
            <button ref={continueRef} type="button" onClick={onContinue} className={goButton}>
              {index + 1 < total ? m.next : m.finish} <ArrowRight size={22} aria-hidden />
            </button>
          </div>
          {result.explanation && <Explanation text={result.explanation} />}
          </div>
        )}
      </Page>
    </div>
  );
}

/**
 * A moment of light when the work was all right or a chapter ends: motes of marigold and lotus rise
 * from the page and fade. Purely decorative (hidden from assistive tech) and gone under reduced motion.
 */
function Celebration() {
  const motes = Array.from({ length: 18 }, (_, i) => {
    const angle = (i / 18) * Math.PI - Math.PI; // the upper half
    const reach = 120 + (i % 3) * 46;
    return { bx: Math.cos(angle) * reach, by: Math.sin(angle) * reach, delay: (i % 6) * 70, lotus: i % 3 === 0 };
  });
  return (
    <span aria-hidden className="pointer-events-none absolute left-1/2 top-24 z-10 motion-reduce:hidden">
      {motes.map((mote, i) => (
        <span
          key={i}
          className={cn("absolute h-2.5 w-2.5 rounded-full animate-burst", mote.lotus ? "bg-lotus" : "bg-light-glow shadow-[0_0_12px_rgba(255,210,122,0.9)]")}
          style={{ "--bx": `${mote.bx}px`, "--by": `${mote.by}px`, animationDelay: `${mote.delay}ms`, animationDuration: "1100ms" } as React.CSSProperties}
        />
      ))}
    </span>
  );
}

/**
 * Y41: what this activity did for the child's garden, told truthfully (garden.ts afterActivity):
 * "grew" only when the server's standing really advanced - a bloom when the answers show the skill is
 * secure; a new bud or a planted seed said as what it is; otherwise warm words for the practice itself.
 */
function GardenNewsPanel({ news }: { news: GardenNews }) {
  const g = useMessages(gardenMessages);
  const fresh = [...news.budded, ...news.planted];
  const label = (plant: (typeof fresh)[number]) => (plant.stage === "bud" ? g.budded(plant.skill.name) : g.planted(plant.skill.name));
  const plants = (list: typeof fresh, text: (p: (typeof fresh)[number]) => string) => (
    <ul className="mt-3 flex flex-wrap justify-center gap-x-5 gap-y-3">
      {list.slice(0, 4).map((p, i) => (
        <li key={p.skill.skillId} className="flex flex-col items-center gap-1 motion-safe:animate-pop-in" style={{ animationDelay: `${300 + i * 140}ms` }}>
          <Plant stage={p.stage} size={56} />
          <span className="max-w-[10rem] text-sm font-bold leading-snug text-tonle-950">{text(p)}</span>
        </li>
      ))}
    </ul>
  );
  if (news.grew.length > 0) {
    return (
      <section aria-labelledby="garden-news" className="mx-auto mt-6 max-w-md rounded-[22px] bg-rice-soft px-5 pb-4 pt-4 ring-1 ring-rice/30">
        <h2 id="garden-news" className="font-display text-xl font-bold text-tonle-950">
          {g.grew}
        </h2>
        <p className="text-sm font-medium text-rice-deep">{g.grewNote}</p>
        {plants(news.grew, (p) => (p.stage === "bloom" ? g.grewPlant.bloom(p.skill.name) : g.grewPlant.bud(p.skill.name)))}
        {fresh.length > 0 && (
          <p className="mt-3 text-sm font-medium text-ink-soft">
            {g.alsoNew}: {fresh.map((p) => p.skill.name).join(", ")}
          </p>
        )}
      </section>
    );
  }
  if (fresh.length > 0) {
    return (
      <section aria-labelledby="garden-news" className="mx-auto mt-6 max-w-md rounded-[22px] bg-palm-soft/60 px-5 pb-4 pt-4 ring-1 ring-palm/20">
        <h2 id="garden-news" className="font-display text-xl font-bold text-tonle-950">
          {g.newTitle}
        </h2>
        <p className="text-sm font-medium text-palm-deep">{news.budded.length > 0 ? g.buddedNote : g.plantedNote}</p>
        {plants(fresh, label)}
      </section>
    );
  }
  if (news.practised.length > 0) {
    return (
      <section aria-labelledby="garden-news" className="mx-auto mt-6 max-w-md rounded-[22px] bg-tonle-50 px-5 py-4 ring-1 ring-tonle-100">
        <h2 id="garden-news" className="font-display text-xl font-bold text-tonle-950">
          {g.practisedTitle}
        </h2>
        <p className="mt-1 text-sm font-medium text-ink-soft">{g.practisedNote(news.practised.slice(0, 3).map((s) => s.name).join(", "))}</p>
      </section>
    );
  }
  return null;
}

/** Y36: a break the parent asked for, suggested at this natural pause; the child chooses. */
interface BreakSuggestion {
  minutes: number;
  rest: () => void;
  oneMore: () => void;
}

function Completed({
  phase,
  journeyHref,
  onKeepGoing,
  breakSuggestion,
}: {
  phase: Extract<Phase, { kind: "completed" }>;
  journeyHref: string;
  onKeepGoing: () => void;
  breakSuggestion: BreakSuggestion | null;
}) {
  const { title, correct, total, next, finishedChapter, nextChapter, itemId, chapterSteps, nextId } = phase;
  const allRight = total > 0 && correct === total;
  const m = useMessages(childMessages).play;
  const keepGoingRef = useRef<HTMLButtonElement | null>(null);
  useEffect(() => keepGoingRef.current?.focus(), []);
  const b = useMessages(childBreakMessages);
  const navigate = useNavigate();
  // Decided once, when the activity is finished: the suggestion doesn't come and go while it is read.
  const [suggestion] = useState(breakSuggestion);
  // The path shows the next book only while it is in this chapter.
  const nextHere = nextId !== null && chapterSteps.some((s) => s.learningPathItemId === nextId) ? nextId : null;

  return (
    <div className="px-4 pb-16 pt-28 sm:pt-32">
      <Page labelledBy="done-title" className="px-6 pb-10 pt-10 text-center motion-safe:animate-pop-in sm:px-14 sm:pb-12">
        {(allRight || finishedChapter) && <Celebration />}
        {/* One buddy: on the path when the path is shown, otherwise standing over the page. */}
        {chapterSteps.length === 0 && <LearningBuddy mood="cheer" size={140} className="mx-auto -mt-32 sm:-mt-36" />}
        <h1 id="done-title" className="font-display text-headline font-bold text-tonle-950">
          {finishedChapter ? m.chapterComplete(finishedChapter.position) : allRight ? m.perfect : m.activityComplete}
        </h1>
        <p className="mt-1 text-lg font-medium text-ink-soft">
          {finishedChapter ? m.litEveryBook(finishedChapter.title) : m.litEveryLantern(title)}
        </p>
        {total > 0 && (
          <p className="mx-auto mt-5 w-fit rounded-pill bg-light-soft px-5 py-2 text-lg font-bold text-light-deep ring-1 ring-light/40">
            {m.gotRight(correct, total)}
          </p>
        )}
        {phase.garden && <GardenNewsPanel news={phase.garden} />}
        {chapterSteps.length > 0 && (
          // The child's stretch of the path: the book just finished takes the light where it sits.
          <div className={cn("mt-8 overflow-hidden bg-[#EAF3E4] px-4 pt-3", next ? "rounded-t-[22px]" : "rounded-[22px] pb-3")}>
            <PathStrip steps={chapterSteps} justLitId={itemId} nextId={nextHere} chapterDone={finishedChapter !== null} />
          </div>
        )}
        {next && (
          <div className={cn("flex items-center gap-4 bg-tonle-50 p-5 text-left", chapterSteps.length > 0 ? "rounded-b-[22px]" : "mt-8 rounded-[22px]")}>
            <Bud state="current" size={40} className="shrink-0" />
            <div>
              <p className="text-sm font-semibold text-clay-deep">
                {finishedChapter && nextChapter ? m.upNextChapter(nextChapter.position, nextChapter.title) : messagesNow(childMessages).home.upNext}
              </p>
              <p className="font-display text-xl font-bold text-tonle-950">{next.title}</p>
              <p className="font-medium text-ink-soft">
                {finishedChapter && next.explanation.reason === "CURRICULUM" ? m.newChapter : childReason(next)}
              </p>
            </div>
          </div>
        )}
        {suggestion ? (
          <section
            aria-labelledby="break-title"
            className="mt-8 flex flex-col items-center gap-4 rounded-[22px] bg-palm-soft/70 px-5 py-6 ring-1 ring-palm/20 sm:flex-row sm:text-left"
          >
            <LearningBuddy mood="happy" size={88} className="shrink-0" />
            <div className="flex-1">
              <h2 id="break-title" className="font-display text-2xl font-bold text-tonle-950">
                {b.title}
              </h2>
              <p className="mt-1 text-lg font-medium text-ink-soft">{b.body(suggestion.minutes)}</p>
              <div className="mt-4 flex flex-col-reverse items-center gap-3 sm:flex-row">
                {next !== null && (
                  <button
                    type="button"
                    onClick={() => {
                      suggestion.oneMore();
                      onKeepGoing();
                    }}
                    className={quietButton}
                  >
                    {b.oneMore}
                  </button>
                )}
                <button
                  ref={keepGoingRef}
                  type="button"
                  onClick={() => {
                    suggestion.rest();
                    navigate(journeyHref);
                  }}
                  className={goButton}
                >
                  {b.rest}
                </button>
              </div>
            </div>
          </section>
        ) : (
          <div className="mt-8 flex flex-col-reverse items-center justify-center gap-4 sm:flex-row">
            <JourneyLink href={journeyHref} />
            {next !== null && (
              <button ref={keepGoingRef} type="button" onClick={onKeepGoing} className={goButton}>
                {m.keepGoing} <ArrowRight size={22} aria-hidden />
              </button>
            )}
          </div>
        )}
      </Page>
    </div>
  );
}
