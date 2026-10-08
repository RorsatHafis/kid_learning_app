import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowRight, BookOpen, Library } from "lucide-react";
import { Lantern, LanternString } from "@/features/world/Lantern";
import { activityAttemptApi, activityContentApi, childrenApi, enrollmentApi, learningApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import { useAsync } from "@/lib/hooks/useAsync";
import type { ActivityVersionDetail, AnswerRecord, Chapter, EnrollmentOverview, JourneyStep } from "@/lib/api/types";
import { BuddySays, LearningBuddy } from "@/features/buddy/LearningBuddy";
import { play as playCue } from "@/features/buddy/sound";
import { childReason } from "@/features/learning/explain";
import { chapterRuns, focusChapter, journeyLight } from "@/features/learning/chapters";
import { PathStrip } from "@/features/world/PathStrip";
import type { PathStep } from "@/features/world/PathOfBooks";
import { cn } from "@/lib/utils/cn";
import { ChildSpaceLayout } from "./ChildSpaceLayout";
import { Trouble, Waiting } from "./ChildHome";
import { QuestionView, type AnswerResult } from "./QuestionView";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";

const LOAD_FAILED = "We couldn't get your activity ready. Let's try again!";
const SAVE_FAILED = "We couldn't save your activity just now. Let's try again!";

type Phase =
  | { kind: "loading" }
  /** Not an error: LearningPathService#getNextItem answers 404 when nothing is
   * PENDING/IN_PROGRESS — the child is simply caught up. */
  | { kind: "caught-up" }
  | { kind: "error"; message: string }
  | { kind: "intro"; attemptId: string; itemId: string; activity: ActivityVersionDetail; reason: string | null }
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
  useDocumentTitle(phase.kind === "intro" || phase.kind === "playing" ? phase.activity.title : phase.kind === "completed" ? phase.title : "Play");
  // React 18 StrictMode runs effects twice in development; without this guard the
  // start call could fire twice in a row for the same path item.
  const loadInFlight = useRef(false);

  // How far the child's path has brought the dawn: the sky behind every play screen (journeyLight).
  const [light, setLight] = useState(0);
  // Only a change the child caused (finishing an activity) eases in; arriving values just appear.
  const [lightEases, setLightEases] = useState(false);

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
        setPhase({ kind: "error", message: SAVE_FAILED });
        return;
      }
      playCue("complete");
      const [answers, after] = await Promise.all([
        activityAttemptApi.listAnswers(attemptId).catch(() => [] as AnswerRecord[]),
        journeyAfter(itemId),
      ]);
      setPhase({
        kind: "completed",
        title,
        correct: answers.filter((answer) => answer.correct).length,
        total: answers.length,
        itemId,
        ...after,
      });
    },
    [journeyAfter],
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
        setPhase(err instanceof ApiError && err.status === 404 ? { kind: "caught-up" } : { kind: "error", message: LOAD_FAILED });
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
        if (overview) {
          setLightEases(false);
          setLight(journeyLight(overview));
        }
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
          const reason = opensChapter
            ? `${chapter.position === 1 ? "Your journey begins" : "A new chapter begins"} · Chapter ${chapter.position}: ${chapter.title}`
            : ofThis
              ? childReason(ofThis)
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
        setPhase({ kind: "error", message: LOAD_FAILED });
      }
    } finally {
      loadInFlight.current = false;
    }
  }, [childId, enrollmentId, finishAttempt, overviewOfPath]);

  // After a completion the sky moves to where the path now is — a finished chapter brings the
  // dawn a step nearer while the child is looking (the scene eases it; reduced motion jumps).
  const lightAfter = phase.kind === "completed" ? phase.lightAfter : null;
  useEffect(() => {
    if (lightAfter === null) return;
    const timer = window.setTimeout(() => {
      setLightEases(true);
      setLight(lightAfter);
    }, 600);
    return () => window.clearTimeout(timer);
  }, [lightAfter]);

  useEffect(() => {
    void loadNext();
  }, [loadNext]);

  const journeyHref = `/learn/${childId}?path=${enrollmentId}`;
  const shell = (content: React.ReactNode) => (
    <ChildSpaceLayout childId={childId} childName={child.data?.displayName} home={journeyHref} firstLight={light} easeLight={lightEases}>
      {content}
    </ChildSpaceLayout>
  );

  switch (phase.kind) {
    case "loading":
      return shell(<Waiting line="Getting your next activity ready…" />);
    case "completing":
      return shell(<Waiting line="Saving your work…" />);
    case "error":
      return shell(<Trouble onRetry={loadNext} line={phase.message} />);
    case "caught-up":
      return shell(
        <div className="flex flex-col items-center gap-8 px-6 pt-24 text-center">
          <BuddySays mood="cheer" size={150} tone="night">
            You lit every book here for now! You can play any of them again from your shelf.
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
      return shell(<Completed phase={phase} journeyHref={journeyHref} onKeepGoing={loadNext} />);
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
      );
  }
}

function JourneyLink({ href, solid, toShelf = false }: { href: string; solid?: boolean; toShelf?: boolean }) {
  return (
    <Link
      to={href}
      className={cn(
        "flex min-h-14 items-center justify-center gap-2 rounded-pill px-7 text-base font-semibold transition-colors duration-150 ease-out",
        solid ? "bg-white text-tonle-900 hover:bg-tonle-50" : "text-tonle-700 ring-1 ring-tonle-200 hover:bg-tonle-50",
      )}
    >
      {toShelf ? <Library size={19} aria-hidden /> : <BookOpen size={19} aria-hidden />} {toShelf ? "My shelf" : "My path"}
    </Link>
  );
}

// ------------------------------------------------------------------ playing a book again (X24)

type PracticePhase =
  | { kind: "loading" }
  | { kind: "error"; message: string; retry: boolean }
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
  useDocumentTitle(phase.kind === "intro" || phase.kind === "playing" ? phase.activity.title : "Play again");
  const loadInFlight = useRef(false);
  const shelfHref = `/learn/${childId}/shelf`;

  const finish = useCallback(async (attemptId: string, title: string) => {
    setPhase({ kind: "completing" });
    try {
      await activityAttemptApi.complete(attemptId);
    } catch {
      setPhase({ kind: "error", message: SAVE_FAILED, retry: true });
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
          setPhase({ kind: "error", message: "You've started this book on your path. Finish it there first!", retry: false });
        } else if (err instanceof ApiError && err.status === 404) {
          setPhase({ kind: "error", message: "This book can't be played again just now.", retry: false });
        } else {
          setPhase({ kind: "error", message: LOAD_FAILED, retry: true });
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
        setPhase({ kind: "intro", attemptId: attempt.id, itemId: "", activity: ordered, reason: "Playing it again" });
      } else {
        setPhase({ kind: "playing", attemptId: attempt.id, itemId: "", activity: ordered, index: resumeIndex, result: null, startedAt: Date.now() });
      }
    } catch {
      setPhase({ kind: "error", message: LOAD_FAILED, retry: true });
    } finally {
      loadInFlight.current = false;
    }
  }, [childId, activityVersionId, finish]);

  useEffect(() => {
    void load();
  }, [load]);

  const shell = (content: React.ReactNode) => (
    <ChildSpaceLayout childId={childId} childName={child.data?.displayName} home={shelfHref}>
      {content}
    </ChildSpaceLayout>
  );

  switch (phase.kind) {
    case "loading":
      return shell(<Waiting line="Getting your book ready…" />);
    case "completing":
      return shell(<Waiting line="Saving your work…" />);
    case "error":
      return shell(
        phase.retry ? (
          <Trouble onRetry={load} line={phase.message} />
        ) : (
          <div className="flex flex-col items-center gap-8 px-6 pt-24 text-center">
            <BuddySays mood="encourage" size={140} tone="night">
              {phase.message}
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
            <div aria-hidden className="absolute -top-[4.5rem] right-4 sm:-top-20 sm:right-10">
              <LearningBuddy mood="cheer" size={110} />
            </div>
            <h1 id="practice-done" className="font-display text-headline font-bold text-tonle-950" style={{ fontVariationSettings: '"opsz" 60' }}>
              {phase.total > 0 && phase.correct === phase.total ? "Perfect again!" : "Practice done!"}
            </h1>
            <p className="mt-1 text-lg font-medium text-ink-soft">You played “{phase.title}” again.</p>
            {phase.total > 0 && (
              <p className="mx-auto mt-5 w-fit rounded-pill bg-light-soft px-5 py-2 text-lg font-bold text-light-deep ring-1 ring-light/40">
                You got {phase.correct} of {phase.total} right
              </p>
            )}
            <div className="mt-8 flex flex-col-reverse items-center justify-center gap-4 sm:flex-row">
              <JourneyLink href={`/learn/${childId}`} />
              <Link to={shelfHref} className={goButton}>
                <Library size={22} aria-hidden /> Back to my shelf
              </Link>
            </div>
          </Page>
        </div>,
      );
  }
}

/** The big warm button that moves the child forward: pressed like a real key. */
const goButton =
  "flex min-h-16 items-center justify-center gap-2 rounded-pill bg-light px-10 text-xl font-bold text-tonle-950 shadow-[0_5px_0_#B86A1C,0_12px_40px_rgba(243,168,59,0.45)] transition-[transform,box-shadow,background-color] duration-150 ease-out hover:bg-light-glow active:translate-y-1 active:shadow-[0_1px_0_#B86A1C,0_6px_20px_rgba(243,168,59,0.35)]";

/** The page the child reads from: warm book paper floating over the dimmed world. */
function Page({ children, className, labelledBy }: { children: React.ReactNode; className?: string; labelledBy: string }) {
  return (
    <section
      aria-labelledby={labelledBy}
      className={cn(
        "on-paper relative mx-auto w-full max-w-3xl rounded-[32px] bg-[#FFFBF3] text-ink shadow-lift ring-1 ring-[#EADFCB]",
        // the page's gutter, where it is sewn into the book
        "before:pointer-events-none before:absolute before:inset-y-8 before:left-0 before:w-3 before:rounded-r-full before:bg-gradient-to-r before:from-[#E7D9BF] before:to-transparent",
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
  reason: string | null;
  journeyHref: string;
  onStart: () => void;
}) {
  const startRef = useRef<HTMLButtonElement | null>(null);
  useEffect(() => startRef.current?.focus(), []);
  const n = activity.items.length;
  return (
    <div className="px-4 pb-16 pt-28 sm:pt-24">
      <Page labelledBy="activity-title" className="px-6 pb-10 pt-10 text-center motion-safe:animate-page-in sm:px-14 sm:pb-12">
        <LearningBuddy mood="happy" size={128} className="mx-auto -mt-28 sm:-mt-32" />
        {reason && <p className="mx-auto mt-3 w-fit rounded-pill bg-tonle-50 px-4 py-1.5 text-sm font-semibold text-tonle-700">{reason}</p>}
        <h1 id="activity-title" className="mt-4 font-display text-headline font-bold text-tonle-950" style={{ fontVariationSettings: '"opsz" 60' }}>
          {activity.title}
        </h1>
        {activity.instructions && <p className="mx-auto mt-3 max-w-lg text-lg font-medium leading-relaxed text-ink-soft">{activity.instructions}</p>}

        <div className="mx-auto mt-8 flex w-fit flex-col items-center gap-3 rounded-[24px] bg-tonle-950 px-8 pb-5 pt-6 shadow-[inset_0_2px_12px_rgba(0,0,0,0.35)]">
          <div className="flex items-start gap-3" aria-hidden>
            {Array.from({ length: n }, (_, i) => (
              <Lantern key={i} state={i === 0 ? "current" : "unlit"} size={n > 8 ? 22 : n > 5 ? 30 : 40} />
            ))}
          </div>
          <p className="text-base font-semibold text-tonle-100">
            {n} {n === 1 ? "question" : "questions"} · light {n === 1 ? "the lantern" : n === 2 ? "both lanterns" : `all ${n} lanterns`}
          </p>
        </div>

        <div className="mt-9 flex flex-col-reverse items-center justify-center gap-4 sm:flex-row">
          <JourneyLink href={journeyHref} />
          <button ref={startRef} type="button" onClick={onStart} className={goButton}>
            Let&apos;s go <ArrowRight size={22} aria-hidden />
          </button>
        </div>
      </Page>
    </div>
  );
}

// Varied, authored words for a right answer (every one still says so plainly).
const RIGHT_HEADLINES = ["You got it!", "That's right!", "Great thinking!"];

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
  const [submitError, setSubmitError] = useState<string | null>(null);
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
    onAnswered({ submitted, correct: record.correct, correctAnswer: record.correctAnswer });
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
      setSubmitError(
        err instanceof ApiError && err.status === 422
          ? "Hmm, that answer doesn't fit this question. Try again!"
          : "We couldn't send that answer. Please try again.",
      );
      setIsSubmitting(false);
    }
  }

  const total = activity.items.length;
  const answeredHere = result ? 1 : 0;
  const mood = result ? (result.correct ? "cheer" : "encourage") : isSubmitting ? "thinking" : "happy";
  return (
    <div className="flex flex-col gap-4 px-4 pb-16 pt-4 sm:gap-6 sm:pt-6">
      <div className="mx-auto grid w-full max-w-3xl grid-cols-[2.75rem_minmax(0,1fr)_2.75rem] items-start gap-3">
        <Link
          to={journeyHref}
          aria-label="Back to my journey (your progress is saved)"
          className="flex h-11 w-11 items-center justify-center rounded-full bg-tonle-950/55 text-tonle-100 ring-1 ring-white/15 backdrop-blur-md transition-colors duration-150 ease-out hover:bg-tonle-950/75"
        >
          <BookOpen size={19} aria-hidden />
        </Link>
        <LanternString total={total} lit={index + answeredHere} current={result ? null : index} label={`Question ${index + 1} of ${total}`} />
        <span
          className="flex h-11 w-11 items-center justify-center rounded-full bg-tonle-950/55 text-sm font-bold tabular-nums text-tonle-100 ring-1 ring-white/15 backdrop-blur-md"
          aria-hidden
        >
          {index + 1}/{total}
        </span>
      </div>

      <Page
        labelledBy="question-prompt"
        className={cn(
          "mt-10 transition-shadow duration-500 motion-safe:animate-page-in sm:mt-12",
          result?.correct && "shadow-[0_0_0_1px_#F3A83B,0_30px_80px_-10px_rgba(243,168,59,0.45)]",
        )}
      >
        {/* The firefly keeps the child company on the page and reacts to their answer. */}
        <div aria-hidden className="absolute -top-[4.5rem] right-4 sm:-top-20 sm:right-10">
          <LearningBuddy mood={mood} size={96} className="sm:hidden" />
          <LearningBuddy mood={mood} size={118} className="hidden sm:block" />
        </div>

        <div className="flex flex-col gap-8 px-5 pb-9 pt-9 sm:px-12 sm:pb-11 sm:pt-11">
          <p className="pr-20 text-sm font-semibold text-ink-faint sm:pr-28">{activity.title}</p>
          <QuestionView item={item} onAnswer={handleAnswer} disabled={isSubmitting} result={result} />
          {submitError && (
            <p role="alert" className="text-center text-base font-semibold text-brick-deep">
              {submitError}
            </p>
          )}
        </div>

        {result && (
          <div
            role="status"
            className={cn(
              "flex flex-col items-center gap-5 rounded-b-[32px] border-t border-dashed px-6 pb-7 pt-6 motion-safe:animate-rise-in sm:flex-row sm:px-12",
              result.correct ? "border-light/50 bg-light-soft/70" : "border-tonle-200 bg-tonle-50",
            )}
          >
            <div className="flex-1 text-center sm:text-left">
              <p className={cn("font-display text-[1.75rem] font-bold leading-tight", result.correct ? "text-light-deep" : "text-tonle-800")}>
                {result.correct ? RIGHT_HEADLINES[index % RIGHT_HEADLINES.length] : "Not quite — good try!"}
              </p>
              <p className="mt-1 text-lg font-medium text-ink">
                {result.correct
                  ? "Your lantern is lit."
                  : result.correctAnswer?.matches?.length
                    ? "Some matches got mixed up — the cards show where each one goes. Let's keep going!"
                    : result.correctAnswer?.text
                      ? `The answer is ${result.correctAnswer.text}. Let's keep going!`
                      : "Let's keep going!"}
              </p>
            </div>
            <button ref={continueRef} type="button" onClick={onContinue} className={goButton}>
              {index + 1 < total ? "Next" : "Finish"} <ArrowRight size={22} aria-hidden />
            </button>
          </div>
        )}
      </Page>
    </div>
  );
}

function Completed({
  phase,
  journeyHref,
  onKeepGoing,
}: {
  phase: Extract<Phase, { kind: "completed" }>;
  journeyHref: string;
  onKeepGoing: () => void;
}) {
  const { title, correct, total, next, finishedChapter, nextChapter, itemId, chapterSteps, nextId } = phase;
  const allRight = total > 0 && correct === total;
  const keepGoingRef = useRef<HTMLButtonElement | null>(null);
  useEffect(() => keepGoingRef.current?.focus(), []);
  // The river shows the next book only while it is in this chapter.
  const nextHere = nextId !== null && chapterSteps.some((s) => s.learningPathItemId === nextId) ? nextId : null;

  return (
    <div className="px-4 pb-16 pt-24 sm:pt-28">
      <Page labelledBy="done-title" className="px-6 pb-10 pt-12 text-center motion-safe:animate-pop-in sm:px-14 sm:pb-12">
        {/* One firefly: on the river when the path is shown, otherwise at the page's corner. */}
        {chapterSteps.length === 0 && (
          <div aria-hidden className="absolute -top-[4.5rem] right-4 sm:-top-20 sm:right-10">
            <LearningBuddy mood="cheer" size={96} className="sm:hidden" />
            <LearningBuddy mood="cheer" size={118} className="hidden sm:block" />
          </div>
        )}
        <h1 id="done-title" className="font-display text-headline font-bold text-tonle-950" style={{ fontVariationSettings: '"opsz" 60' }}>
          {finishedChapter ? `Chapter ${finishedChapter.position} complete!` : allRight ? "Perfect!" : "Activity complete!"}
        </h1>
        <p className="mt-1 text-lg font-medium text-ink-soft">
          {finishedChapter ? `You lit every book in “${finishedChapter.title}”.` : `You lit every lantern in “${title}”.`}
        </p>
        {total > 0 && (
          <p className="mx-auto mt-5 w-fit rounded-pill bg-light-soft px-5 py-2 text-lg font-bold text-light-deep ring-1 ring-light/40">
            You got {correct} of {total} right
          </p>
        )}
        {chapterSteps.length > 0 && (
          // The child's stretch of river: the book just finished takes the light where it sits.
          <div
            className={cn(
              "mt-8 overflow-hidden bg-tonle-950 px-4 pt-3 shadow-[inset_0_2px_12px_rgba(0,0,0,0.35)]",
              next ? "rounded-t-[22px]" : "rounded-[22px] pb-3",
            )}
          >
            <PathStrip steps={chapterSteps} justLitId={itemId} nextId={nextHere} chapterDone={finishedChapter !== null} />
          </div>
        )}
        {next && (
          <div
            className={cn(
              "flex items-center gap-4 bg-tonle-950 p-5 text-left text-white shadow-[inset_0_2px_12px_rgba(0,0,0,0.35)]",
              chapterSteps.length > 0 ? "rounded-b-[22px] pt-1" : "mt-8 rounded-[22px]",
            )}
          >
            <Lantern state="current" size={30} className="shrink-0" />
            <div>
              <p className="text-sm font-semibold text-light-glow">
                {finishedChapter && nextChapter ? `Up next · Chapter ${nextChapter.position}: ${nextChapter.title}` : "Up next"}
              </p>
              <p className="font-display text-xl font-bold">{next.title}</p>
              <p className="font-medium text-tonle-100">
                {finishedChapter && next.explanation.reason === "CURRICULUM" ? "A new chapter begins!" : childReason(next)}
              </p>
            </div>
          </div>
        )}
        <div className="mt-8 flex flex-col-reverse items-center justify-center gap-4 sm:flex-row">
          <JourneyLink href={journeyHref} />
          {next !== null && (
            <button ref={keepGoingRef} type="button" onClick={onKeepGoing} className={goButton}>
              Keep going <ArrowRight size={22} aria-hidden />
            </button>
          )}
        </div>
      </Page>
    </div>
  );
}
