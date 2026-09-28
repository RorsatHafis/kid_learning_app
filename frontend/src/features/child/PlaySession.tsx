import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowRight, BookOpen } from "lucide-react";
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

/** The page the child reads from: warm book paper floating over the dimmed world. */
function Page({ children, className, labelledBy }: { children: React.ReactNode; className?: string; labelledBy: string }) {
  return (
    <section
      aria-labelledby={labelledBy}
      className={cn(
        "relative mx-auto w-full max-w-3xl rounded-[30px] bg-[#FFFBF3] text-ink shadow-lift ring-1 ring-[#EADFCB]",
        "before:pointer-events-none before:absolute before:inset-y-6 before:left-0 before:w-3 before:rounded-r-full before:bg-gradient-to-r before:from-[#E7D9BF] before:to-transparent",
        className,
      )}
    >
      {children}
    </section>
  );
}

/** The activity's questions as small books that light up as they are answered. */
function Books({ total, lit, current, label }: { total: number; lit: number; current: number | null; label: string }) {
  return (
    <div className="flex items-center gap-2" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={lit} aria-label={label}>
      {Array.from({ length: total }, (_, i) => (
        <span
          key={i}
          aria-hidden
          className={cn(
            "h-3.5 w-7 rounded-[4px] transition-all duration-300 ease-out",
            i < lit ? "bg-light shadow-[0_0_14px_rgba(255,210,122,0.75)]" : i === current ? "bg-white/70" : "bg-white/20",
          )}
        />
      ))}
    </div>
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
    <div className="px-4 pb-16 pt-28 sm:pt-20">
      <Page labelledBy="activity-title" className="animate-rise-in px-6 pb-10 pt-8 text-center sm:px-14 sm:pb-14 sm:pt-10">
        <LearningBuddy mood="happy" size={132} className="mx-auto -mt-24 sm:-mt-28" />
        {reason && <p className="mx-auto mt-2 w-fit rounded-pill bg-tonle-50 px-4 py-1.5 text-sm font-semibold text-tonle-700">{reason}</p>}
        <h1 id="activity-title" className="mt-5 font-display text-headline font-bold text-tonle-950" style={{ fontVariationSettings: '"opsz" 60' }}>
          {activity.title}
        </h1>
        {activity.instructions && <p className="mx-auto mt-3 max-w-lg text-lg font-medium leading-relaxed text-ink-soft">{activity.instructions}</p>}
        <p className="mt-6 flex items-center justify-center gap-2 text-sm font-semibold text-ink-soft">
          <span className="flex gap-1" aria-hidden>
            {Array.from({ length: n }, (_, i) => (
              <span key={i} className="h-3 w-5 rounded-[3px] bg-tonle-200" />
            ))}
          </span>
          {n} {n === 1 ? "question" : "questions"}
        </p>
        <div className="mt-8 flex flex-col-reverse items-center justify-center gap-3 sm:flex-row">
          <JourneyLink href={journeyHref} />
          <button
            ref={startRef}
            type="button"
            onClick={onStart}
            className="flex min-h-14 items-center gap-2 rounded-pill bg-light px-10 text-xl font-bold text-tonle-950 shadow-glow transition-transform duration-200 ease-out hover:-translate-y-0.5 active:scale-[0.97]"
          >
            Let&apos;s go <ArrowRight size={22} aria-hidden />
          </button>
        </div>
      </Page>
    </div>
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
  const answeredHere = result ? 1 : 0;
  return (
    <div className="flex flex-col gap-5 px-4 pb-16 pt-6 sm:pt-10">
      <div className="mx-auto flex w-full max-w-3xl items-center gap-4">
        <Link
          to={journeyHref}
          aria-label="Back to my journey (your progress is saved)"
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-tonle-950/55 text-tonle-100 ring-1 ring-white/15 backdrop-blur-md transition-colors duration-150 ease-out hover:bg-tonle-950/75"
        >
          <BookOpen size={19} aria-hidden />
        </Link>
        <Books total={total} lit={index + answeredHere} current={index} label={`Question ${index + 1} of ${total}`} />
        <span className="ml-auto text-sm font-semibold text-tonle-100" aria-hidden>
          {index + 1} / {total}
        </span>
      </div>

      <Page labelledBy="question-prompt" className={cn("animate-rise-in overflow-hidden transition-shadow duration-500", result?.correct && "shadow-glow")}>
        <div className="flex flex-col gap-7 px-6 pb-8 pt-7 sm:px-12 sm:pb-10 sm:pt-9">
          <p className="text-sm font-semibold text-ink-faint">{activity.title}</p>
          <h1
            id="question-prompt"
            className="font-display text-[clamp(2.25rem,1.6rem+3vw,3.75rem)] font-bold leading-[1.08] tracking-[-0.02em] text-tonle-950"
            style={{ fontVariationSettings: '"opsz" 72' }}
          >
            {item.prompt}
          </h1>
          <QuestionView item={item} onAnswer={handleAnswer} disabled={isSubmitting} result={result} />
          {submitError && (
            <p role="alert" className="text-base font-semibold text-brick-deep">
              {submitError}
            </p>
          )}
        </div>

        {result && (
          <div
            role="status"
            className={cn(
              "flex animate-rise-in flex-col items-center gap-4 border-t px-6 py-5 sm:flex-row sm:px-12",
              result.correct ? "border-light/40 bg-light-soft" : "border-tonle-100 bg-tonle-50",
            )}
          >
            <LearningBuddy mood={result.correct ? "cheer" : "encourage"} size={92} className="-my-2" />
            <div className="flex-1 text-center sm:text-left">
              <p className={cn("font-display text-2xl font-bold", result.correct ? "text-light-deep" : "text-tonle-800")}>
                {result.correct ? "You got it!" : "Good try!"}
              </p>
              <p className="text-base font-medium text-ink">
                {result.correct
                  ? "That's right."
                  : result.correctAnswer?.matches?.length
                    ? "Some matches got mixed up — the cards show where each one goes. Let's keep going!"
                    : result.correctAnswer?.text
                      ? `The answer is ${result.correctAnswer.text}. Let's keep going!`
                      : "That wasn't quite it — let's keep going!"}
              </p>
            </div>
            <button
              ref={continueRef}
              type="button"
              onClick={onContinue}
              className="flex min-h-14 items-center gap-2 rounded-pill bg-tonle-900 px-9 text-lg font-semibold text-white transition-transform duration-150 ease-out hover:bg-tonle-800 active:scale-[0.97]"
            >
              {index + 1 < total ? "Next" : "Finish"} <ArrowRight size={20} aria-hidden />
            </button>
          </div>
        )}
      </Page>
    </div>
  );
}

// Motes of light rising from the lit book (the world's own celebration, not confetti).
const MOTES = Array.from({ length: 14 }, (_, i) => ({ left: 18 + ((i * 53) % 64), delay: (i % 7) * 140, size: 6 + (i % 3) * 3 }));

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
    <div className="px-4 pb-16 pt-28 sm:pt-20">
      <Page labelledBy="done-title" className="animate-pop-in px-6 pb-10 pt-8 text-center sm:px-14 sm:pb-12">
        <div aria-hidden className="relative mx-auto -mt-24 h-44 w-60 sm:-mt-28">
          <span className="absolute inset-x-6 bottom-6 h-24 rounded-full bg-light/60 blur-2xl motion-safe:animate-glow-breathe" />
          {MOTES.map((m, i) => (
            <span
              key={i}
              className="absolute bottom-10 rounded-full bg-light-glow shadow-[0_0_10px_rgba(255,210,122,0.9)] motion-safe:animate-drift-up motion-reduce:hidden"
              style={{ left: `${m.left}%`, width: m.size, height: m.size, animationDelay: `${m.delay}ms` }}
            />
          ))}
          <svg viewBox="0 0 240 150" className="absolute inset-x-0 bottom-0 animate-light-up">
            <path d="M120 60 C96 40 48 34 14 44 L14 118 C48 108 96 114 120 138 C144 114 192 108 226 118 L226 44 C192 34 144 40 120 60 Z" fill="#FFD27A" />
            <path d="M120 60 C98 46 56 42 26 50 L26 110 C56 102 98 108 120 128 Z" fill="#FFF3D6" />
            <path d="M120 60 C142 46 184 42 214 50 L214 110 C184 102 142 108 120 128 Z" fill="#FFF6E2" />
            <path d="M120 60 L120 130" stroke="#E0B070" strokeWidth="2" />
          </svg>
          <LearningBuddy mood="cheer" size={96} className="absolute -right-6 top-0" />
        </div>
        <h1 id="done-title" className="mt-4 font-display text-headline font-bold text-tonle-950" style={{ fontVariationSettings: '"opsz" 60' }}>
          {allRight ? "Perfect!" : "Activity complete!"}
        </h1>
        <p className="mt-1 text-lg font-medium text-ink-soft">You lit “{title}”.</p>
        {total > 0 && (
          <p className="mx-auto mt-5 w-fit rounded-pill bg-light-soft px-5 py-2 text-lg font-bold text-light-deep">
            You got {correct} of {total} right
          </p>
        )}
        {next && (
          <div className="mt-7 flex items-center gap-4 rounded-[20px] bg-tonle-50 p-5 text-left ring-1 ring-tonle-100">
            <span aria-hidden className="h-12 w-16 shrink-0 rounded-[6px] bg-tonle-700 shadow-[inset_8px_0_0_#182E66]" />
            <div>
              <p className="text-sm font-semibold text-tonle-600">Up next</p>
              <p className="font-display text-xl font-bold text-tonle-950">{next.title}</p>
              <p className="font-medium text-ink-soft">{childReason(next)}</p>
            </div>
          </div>
        )}
        <div className="mt-8 flex flex-col-reverse items-center justify-center gap-3 sm:flex-row">
          <JourneyLink href={journeyHref} />
          {next !== null && (
            <button
              ref={keepGoingRef}
              type="button"
              onClick={onKeepGoing}
              className="flex min-h-14 items-center gap-2 rounded-pill bg-light px-10 text-xl font-bold text-tonle-950 shadow-glow transition-transform duration-200 ease-out hover:-translate-y-0.5 active:scale-[0.97]"
            >
              Keep going <ArrowRight size={22} aria-hidden />
            </button>
          )}
        </div>
      </Page>
    </div>
  );
}
