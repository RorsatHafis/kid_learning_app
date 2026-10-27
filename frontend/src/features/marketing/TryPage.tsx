import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, Info } from "lucide-react";
import { siteApi } from "@/lib/api/site";
import { ApiError } from "@/lib/api/ApiError";
import type { ActivityVersionDetail } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { buttonClasses } from "@/components/ui/Button";
import { SkipLink } from "@/components/ui/SkipLink";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { PaintedBackdrop } from "@/features/world/PaintedWorld";
import { ProgressBuds } from "@/features/world/ProgressBuds";
import { QuestionView, type AnswerResult } from "@/features/child/QuestionView";
import { AnswerVerdict } from "@/features/child/AnswerVerdict";
import { play as playCue } from "@/features/buddy/sound";
import { Nav } from "./sections/Nav";
import { tryMessages } from "./try.messages";
import { usePublicCopy } from "./publicCopy";
import { LearnCard, LookAgain } from "@/features/child/Learn";

const goButton = buttonClasses("accent", "lg", "px-8 text-[1.0625rem]");
const quietButton = buttonClasses("outline", "lg", "bg-white/90");

type Phase =
  | { kind: "choosing" }
  | { kind: "intro"; activity: ActivityVersionDetail }
  | { kind: "playing"; activity: ActivityVersionDetail; index: number; result: AnswerResult | null; right: number }
  | { kind: "done"; activity: ActivityVersionDetail; right: number };

/**
 * The public demo (M2): a few real lessons a visitor plays without an account, exactly as a child plays them (the
 * same question view and verdict), checked by the server and saved nowhere. It ends by saying what an account adds -
 * evidence a family can read - and offers to create one. No trial or price is promised here.
 */
export function TryPage() {
  usePublicCopy();
  const t = useMessages(tryMessages);
  useDocumentTitle(t.documentTitle);
  const activities = useAsync(() => siteApi.demoActivities(), []);
  const [phase, setPhase] = useState<Phase>({ kind: "choosing" });
  const [gone, setGone] = useState(false);

  return (
    <div className="relative min-h-screen bg-paper">
      <PaintedBackdrop />
      <SkipLink />
      <div className="relative">
        <Nav />
        <main id="main" tabIndex={-1} className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-4 pb-20 pt-8 sm:px-8 sm:pt-12">
          {phase.kind === "choosing" && (
            <Choose
              activities={activities}
              gone={gone}
              onStart={(activity) => {
                setGone(false);
                // F1b: an activity that teaches first opens with its introduction, as it does for a child.
                setPhase(activity.introduction ? { kind: "intro", activity } : { kind: "playing", activity, index: 0, result: null, right: 0 });
              }}
            />
          )}
          {phase.kind === "intro" && (
            <Intro activity={phase.activity} onStart={() => setPhase({ kind: "playing", activity: phase.activity, index: 0, result: null, right: 0 })} />
          )}
          {phase.kind === "playing" && (
            <Play
              phase={phase}
              onAnswered={(result) => setPhase({ ...phase, result, right: phase.right + (result.correct ? 1 : 0) })}
              onContinue={() =>
                setPhase(
                  phase.index + 1 < phase.activity.items.length
                    ? { ...phase, index: phase.index + 1, result: null }
                    : { kind: "done", activity: phase.activity, right: phase.right },
                )
              }
              onGone={() => {
                setGone(true);
                activities.refetch();
                setPhase({ kind: "choosing" });
              }}
            />
          )}
          {phase.kind === "done" && <Done phase={phase} onAnother={() => setPhase({ kind: "choosing" })} />}
        </main>
      </div>
    </div>
  );
}

function Choose({
  activities,
  gone,
  onStart,
}: {
  activities: { data: ActivityVersionDetail[] | null; error: Error | null; isLoading: boolean; refetch: () => void };
  gone: boolean;
  onStart: (activity: ActivityVersionDetail) => void;
}) {
  const t = useMessages(tryMessages);
  return (
    <>
      <header className="flex flex-col gap-3">
        <p className="text-sm font-bold uppercase tracking-[0.14em] text-palm-deep">{t.eyebrow}</p>
        <h1 className="font-display text-[clamp(2.1rem,1.6rem+2vw,3.2rem)] font-bold leading-[1.05] text-tonle-950">{t.title}</h1>
        <p className="max-w-2xl text-lg leading-relaxed text-ink-soft">{t.lead}</p>
        {/* F1c: the other half of the product - what a parent reads afterwards, with a sample child. */}
        <Link to="/try/parent" className="inline-flex w-fit items-center gap-1.5 font-semibold text-tonle-800 underline-offset-4 hover:underline">
          {t.parentDemo} <ArrowRight size={16} aria-hidden />
        </Link>
      </header>
      {gone && (
        <p role="alert" className="rounded-[14px] bg-light-soft px-4 py-3 font-semibold text-light-deep">
          {t.gone}
        </p>
      )}
      {activities.isLoading && <p className="text-ink-soft">{t.loading}</p>}
      {activities.error && (
        <div role="alert" className="flex flex-col items-start gap-3 rounded-[20px] bg-white p-5 ring-1 ring-line">
          <p className="font-semibold text-tonle-950">{t.failed}</p>
          <button type="button" onClick={activities.refetch} className={quietButton}>
            {t.retry}
          </button>
        </div>
      )}
      {activities.data && activities.data.length === 0 && (
        <div className="flex flex-col items-start gap-3 rounded-[24px] bg-white p-6 shadow-paper ring-1 ring-line">
          <p className="text-lg font-semibold text-tonle-950">{t.none}</p>
          <p className="text-ink-soft">{t.noneLead}</p>
          <Link to="/register" className={goButton}>
            {t.signUp} <ArrowRight size={18} aria-hidden />
          </Link>
        </div>
      )}
      {activities.data && activities.data.length > 0 && (
        <section aria-labelledby="choose-title" className="flex flex-col gap-4">
          <h2 id="choose-title" className="font-display text-2xl font-bold text-tonle-950">
            {t.choose}
          </h2>
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {activities.data.map((activity) => (
              <li key={activity.activityVersionId} className="flex flex-col gap-3 rounded-[24px] bg-[#FFFDF8] p-5 shadow-paper ring-1 ring-tonle-950/5">
                <p className="font-display text-xl font-bold leading-snug text-tonle-950">{activity.title}</p>
                {activity.instructions && <p className="text-[0.9375rem] leading-relaxed text-ink-soft">{activity.instructions}</p>}
                <p className="text-sm font-semibold text-palm-deep">{t.questions(activity.items.length)}</p>
                <button
                  type="button"
                  onClick={() => onStart(activity)}
                  aria-label={`${t.start}: ${activity.title}`}
                  className={cn(goButton, "mt-auto w-full justify-center")}
                >
                  {t.start} <ArrowRight size={18} aria-hidden />
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}

/** F1b: the activity's "Learn first" page, with the buddy - the same card a child sees before the first question. */
function Intro({ activity, onStart }: { activity: ActivityVersionDetail; onStart: () => void }) {
  const t = useMessages(tryMessages);
  const start = useRef<HTMLButtonElement | null>(null);
  useEffect(() => start.current?.focus(), []);
  return (
    <section aria-labelledby="intro-title" className="mx-auto flex w-full max-w-2xl flex-col items-center gap-5 rounded-[32px] bg-[#FFFDF8] px-5 py-8 text-center shadow-lift ring-1 ring-tonle-950/5 sm:px-10">
      <p className="flex items-center gap-2 rounded-pill bg-white/95 px-4 py-2 text-sm font-semibold text-tonle-900 shadow-tight ring-1 ring-tonle-950/10">
        <Info size={16} aria-hidden /> {t.banner}
      </p>
      <LearningBuddy mood="happy" size={110} />
      <h1 id="intro-title" className="font-display text-[clamp(1.75rem,1.4rem+1.6vw,2.4rem)] font-bold leading-tight text-tonle-950">
        {activity.title}
      </h1>
      {activity.instructions && <p className="max-w-lg text-lg text-ink-soft">{activity.instructions}</p>}
      {activity.introduction && <LearnCard introduction={activity.introduction} />}
      {/* As in the child's app (L7): on a phone the way on stays on screen below a long introduction. */}
      <div className="sticky bottom-0 z-10 -mx-5 flex w-[calc(100%+2.5rem)] justify-center bg-gradient-to-t from-[#FFFDF8] from-70% to-[#FFFDF8]/0 px-5 pb-4 pt-6 sm:static sm:mx-0 sm:w-auto sm:bg-none sm:p-0">
        <button ref={start} type="button" onClick={onStart} className={goButton}>
          {t.letsGo} <ArrowRight size={18} aria-hidden />
        </button>
      </div>
    </section>
  );
}

function Play({
  phase,
  onAnswered,
  onContinue,
  onGone,
}: {
  phase: Extract<Phase, { kind: "playing" }>;
  onAnswered: (result: AnswerResult) => void;
  onContinue: () => void;
  onGone: () => void;
}) {
  const t = useMessages(tryMessages);
  const { activity, index, result } = phase;
  const item = activity.items[index];
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState<"notSent" | "doesntFit" | "tooMany" | null>(null);
  const continueRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (result) continueRef.current?.focus();
  }, [result]);

  if (!item) return null;

  async function answer(submitted: string) {
    if (checking) return;
    setChecking(true);
    setError(null);
    try {
      const verdict = await siteApi.checkDemo(activity.activityVersionId, item.questionVersionId, submitted);
      playCue(verdict.correct ? "correct" : "incorrect");
      onAnswered({ submitted, correct: verdict.correct, correctAnswer: verdict.correctAnswer, explanation: verdict.explanation });
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        onGone();
        return;
      }
      setError(err instanceof ApiError && err.status === 429 ? "tooMany" : err instanceof ApiError && err.status === 422 ? "doesntFit" : "notSent");
    } finally {
      setChecking(false);
    }
  }

  const mood = result ? (result.correct ? "cheer" : "encourage") : checking ? "thinking" : "idle";
  return (
    <div className="flex flex-col gap-5">
      <p className="flex items-center gap-2 self-center rounded-pill bg-white/95 px-4 py-2 text-sm font-semibold text-tonle-900 shadow-tight ring-1 ring-tonle-950/10">
        <Info size={16} aria-hidden /> {t.banner}
      </p>
      <ProgressBuds total={activity.items.length} done={index + (result ? 1 : 0)} current={result ? null : index} label={t.questionOf(index + 1, activity.items.length)} />
      {activity.introduction && (
        <div className="flex justify-center">
          <LookAgain introduction={activity.introduction} />
        </div>
      )}
      <section
        aria-labelledby="question-prompt"
        className={cn(
          "relative mx-auto mt-8 w-full max-w-3xl rounded-[32px] bg-[#FFFDF8] text-ink shadow-lift ring-1 ring-tonle-950/5",
          "before:pointer-events-none before:absolute before:inset-[10px] before:rounded-[24px] before:border before:border-[#EFE6D2]",
          result?.correct && "shadow-[0_0_0_2px_#F2A93B,0_30px_70px_-20px_rgba(199,122,30,0.45)]",
        )}
      >
        <div aria-hidden className="pointer-events-none absolute -top-14 right-3 sm:-top-16 sm:right-8">
          <LearningBuddy mood={mood} size={78} className="sm:hidden" />
          <LearningBuddy mood={mood} size={96} className="hidden sm:block" />
        </div>
        <div className="flex flex-col gap-8 px-5 pb-9 pt-10 sm:px-12 sm:pb-11 sm:pt-12">
          <QuestionView key={item.questionVersionId} item={item} onAnswer={answer} disabled={checking} result={result} />
          {error && (
            <p role="alert" className="text-center text-base font-semibold text-brick-deep">
              {t[error]}
            </p>
          )}
        </div>
        {result && (
          <AnswerVerdict item={item} index={index} total={activity.items.length} result={result} onContinue={onContinue} continueRef={continueRef} />
        )}
      </section>
    </div>
  );
}

function Done({ phase, onAnother }: { phase: Extract<Phase, { kind: "done" }>; onAnother: () => void }) {
  const t = useMessages(tryMessages);
  const heading = useRef<HTMLHeadingElement | null>(null);
  useEffect(() => heading.current?.focus(), []);
  return (
    <section aria-labelledby="done-title" className="mx-auto flex w-full max-w-2xl flex-col items-center gap-5 rounded-[32px] bg-[#FFFDF8] px-6 py-10 text-center shadow-lift ring-1 ring-tonle-950/5 sm:px-12">
      <LearningBuddy mood="cheer" size={120} />
      <h1 id="done-title" ref={heading} tabIndex={-1} className="font-display text-[2rem] font-bold leading-tight text-tonle-950 focus:outline-none">
        {t.doneTitle}
      </h1>
      <p className="text-lg font-semibold text-tonle-900">{t.doneScore(phase.right, phase.activity.items.length)}</p>
      <p className="max-w-lg text-[1.0625rem] leading-relaxed text-ink-soft">{t.doneLead}</p>
      {/* F1b: what creating an account actually enables - nothing promised that the product doesn't do. */}
      <div className="w-full max-w-lg rounded-[20px] bg-white p-5 text-left ring-1 ring-line">
        <h2 className="font-display text-lg font-bold text-tonle-950">{t.accountTitle}</h2>
        <ul className="mt-2 flex list-disc flex-col gap-1.5 pl-5 text-[0.9375rem] leading-relaxed text-ink">
          <li>{t.accountSaved}</li>
          <li>{t.accountSetUp}</li>
        </ul>
      </div>
      <div className="flex flex-col gap-3 sm:flex-row">
        <Link to="/register" className={goButton}>
          {t.signUp} <ArrowRight size={18} aria-hidden />
        </Link>
        <button type="button" onClick={onAnother} className={quietButton}>
          {t.another}
        </button>
      </div>
      {/* No pressure: "Not now" goes back to exploring the site, nothing asked. */}
      <Link to="/" className="inline-block py-3 text-sm font-semibold text-tonle-800 underline-offset-4 hover:underline">
        {t.notNow}
      </Link>
    </section>
  );
}
