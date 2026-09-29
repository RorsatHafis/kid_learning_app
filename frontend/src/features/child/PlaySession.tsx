import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowRight, BookOpen } from "lucide-react";
import { Lantern, LanternString } from "@/features/world/Lantern";
import { activityAttemptApi, activityContentApi, childrenApi, enrollmentApi, learningApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import { useAsync } from "@/lib/hooks/useAsync";
import type { ActivityVersionDetail, AnswerRecord, JourneyStep } from "@/lib/api/types";
import { BuddySays, LearningBuddy } from "@/features/buddy/LearningBuddy";
import { play as playCue } from "@/features/buddy/sound";
import { childReason } from "@/features/learning/explain";
import { cn } from "@/lib/utils/cn";
import { ChildSpaceLayout } from "./ChildSpaceLayout";
import { Trouble, Waiting } from "./ChildHome";
import { QuestionView, type AnswerResult } from "./QuestionView";

const LOAD_FAILED = "We couldn't get your activity ready. Let's try again!";
const SAVE_FAILED = "We couldn't save your activity just now. Let's try again!";

type Phase =
  | { kind: "loading" }
  /** Not an error: LearningPathService#getNextItem answers 404 when nothing is
   * PENDING/IN_PROGRESS — the child is simply caught up. */
  | { kind: "caught-up" }
  | { kind: "error"; message: string }
  | { kind: "intro"; attemptId: string; activity: ActivityVersionDetail; reason: string | null }
  | {
      kind: "playing";
      attemptId: string;
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
  // React 18 StrictMode runs effects twice in development; without this guard the
  // start call could fire twice in a row for the same path item.
  const loadInFlight = useRef(false);

  const nextStepOnPath = useCallback(async (): Promise<JourneyStep | null | undefined> => {
    try {
      const overviews = await learningApi.enrollments(childId);
      return overviews.find((overview) => overview.enrollmentId === enrollmentId)?.nextStep ?? null;
    } catch {
      return undefined;
    }
  }, [childId, enrollmentId]);

  const finishAttempt = useCallback(
    async (attemptId: string, title: string) => {
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
      const [answers, next] = await Promise.all([
        activityAttemptApi.listAnswers(attemptId).catch(() => [] as AnswerRecord[]),
        nextStepOnPath(),
      ]);
      setPhase({
        kind: "completed",
        title,
        correct: answers.filter((answer) => answer.correct).length,
        total: answers.length,
        next,
      });
    },
    [nextStepOnPath],
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
        const [activity, answers, next] = await Promise.all([
          activityContentApi.getVersion(item.activityVersionId),
          activityAttemptApi.listAnswers(attempt.id),
          nextStepOnPath(),
        ]);

        const items = [...activity.items].sort((a, b) => a.sequenceOrder - b.sequenceOrder);
        const ordered = { ...activity, items };
        const answered = new Set(answers.map((answer) => answer.questionVersionId));
        const resumeIndex = items.findIndex((question) => !answered.has(question.questionVersionId));

        if (resumeIndex === -1) {
          // Every question already answered (the child left on the last feedback
          // screen) — the attempt only needs completing.
          await finishAttempt(attempt.id, activity.title);
          return;
        }
        if (resumeIndex === 0) {
          const reason = next && next.learningPathItemId === item.id ? childReason(next) : null;
          setPhase({ kind: "intro", attemptId: attempt.id, activity: ordered, reason });
          return;
        }
        setPhase({ kind: "playing", attemptId: attempt.id, activity: ordered, index: resumeIndex, result: null, startedAt: Date.now() });
      } catch {
        setPhase({ kind: "error", message: LOAD_FAILED });
      }
    } finally {
      loadInFlight.current = false;
    }
  }, [childId, enrollmentId, finishAttempt, nextStepOnPath]);

  useEffect(() => {
    void loadNext();
  }, [loadNext]);

  const journeyHref = `/learn/${childId}?path=${enrollmentId}`;
  const shell = (content: React.ReactNode) => (
    <ChildSpaceLayout childId={childId} childName={child.data?.displayName} home={journeyHref}>
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
            You lit every book here for now! Come back soon for more.
          </BuddySays>
          <JourneyLink href={journeyHref} solid />
        </div>,
      );
    case "intro":
      return shell(
        <Intro
          activity={phase.activity}
          reason={phase.reason}
          journeyHref={journeyHref}
          onStart={() =>
            setPhase({ kind: "playing", attemptId: phase.attemptId, activity: phase.activity, index: 0, result: null, startedAt: Date.now() })
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
            void finishAttempt(phase.attemptId, phase.activity.title);
          }}
        />,
      );
  }
}

function JourneyLink({ href, solid }: { href: string; solid?: boolean }) {
  return (
    <Link
      to={href}
      className={cn(
        "flex min-h-14 items-center justify-center gap-2 rounded-pill px-7 text-base font-semibold transition-colors duration-150 ease-out",
        solid ? "bg-white text-tonle-900 hover:bg-tonle-50" : "text-tonle-700 ring-1 ring-tonle-200 hover:bg-tonle-50",
      )}
    >
      <BookOpen size={19} aria-hidden /> My path
    </Link>
  );
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
        "relative mx-auto w-full max-w-3xl rounded-[32px] bg-[#FFFBF3] text-ink shadow-lift ring-1 ring-[#EADFCB]",
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
            {n} {n === 1 ? "question" : "questions"} · light {n === 1 ? "the lantern" : `all ${n} lanterns`}
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
              "flex flex-col items-center gap-5 rounded-b-[32px] border-t-2 border-dashed px-6 pb-7 pt-6 motion-safe:animate-rise-in sm:flex-row sm:px-12",
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
  const { title, correct, total, next } = phase;
  const allRight = total > 0 && correct === total;
  const keepGoingRef = useRef<HTMLButtonElement | null>(null);
  useEffect(() => keepGoingRef.current?.focus(), []);
  const lanterns = Math.min(Math.max(total, 1), 10);

  return (
    <div className="px-4 pb-16 pt-6 sm:pt-8">
      {/* Every lantern of the activity, lit and released into the evening sky. */}
      <div aria-hidden className="relative mx-auto flex h-40 max-w-xl items-end justify-center gap-5 sm:h-48 sm:gap-8">
        {Array.from({ length: lanterns }, (_, i) => (
          <span
            key={i}
            className="motion-safe:animate-rise-away"
            style={{
              animationDelay: `${300 + i * 180}ms`,
              ["--tilt" as string]: `${i % 2 ? -5 : 5}deg`,
              marginBottom: `${(i * 37) % 28}px`,
            }}
          >
            <Lantern state="lit" size={lanterns > 6 ? 32 : 48} />
          </span>
        ))}
      </div>

      <Page labelledBy="done-title" className="mt-2 px-6 pb-10 pt-12 text-center motion-safe:animate-pop-in sm:px-14 sm:pb-12">
        <div aria-hidden className="absolute -top-[4.5rem] right-4 sm:-top-20 sm:right-10">
          <LearningBuddy mood="cheer" size={96} className="sm:hidden" />
          <LearningBuddy mood="cheer" size={118} className="hidden sm:block" />
        </div>
        <h1 id="done-title" className="font-display text-headline font-bold text-tonle-950" style={{ fontVariationSettings: '"opsz" 60' }}>
          {allRight ? "Perfect!" : "Activity complete!"}
        </h1>
        <p className="mt-1 text-lg font-medium text-ink-soft">You lit every lantern in “{title}”.</p>
        {total > 0 && (
          <p className="mx-auto mt-5 w-fit rounded-pill bg-light-soft px-5 py-2 text-lg font-bold text-light-deep ring-1 ring-light/40">
            You got {correct} of {total} right
          </p>
        )}
        {next && (
          <div className="mt-8 flex items-center gap-4 rounded-[22px] bg-tonle-950 p-5 text-left text-white shadow-[inset_0_2px_12px_rgba(0,0,0,0.35)]">
            <Lantern state="current" size={30} className="shrink-0" />
            <div>
              <p className="text-sm font-semibold text-light-glow">Up next</p>
              <p className="font-display text-xl font-bold">{next.title}</p>
              <p className="font-medium text-tonle-100">{childReason(next)}</p>
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
