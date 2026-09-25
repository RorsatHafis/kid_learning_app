import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowRight, Map as MapIcon } from "lucide-react";
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
        <div className="flex flex-col items-center gap-6 pt-10 text-center">
          <BuddySays mood="cheer" size={140} tone="sun">
            You finished everything here for now! Come back soon for more.
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
        "flex min-h-12 items-center justify-center gap-2 rounded-pill px-7 py-3 text-base font-extrabold shadow-tight",
        solid ? "bg-kid-skyDeep text-white" : "bg-white text-ink hover:bg-kid-cream",
      )}
    >
      <MapIcon size={20} aria-hidden /> My journey
    </Link>
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
  return (
    <section
      aria-labelledby="activity-title"
      className="mx-auto flex max-w-2xl animate-rise-in flex-col items-center gap-6 rounded-[36px] bg-white p-8 text-center shadow-soft sm:p-12"
    >
      <LearningBuddy mood="happy" size={140} />
      {reason && <p className="rounded-pill bg-kid-lavender px-4 py-1.5 text-sm font-bold text-kid-lavenderDeep">{reason}</p>}
      <h1 id="activity-title" className="text-3xl font-extrabold sm:text-4xl">
        {activity.title}
      </h1>
      {activity.instructions && <p className="text-lg font-semibold text-muted">{activity.instructions}</p>}
      <p className="text-sm font-bold uppercase tracking-wide text-muted">
        {activity.items.length} {activity.items.length === 1 ? "question" : "questions"}
      </p>
      <div className="flex flex-col-reverse items-center gap-3 sm:flex-row">
        <JourneyLink href={journeyHref} />
        <button
          ref={startRef}
          type="button"
          onClick={onStart}
          className="flex min-h-14 items-center gap-2 rounded-pill bg-kid-skyDeep px-10 py-4 text-xl font-extrabold text-white shadow-soft transition-transform hover:-translate-y-0.5"
        >
          Let's go <ArrowRight size={22} aria-hidden />
        </button>
      </div>
    </section>
  );
}

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
  return (
    <section aria-labelledby="question-prompt" className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="flex items-center gap-4">
        <Link
          to={journeyHref}
          aria-label="Back to my journey (your progress is saved)"
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-white text-muted shadow-tight hover:text-ink"
        >
          <MapIcon size={20} aria-hidden />
        </Link>
        <div
          className="flex flex-1 gap-1.5"
          role="progressbar"
          aria-valuemin={1}
          aria-valuemax={total}
          aria-valuenow={index + 1}
          aria-label={`Question ${index + 1} of ${total}`}
        >
          {activity.items.map((q, i) => (
            <span
              key={q.questionVersionId}
              className={cn(
                "h-3 flex-1 rounded-pill transition-colors",
                i < index || (i === index && result) ? "bg-kid-mintDeep" : i === index ? "bg-kid-skyDeep" : "bg-white",
              )}
            />
          ))}
        </div>
        <span className="text-sm font-extrabold text-muted" aria-hidden>
          {index + 1}/{total}
        </span>
      </div>

      <div className="flex animate-rise-in flex-col gap-6 rounded-[36px] bg-white p-6 shadow-soft sm:p-10">
        <p className="text-sm font-bold text-muted">{activity.title}</p>
        <h1 id="question-prompt" className="text-3xl font-extrabold leading-snug sm:text-4xl">
          {item.prompt}
        </h1>
        <QuestionView item={item} onAnswer={handleAnswer} disabled={isSubmitting} result={result} />
        {submitError && (
          <p role="alert" className="text-base font-bold text-kid-coralDeep">
            {submitError}
          </p>
        )}
      </div>

      {result && (
        <div
          role="status"
          className={cn(
            "flex animate-rise-in flex-col items-center gap-4 rounded-[32px] p-5 sm:flex-row sm:p-6",
            result.correct ? "bg-kid-mint" : "bg-kid-peach",
          )}
        >
          <LearningBuddy mood={result.correct ? "cheer" : "encourage"} size={96} />
          <div className="flex-1 text-center sm:text-left">
            <p className={cn("text-2xl font-extrabold", result.correct ? "text-kid-mintDeep" : "text-kid-peachDeep")}>
              {result.correct ? "You got it!" : "Good try!"}
            </p>
            <p className="text-base font-semibold text-ink">
              {result.correct
                ? "That's right."
                : result.correctAnswer?.text
                  ? `The answer is ${result.correctAnswer.text}. Let's keep going!`
                  : "That wasn't quite it — let's keep going!"}
            </p>
          </div>
          <button
            ref={continueRef}
            type="button"
            onClick={onContinue}
            className="flex min-h-14 items-center gap-2 rounded-pill bg-kid-skyDeep px-9 py-3 text-lg font-extrabold text-white shadow-soft"
          >
            {index + 1 < total ? "Next" : "Finish"} <ArrowRight size={20} aria-hidden />
          </button>
        </div>
      )}
    </section>
  );
}

const CONFETTI = ["#FFD166", "#9BE3C4", "#B9A6FF", "#FF8A80", "#8EC5FF", "#FFB38A"];

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

  return (
    <section
      aria-labelledby="done-title"
      className="relative mx-auto flex max-w-2xl animate-pop-in flex-col items-center gap-6 overflow-hidden rounded-[36px] bg-white p-8 text-center shadow-soft sm:p-12"
    >
      <div aria-hidden className="pointer-events-none absolute inset-x-0 top-0 h-0">
        {Array.from({ length: 18 }, (_, i) => (
          <span
            key={i}
            className="absolute top-0 h-3 w-2 rounded-sm motion-safe:animate-confetti-fall motion-reduce:hidden"
            style={{ left: `${(i * 97) % 100}%`, background: CONFETTI[i % CONFETTI.length], animationDelay: `${(i % 6) * 90}ms` }}
          />
        ))}
      </div>
      <LearningBuddy mood="cheer" size={150} />
      <h1 id="done-title" className="text-3xl font-extrabold sm:text-4xl">
        {allRight ? "Perfect!" : "Activity complete!"}
      </h1>
      <p className="text-lg font-semibold text-muted">{title}</p>
      {total > 0 && (
        <p className="rounded-pill bg-kid-mint px-5 py-2 text-lg font-extrabold text-kid-mintDeep">
          You got {correct} of {total} right
        </p>
      )}
      {next && (
        <div className="w-full rounded-3xl bg-kid-lavender p-5 text-left">
          <p className="text-xs font-extrabold uppercase tracking-wide text-kid-lavenderDeep">Up next</p>
          <p className="text-xl font-extrabold">{next.title}</p>
          <p className="font-semibold text-ink">{childReason(next)}</p>
        </div>
      )}
      <div className="flex flex-col-reverse items-center gap-3 sm:flex-row">
        <JourneyLink href={journeyHref} solid={next === null} />
        {next !== null && (
          <button
            ref={keepGoingRef}
            type="button"
            onClick={onKeepGoing}
            className="flex min-h-14 items-center gap-2 rounded-pill bg-kid-skyDeep px-10 py-4 text-xl font-extrabold text-white shadow-soft"
          >
            Keep going <ArrowRight size={22} aria-hidden />
          </button>
        )}
      </div>
    </section>
  );
}
